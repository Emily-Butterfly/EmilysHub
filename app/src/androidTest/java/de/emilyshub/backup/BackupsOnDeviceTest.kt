package de.emilyshub.backup

import android.content.Context
import android.database.Cursor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.HubDatabase
import de.emilyshub.places.Coordinates
import de.emilyshub.places.PlaceDraft
import de.emilyshub.places.Places
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import de.emilyshub.tags.Tags
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.Todos
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalTime
import java.util.UUID
import javax.crypto.KeyGenerator

/**
 * Backups (SPEC.md §10) between two databases encrypted with SQLCipher, on Android's own crypto, with the real number
 * of key derivation rounds where that is what counts; and older ones migrated on SQLCipher's SQLite with the schema of
 * then from the app's assets: what the Robolectric tests cannot show.
 */
@RunWith(AndroidJUnit4::class)
class BackupsOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = List(2) { "backup-test-${UUID.randomUUID()}.db" }
    private val wrappingKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @After
    fun deleteDatabases() {
        names.forEach(context::deleteDatabase)
    }

    @Test
    fun `a backup of one encrypted database opens with its password only and restores into another as it was`() {
        val old = HubDatabase.open(context, randomKey(), names[0])
        val new = HubDatabase.open(context, randomKey(), names[1])
        try {
            someData(old)
            val file = runBlocking {
                val backups = backupsOf(old)
                backups.setPassword(PASSWORD)
                ByteArrayOutputStream().also { out -> backups.export { out } }.toByteArray()
            }

            val backups = backupsOf(new)
            val wrongPassword = assertThrows(BackupException::class.java) {
                runBlocking { backups.open({ ByteArrayInputStream(file) }, "Lila und Flieder") }
            }
            assertEquals(BackupProblem.WrongPassword, wrongPassword.problem)
            runBlocking { backups.restore(backups.open({ ByteArrayInputStream(file) }, PASSWORD)) }

            assertTrue(everything(old).getValue("routines").isNotEmpty())
            assertEquals(everything(old), everything(new))
        } finally {
            old.close()
            new.close()
        }
    }

    @Test
    fun `a backup of an older app version is brought up to this one with the layout of then from the app`() {
        val old = HubDatabase.open(context, randomKey(), names[0])
        val new = HubDatabase.open(context, randomKey(), names[1])
        try {
            someData(old)
            val key = BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS)
            val content = runBlocking {
                val backups = backupsOf(old, iterations = FEW_ITERATIONS)
                backups.setPassword(PASSWORD)
                val file = ByteArrayOutputStream().also { out -> backups.export { out } }.toByteArray()
                JSONObject(String(BackupFile.open(file, PASSWORD).plaintext))
            }
            val file = BackupFile.seal(asOfVersion16(content).toString().toByteArray(), key)

            val backups = backupsOf(new)
            runBlocking { backups.restore(backups.open({ ByteArrayInputStream(file) }, PASSWORD)) }

            assertEquals(everything(old), everything(new))
        } finally {
            old.close()
            new.close()
        }
    }

    /**
     * [content] as version 16 made it, the first that made backups: without the wishes, which came with 17, nor the notes,
     * which came with 21, their folders, which came with 22, their links, which came with 23, and their tags.
     */
    private fun asOfVersion16(content: JSONObject): JSONObject {
        val tables = content.getJSONObject("tables")
        val notes = listOf(
            "note_tabs", "notes", "note_folders", "note_general_tab", "note_goals", "note_todos", "note_routines", "note_tags",
        )
        // Nor the household book, which came with 28.
        (listOf("wishes", "wish_tags", "ledger_categories", "bookings", "recurring_bookings") + notes).forEach { tables.remove(it) }
        // Nor the goals to-dos hang under, which came with 18, nor to-dos coming again, which came with 19, nor the
        // goals routines hang under, which came with 20, nor ends of series, which came with 26, nor to-dos shown before
        // their day, which came with 27.
        val newer = mapOf(
            "todos" to listOf("goal_id", "recurrence", "next_id", "ends_after", "ends_on", "series_number", "shows_days_before"),
            "routines" to listOf("goal_id", "target", "ends_on"),
        )
        for ((table, names) in newer) {
            val columns = tables.getJSONObject(table).getJSONArray("columns")
            val rows = tables.getJSONObject(table).getJSONArray("rows")
            for (name in names) {
                val index = (0 until columns.length()).first { columns.getString(it) == name }
                columns.remove(index)
                for (row in 0 until rows.length()) rows.getJSONArray(row).remove(index)
            }
        }
        return content.put("schema", 16)
    }

    /** A routine with a tag and a place with a WLAN and a GPS area, and a to-do. */
    private fun someData(database: HubDatabase) {
        runBlocking {
            val tag = Tags({ database }).create("Gesundheit")
            val shop = PlaceDraft("Supermarkt", listOf("Markt-WLAN")).withCenter(Coordinates(52.52, 13.405))
            val place = Places({ database }).create(shop)
            Routines({ database }, Clock.systemUTC()).create(
                RoutineDraft(
                    "Vitamine", description = "", listOf(LocalTime.of(7, 30)), Repetition.Daily,
                    tagIds = setOf(tag), placeIds = setOf(place),
                ),
            )
            Todos({ database }, Clock.systemUTC()).create(TodoDraft("Milch kaufen", placeIds = setOf(place)))
        }
    }

    private fun backupsOf(database: HubDatabase, iterations: Int = BackupKey.ITERATIONS) = Backups(
        database = { database },
        settings = { database.settings() },
        wrappingKey = { wrappingKey },
        clock = Clock.systemUTC(),
        migration = BackupMigration(context) { HubDatabase.openHelpers(ByteArray(32).also(SecureRandom()::nextBytes)) },
        iterations = iterations,
    )

    /** Every row of every table but the settings, read here on its own, without the backups. */
    private fun everything(database: HubDatabase): Map<String, List<List<Any?>>> {
        val db = database.openHelper.readableDatabase
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
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                            Cursor.FIELD_TYPE_NULL -> null
                            else -> cursor.getString(column)
                        }
                    }
                }
            }
        }
    }

    private fun randomKey() = ByteArray(32).also(SecureRandom()::nextBytes)

    private companion object {
        const val PASSWORD = "Flieder und Lila"

        /** Enough where the real count is not what is tested. */
        const val FEW_ITERATIONS = 1_000
    }
}
