package de.emilyshub.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.emilyshub.places.Area
import de.emilyshub.places.Place
import de.emilyshub.places.PlaceWifi
import de.emilyshub.places.PlaceWithWifis
import de.emilyshub.routines.ChecklistItem
import de.emilyshub.routines.ChecklistTick
import de.emilyshub.routines.FollowUps
import de.emilyshub.routines.Occurrence
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Priority
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.Routine
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.routines.RoutineTag
import de.emilyshub.routines.TimeWindow
import de.emilyshub.tags.Tag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherDatabase

/**
 * Each migration on an encrypted database. Opening it runs the migrations, and Room then checks every
 * table, column, key and index against the app's entities, failing if anything differs.
 *
 * Room's MigrationTestHelper would do the same, but room-testing needs a newer kotlinx-serialization
 * than the app ships, and the test APK has to use the app's.
 */
@RunWith(AndroidJUnit4::class)
class HubDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-${UUID.randomUUID()}.db"
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun `version 1 moves to the current version keeping its settings and gaining routines`() {
        createAtVersion(1) { it.execSQL("INSERT INTO settings (name, value) VALUES ('design', 'dark')") }

        withMigratedDatabase { database ->
            assertEquals("dark", database.settings().get("design"))

            val day = LocalDate.parse("2026-10-23")
            val routine = Routine(
                name = "Lesen",
                description = "",
                times = listOf(LocalTime.of(21, 0)),
                repetition = Repetition.Daily,
                plannedFrom = day.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            )
            val id = database.routines().insert(routine)
            val occurrence = Occurrence(routineId = id, date = day, time = LocalTime.of(21, 0), status = OccurrenceStatus.Open)
            database.routines().insertOccurrences(listOf(occurrence))
            val occurrences = database.routines().observeOccurrencesWithChecklists(day, day).first()
            assertEquals(listOf("Lesen"), occurrences.map { it.routine.name })
        }
    }

    @Test
    fun `version 2 moves to version 3 keeping each routine daily at its time and its occurrences`() {
        createAtVersion(2) {
            it.execSQL(
                "INSERT INTO routines (id, name, description, time, start_date) " +
                    "VALUES (7, 'Lesen', 'Vor dem Schlafen', '21:00', '2026-10-23')",
            )
            it.execSQL(
                "INSERT INTO routines (id, name, description, time, start_date) VALUES (8, 'Joggen', '', '07:00', '2026-10-24')",
            )
            it.execSQL("INSERT INTO occurrences (routine_id, date, time, status) VALUES (7, '2026-10-23', '21:00', 'done')")
        }

        withMigratedDatabase { database ->
            val routine = database.routines().get(7)
            assertEquals(
                Routine(
                    id = 7,
                    name = "Lesen",
                    description = "Vor dem Schlafen",
                    times = listOf(LocalTime.of(21, 0)),
                    repetition = Repetition.Daily,
                    plannedFrom = startOf("2026-10-23"),
                ),
                routine,
            )
            assertEquals(startOf("2026-10-24"), database.routines().get(8)?.plannedFrom)
            val day = LocalDate.parse("2026-10-23")
            val occurrences = database.routines().observeOccurrencesWithChecklists(day, day).first()
            assertEquals(listOf("Lesen" to OccurrenceStatus.Done), occurrences.map { it.routine.name to it.occurrence.status })

            // The occurrences belong to the new table: deleting the routine still takes them along.
            database.routines().delete(7)
            assertEquals(emptyList<Any>(), database.routines().observeOccurrencesWithChecklists(day, day).first())
        }
    }

    @Test
    fun `version 3 moves to version 4 keeping the occurrences with none put off`() {
        createAtVersion(3) {
            it.execSQL(
                "INSERT INTO routines (id, name, description, times, repetition, planned_from) " +
                    "VALUES (3, 'Tabletten', '', '08:00,20:00', 'daily', '2026-10-23T06:00:00Z')",
            )
            it.execSQL("INSERT INTO occurrences (routine_id, date, time, status) VALUES (3, '2026-10-23', '08:00', 'done')")
        }

        withMigratedDatabase { database ->
            val occurrences = database.routines().occurrencesOn(3, LocalDate.parse("2026-10-23"))
            assertEquals(listOf(OccurrenceStatus.Done to null), occurrences.map { it.status to it.remindAt })
        }
    }

    @Test
    fun `version 4 moves to version 5 giving each routine three follow-ups 30 minutes apart`() {
        createAtVersion(4) {
            it.execSQL(
                "INSERT INTO routines (id, name, description, times, repetition, planned_from) " +
                    "VALUES (3, 'Tabletten', '', '08:00,20:00', 'daily', '2026-10-23T06:00:00Z')",
            )
            it.execSQL(
                "INSERT INTO occurrences (routine_id, date, time, status, remind_at) " +
                    "VALUES (3, '2026-10-23', '08:00', 'open', '2026-10-23T06:10:00Z')",
            )
        }

        withMigratedDatabase { database ->
            assertEquals(FollowUps(count = 3, everyMinutes = 30), database.routines().get(3)?.followUps)
            val occurrences = database.routines().occurrencesOn(3, LocalDate.parse("2026-10-23"))
            assertEquals(listOf(Instant.parse("2026-10-23T06:10:00Z")), occurrences.map { it.remindAt })
        }
    }

    @Test
    fun `version 5 moves to version 6 keeping how each routine reminds again and with no full-screen alarm`() {
        createAtVersion(5) {
            it.execSQL(
                "INSERT INTO routines (id, name, description, times, repetition, planned_from, follow_up_count, " +
                    "follow_up_minutes) VALUES (3, 'Tabletten', '', '08:00', 'daily', '2026-10-23T06:00:00Z', 5, 10)",
            )
        }

        withMigratedDatabase { database ->
            val routine = database.routines().get(3)
            assertEquals(FollowUps(count = 5, everyMinutes = 10) to false, routine?.let { it.followUps to it.fullScreen })
        }
    }

    @Test
    fun `version 6 moves to version 7 giving each routine four hours to catch up on`() {
        createAtVersion(6) {
            it.execSQL(
                "INSERT INTO routines (id, name, description, times, repetition, planned_from, " +
                    "follow_up_count, follow_up_minutes, full_screen) " +
                    "VALUES (3, 'Tabletten', '', '08:00', 'daily', '2026-10-23T06:00:00Z', 5, 10, 1)",
            )
        }

        withMigratedDatabase { database ->
            val routine = database.routines().get(3)
            assertEquals(TimeWindow(hours = 4) to true, routine?.let { it.window to it.fullScreen })
        }
    }

    @Test
    fun `version 7 moves to version 8 keeping each routine active at normal priority and without a checklist`() {
        createAtVersion(7) {
            it.execSQL(
                "INSERT INTO routines (id, name, description, times, repetition, planned_from, follow_up_count, " +
                    "follow_up_minutes, full_screen, window_hours, window_until_midnight) " +
                    "VALUES (3, 'Tabletten', '', '08:00', 'daily', '2026-10-23T06:00:00Z', 5, 10, 1, 6, 0)",
            )
            it.execSQL("INSERT INTO occurrences (id, routine_id, date, time, status) VALUES (9, 3, '2026-10-23', '08:00', 'done')")
        }

        withMigratedDatabase { database ->
            val routine = database.routines().get(3)
            assertEquals(
                listOf(Priority.Normal, RoutineStatus.Active, TimeWindow(hours = 6)),
                routine?.let { listOf(it.priority, it.status, it.window) },
            )
            assertEquals(emptyList<ChecklistItem>(), database.routines().checklist(3))

            // The checklist belongs to the routine and its ticks to the occurrence: deleting the routine takes them along.
            database.routines().insertChecklistItems(listOf(ChecklistItem(routineId = 3, position = 0, text = "Mit Wasser")))
            val item = database.routines().checklist(3).single()
            database.routines().insertTicks(listOf(ChecklistTick(occurrenceId = 9, itemId = item.id)))
            assertEquals(listOf(item.id), database.routines().ticks(9))
            database.routines().delete(3)
            assertEquals(emptyList<Long>(), database.routines().ticks(9))
        }
    }

    @Test
    fun `version 8 moves to version 9 keeping each routine and giving it no tags yet`() {
        createAtVersion(8) {
            it.execSQL(
                "INSERT INTO routines (id, name, description, times, repetition, planned_from, follow_up_count, " +
                    "follow_up_minutes, full_screen, window_hours, window_until_midnight, priority, status) " +
                    "VALUES (3, 'Tabletten', '', '08:00', 'daily', '2026-10-23T06:00:00Z', 5, 10, 1, 6, 0, 'high', 'paused')",
            )
        }

        withMigratedDatabase { database ->
            val routine = database.routines().get(3)
            assertEquals(listOf(Priority.High, RoutineStatus.Paused), routine?.let { listOf(it.priority, it.status) })
            assertEquals(emptyList<Long>(), database.routines().tagIdsOf(3))

            // A tag deleted is taken off the routine, and a routine deleted takes its tags along.
            val work = database.tags().insert(Tag(name = "Arbeit"))
            val health = database.tags().insert(Tag(name = "Gesundheit"))
            database.routines().tag(3, listOf(work, health))
            database.tags().delete(work)
            assertEquals(listOf(health), database.routines().tagIdsOf(3))
            database.routines().delete(3)
            assertEquals(emptyList<RoutineTag>(), database.routines().observeRoutineTags().first())
        }
    }

    @Test
    fun `version 9 moves to version 10 keeping each routine and its tags and with no places yet`() {
        createAtVersion(9) {
            it.execSQL(
                "INSERT INTO routines (id, name, description, times, repetition, planned_from, follow_up_count, " +
                    "follow_up_minutes, full_screen, window_hours, window_until_midnight, priority, status) " +
                    "VALUES (3, 'Tabletten', '', '08:00', 'daily', '2026-10-23T06:00:00Z', 5, 10, 1, 6, 0, 'high', 'active')",
            )
            it.execSQL("INSERT INTO tags (id, name) VALUES (4, 'Gesundheit')")
            it.execSQL("INSERT INTO routine_tags (routine_id, tag_id) VALUES (3, 4)")
        }

        withMigratedDatabase { database ->
            assertEquals(listOf(4L), database.routines().tagIdsOf(3))
            assertEquals(emptyList<PlaceWithWifis>(), database.places().all())

            // A place deleted takes its WLANs along.
            val home = database.places().insert(Place(name = "Zuhause"))
            database.places().insertWifis(listOf(PlaceWifi(home, "FritzBox")))
            assertEquals(listOf("FritzBox"), database.places().all().single().ssids)
            database.places().delete(home)
            assertEquals(emptyList<PlaceWithWifis>(), database.places().all())
        }
    }

    @Test
    fun `version 10 moves to version 11 keeping each place and letting a WLAN be at several`() {
        createAtVersion(10) {
            it.execSQL("INSERT INTO places (id, name) VALUES (1, 'Uni')")
            it.execSQL("INSERT INTO places (id, name) VALUES (2, 'Arbeit')")
            it.execSQL("INSERT INTO place_wifis (place_id, ssid) VALUES (1, 'eduroam')")
        }

        withMigratedDatabase { database ->
            database.places().insertWifis(listOf(PlaceWifi(2, "eduroam")))
            assertEquals(
                listOf("Uni" to listOf("eduroam"), "Arbeit" to listOf("eduroam")),
                database.places().all().map { it.name to it.ssids },
            )
        }
    }

    @Test
    fun `version 11 moves to version 12 with no routine bound to a place and nothing waiting`() {
        createAtVersion(11) {
            it.execSQL(
                "INSERT INTO routines (id, name, description, times, repetition, planned_from, follow_up_count, " +
                    "follow_up_minutes, full_screen, window_hours, window_until_midnight, priority, status) " +
                    "VALUES (3, 'Tabletten', '', '08:00', 'daily', '2026-10-23T06:00:00Z', 3, 30, 0, 4, 0, 'normal', 'active')",
            )
            it.execSQL("INSERT INTO occurrences (id, routine_id, date, time, status) VALUES (9, 3, '2026-10-23', '08:00', 'open')")
            it.execSQL("INSERT INTO places (id, name) VALUES (1, 'Zuhause')")
        }

        withMigratedDatabase { database ->
            assertEquals(listOf(false), database.routines().occurrencesOn(3, LocalDate.parse("2026-10-23")).map { it.waiting })
            assertEquals(emptyList<Long>(), database.routines().placeIdsOf(3))

            // A place deleted is taken off the routines bound to it.
            database.routines().bind(3, listOf(1L))
            assertEquals(listOf(1L), database.routines().placeIdsOf(3))
            database.places().delete(1)
            assertEquals(emptyList<Long>(), database.routines().placeIdsOf(3))
        }
    }

    @Test
    fun `version 12 moves to version 13 keeping each place with its WLANs and no GPS area`() {
        createAtVersion(12) {
            it.execSQL("INSERT INTO places (id, name) VALUES (1, 'Zuhause')")
            it.execSQL("INSERT INTO place_wifis (place_id, ssid) VALUES (1, 'FritzBox')")
        }

        withMigratedDatabase { database ->
            val places = database.places().all()
            assertEquals(listOf(Triple("Zuhause", listOf("FritzBox"), null)), places.map { Triple(it.name, it.ssids, it.area) })

            val gym = Area(latitude = 52.520008, longitude = 13.404954, radius = 150)
            database.places().insert(Place(name = "Gym", area = gym))
            assertEquals(listOf(null, gym), database.places().all().map { it.area })
        }
    }

    private fun startOf(date: String) = LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant()

    private fun withMigratedDatabase(block: suspend (HubDatabase) -> Unit) {
        val database = HubDatabase.open(context, key, name)
        try {
            runBlocking { block(database) }
        } finally {
            database.close()
        }
    }

    /** An encrypted database as [version] of the app created it, built from the schema Room exported for it. */
    private fun createAtVersion(version: Int, fill: (SQLCipherDatabase) -> Unit) {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("${HubDatabase::class.java.name}/$version.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
            .getJSONObject("database")

        System.loadLibrary("sqlcipher")
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        val flags = SQLCipherDatabase.OPEN_READWRITE or SQLCipherDatabase.CREATE_IF_NECESSARY
        SQLCipherDatabase.openDatabase(file.path, HubDatabase.rawKey(key), null, flags, null, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE_NAME, table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_NAME, table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            fill(db)
        }
    }

    private companion object {
        const val TABLE_NAME = "\${TABLE_NAME}"
    }
}
