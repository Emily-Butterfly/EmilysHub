package de.emilyshub.backup

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.alarmsFor
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.Setting
import de.emilyshub.history
import de.emilyshub.place
import de.emilyshub.places.Area
import de.emilyshub.places.Coordinates
import de.emilyshub.reminders.ReminderReceiver
import de.emilyshub.routine
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.tag
import de.emilyshub.todo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Backups of all data (SPEC.md §10): made with the backup password set once, opened with it and checked strictly, and
 * only then put in place of all data, or nothing changes.
 */
@RunWith(AndroidJUnit4::class)
class BackupsTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val backups = app.backups

    @Test
    fun `a backup brings back all data as they were when it was made`() {
        val made = someOfEverything()
        val file = exported()
        changeEverything()

        restore(file)

        assertEquals(made, everything())
        assertEquals(listOf("Vitamine", "Yoga"), runBlocking { app.routines.inUse().first() }.map { it.name })
    }

    @Test
    fun `opening a backup changes nothing yet`() {
        someOfEverything()
        val file = exported()
        changeEverything()
        val changed = everything()

        val backup = open(file)

        assertEquals(changed, everything())
        assertEquals(Instant.parse("2026-10-23T05:00:00Z"), backup.created.toInstant())
        assertEquals(Backup.Summary(routines = 2, todos = 2, places = 2, tags = 2), backup.summary)
    }

    @Test
    fun `what shows the data sees those restored`() {
        someOfEverything()
        val file = exported()
        changeEverything()
        val shown = mutableListOf<List<String>>()
        val showing = CoroutineScope(Dispatchers.Unconfined).launch {
            app.routines.inUse().collect { all -> shown += all.map { it.name } }
        }

        restore(file)

        showing.cancel()
        assertEquals(listOf("Vitamine", "Yoga"), shown.last())
    }

    @Test
    fun `a wrong password, a changed file or a cut one is refused, and the data stay as they are`() {
        someOfEverything()
        val file = exported()
        changeEverything()
        val changed = everything()

        assertProblem(BackupProblem.WrongPassword) { open(file, password = "Lila und Flieder") }
        assertProblem(BackupProblem.Damaged) { open(file.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }) }
        assertProblem(BackupProblem.Damaged) { open(file.copyOf(file.size - 100)) }
        assertProblem(BackupProblem.NotABackup) { open("Einkaufsliste".toByteArray()) }
        assertEquals(changed, everything())
    }

    @Test
    fun `a backup of the data of an older or newer app version is refused as such`() {
        someOfEverything()
        val content = contentOf(exported())

        assertProblem(BackupProblem.OlderVersion) { open(sealed(content.put("schema", HubDatabase.VERSION - 1))) }
        assertProblem(BackupProblem.NewerVersion) { open(sealed(content.put("schema", HubDatabase.VERSION + 1))) }
    }

    @Test
    fun `a backup that does not fit the tables of the app is refused, and the data stay as they are`() {
        someOfEverything()
        val file = exported()
        changeEverything()
        val changed = everything()
        val unfitting: List<Pair<String, (JSONObject) -> Unit>> = listOf(
            "a table missing" to { it.tables().remove("tags") },
            "a table unknown" to { it.tables().put("passwords", it.tables().getJSONObject("tags")) },
            "a column unknown" to { it.table("tags").getJSONArray("columns").put("colour") },
            "a column missing" to { it.table("tags").put("columns", JSONArray(listOf("id"))) },
            "a row too short" to { it.rowsOf("tags").put(JSONArray(listOf(99))) },
            "text for a number" to { it.rowsOf("tags").getJSONArray(0).put(0, "eins") },
            "a number for text" to { it.rowsOf("tags").getJSONArray(0).put(1, 42) },
            "nothing where it must be" to { it.rowsOf("tags").getJSONArray(0).put(1, JSONObject.NULL) },
            "the same id twice" to { it.rowsOf("tags").put(it.rowsOf("tags").getJSONArray(0)) },
            "a tag on no to-do" to { it.rowsOf("todo_tags").getJSONArray(0).put(0, 999) },
            "a time not a time" to { it.put("created", "gestern") },
            "a list for a value" to { it.rowsOf("tags").getJSONArray(0).put(1, JSONArray()) },
            "a key unknown" to { it.put("passwords", JSONObject()) },
            "a key unknown in a table" to { it.table("tags").put("colour", "lila") },
            "a repetition the app cannot read" to { it.setFirst("routines", "repetition", "jede Woche") },
            "a status the app cannot read" to { it.setFirst("occurrences", "status", "vielleicht") },
            "a time the app cannot read" to { it.setFirst("routines", "times", "halb acht") },
            "a date the app cannot read" to { it.setFirst("occurrences", "date", "2026-02-30") },
        )

        for ((what, change) in unfitting) {
            val content = contentOf(file).also(change)
            assertEquals(what, BackupProblem.Damaged, assertThrows(BackupException::class.java) { open(sealed(content)) }.problem)
        }
        assertProblem(BackupProblem.Damaged) { open(BackupFile.seal("{ kein JSON".toByteArray(), key())) }
        assertEquals(changed, everything())
    }

    @Test
    fun `the backup password is set once, and only its key is kept, sealed with the Keystore key`() {
        someOfEverything()
        assertFalse(runBlocking { backups.hasPassword() })
        assertThrows(IllegalStateException::class.java) { exported(setPassword = false) }

        runBlocking { backups.setPassword(PASSWORD) }

        assertTrue(runBlocking { backups.hasPassword() })
        val stored = app.settingsDao.values.values.joinToString()
        assertFalse(stored.contains(PASSWORD))
        assertThrows(IllegalStateException::class.java) { runBlocking { backups.setPassword("ein anderes Passwort") } }
        open(exported(setPassword = false), PASSWORD)
    }

    @Test
    fun `a new backup password has at least 10 characters, typed the same twice`() {
        assertEquals(PasswordProblem.TooShort, Backups.passwordProblem("Flieder", "Flieder"))
        assertEquals(PasswordProblem.NotRepeated, Backups.passwordProblem("Flieder und Lila", "Flieder und lila"))
        assertNull(Backups.passwordProblem("Fliederbaum", "Fliederbaum"))
    }

    @Test
    fun `restoring on a device without a backup password takes over the one of the backup`() {
        someOfEverything()
        val file = exported()
        app.settingsDao.values.clear()

        restore(file)

        assertTrue(runBlocking { backups.hasPassword() })
        open(exported(setPassword = false), PASSWORD)
    }

    @Test
    fun `restoring keeps the backup password set on this device`() {
        someOfEverything()
        val other = BackupKey.derive("das Passwort vom alten Handy", iterations = FEW_ITERATIONS)
        val file = BackupFile.seal(contentOf(exported()).toString().toByteArray(), other)

        restore(file, password = "das Passwort vom alten Handy")

        open(exported(setPassword = false), PASSWORD)
        assertProblem(BackupProblem.WrongPassword) { open(exported(setPassword = false), password = "das Passwort vom alten Handy") }
    }

    @Test
    fun `the settings of this device are no part of a backup`() {
        runBlocking { app.testDatabase.settings().put(Setting("design", "dark")) }
        val file = exported()
        runBlocking { app.testDatabase.settings().put(Setting("design", "light")) }

        restore(file)

        assertEquals("light", runBlocking { app.testDatabase.settings().get("design") })
        assertFalse(contentOf(file).tables().has("settings"))
    }

    @Test
    fun `after a restore, the reminders follow the data restored`() {
        val vitamins = app.routine("Vitamine", "07:30")
        val file = exported()
        runBlocking { app.routines.delete(vitamins) }
        assertEquals(emptyList<Any>(), reminderAlarms())

        restore(file)

        assertEquals(listOf(Instant.parse("2026-10-23T05:30:00Z")), reminderAlarms())
    }

    /** Two routines, one with a checklist ticked in part today, tags, places by WLAN and GPS, to-dos at them. */
    private fun someOfEverything(): Map<String, List<List<Any?>>> {
        val home = app.place("Zuhause", "FRITZ!Box 7590")
        val shop = app.place("Supermarkt", area = Area(Coordinates(52.52, 13.405), radius = 150))
        val health = app.tag("Gesundheit")
        val sport = app.tag("Sport")
        val vitamins = app.routine("Vitamine", "07:30", checklist = listOf("Vitamin D", "Magnesium"), tags = setOf(health))
        app.routine("Yoga", "18:00", tags = setOf(sport), places = setOf(home))
        app.history(vitamins, "2026-10-21" to Missed, "2026-10-22" to Done)
        runBlocking {
            val occurrence = app.testDatabase.routines().occurrencesOn(vitamins, LocalDate.parse("2026-10-23")).single()
            app.routines.tick(occurrence.id, app.routines.checklist(vitamins).first().id, ticked = true)
        }
        app.todo("Milch kaufen", checklist = listOf("Hafermilch"), tags = setOf(health), places = setOf(shop))
        app.todo("Steuer machen")
        return everything().also { all -> assertTrue("Some of everything: $all", all.values.all { it.isNotEmpty() }) }
    }

    private fun changeEverything() = runBlocking {
        app.routines.inUse().first().forEach { app.routines.delete(it.id) }
        app.tags.all().first().forEach { app.tags.delete(it.id) }
        app.todo("Neues To-do")
    }

    /** Every row of every table but the settings, read here on its own, without the backups. */
    private fun everything(): Map<String, List<List<Any?>>> {
        val db = app.testDatabase.openHelper.readableDatabase
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table', 'settings') ORDER BY name",
        ).use { cursor -> List(cursor.count) { cursor.moveToNext(); cursor.getString(0) } }
        return tables.associateWith { table ->
            db.query("SELECT * FROM `$table` ORDER BY 1, 2").use { cursor ->
                List(cursor.count) {
                    cursor.moveToNext()
                    List(cursor.columnCount) { column ->
                        when (cursor.getType(column)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                            android.database.Cursor.FIELD_TYPE_NULL -> null
                            else -> cursor.getString(column)
                        }
                    }
                }
            }
        }
    }

    private fun exported(setPassword: Boolean = true): ByteArray {
        if (setPassword) runBlocking { backups.setPassword(PASSWORD) }
        val file = ByteArrayOutputStream()
        runBlocking { backups.export { file } }
        return file.toByteArray()
    }

    private fun open(file: ByteArray, password: String = PASSWORD): Backup =
        runBlocking { backups.open({ ByteArrayInputStream(file) }, password) }

    private fun restore(file: ByteArray, password: String = PASSWORD) = runBlocking { backups.restore(open(file, password)) }

    private fun contentOf(file: ByteArray) = JSONObject(String(BackupFile.open(file, PASSWORD).plaintext))

    private fun sealed(content: JSONObject) = BackupFile.seal(content.toString().toByteArray(), key())

    private fun key() = BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS)

    private fun JSONObject.tables() = getJSONObject("tables")

    private fun JSONObject.table(name: String) = tables().getJSONObject(name)

    private fun JSONObject.rowsOf(table: String) = table(table).getJSONArray("rows")

    /** Sets the [column] of the first row of [table] to [value]. */
    private fun JSONObject.setFirst(table: String, column: String, value: Any) {
        val columns = table(table).getJSONArray("columns")
        rowsOf(table).getJSONArray(0).put((0 until columns.length()).first { columns.getString(it) == column }, value)
    }

    private fun reminderAlarms() = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        .alarmsFor(ReminderReceiver.ACTION_REMIND).map { Instant.ofEpochMilli(it.triggerAtMs) }

    private fun assertProblem(problem: BackupProblem, block: () -> Unit) {
        assertEquals(problem, assertThrows(BackupException::class.java, block).problem)
    }

    private companion object {
        const val PASSWORD = "Flieder und Lila"
        const val FEW_ITERATIONS = 1_000
    }
}
