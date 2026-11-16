package de.emilyshub.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import de.emilyshub.island.IslandOwnFiles
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * `tracking.db` of an older version is brought up to this one by its own migrations, its data kept; Room checks the
 * outcome against the schema it exported, so that a migration that misses anything fails here.
 */
@RunWith(AndroidJUnit4::class)
class TrackingMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(TrackingDatabase.NAME)
    }

    @Test
    fun `version 1 gains the entries and medications and keeps its settings`() {
        createTrackingAsExported(context, version = 1) { it.execSQL("INSERT INTO tracking_setting (`key`, value) VALUES ('probe', 'kept')") }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                assertEquals("kept", database.settings().get("probe"))
                val day = LocalDate.parse("2026-11-15")
                database.entries().insertMorning(MorningEntry(date = day, restingPulse = 64, recordedOn = day))
                assertEquals(64, database.entries().morning(day)?.restingPulse)
                val id = database.medications().insert(Medication(name = "Medikament A", times = listOf(LocalTime.of(8, 0)), start = day))
                assertEquals("Medikament A", database.medications().get(id)?.name)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 2 gains the values from Health Connect and keeps its entries`() {
        createTrackingAsExported(context, version = 2) {
            it.execSQL("INSERT INTO morning_entry (date, resting_pulse, measured_differently, recorded_on) VALUES ('2026-11-15', 64, 0, '2026-11-15')")
        }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                assertEquals(64, database.entries().morning(LocalDate.parse("2026-11-15"))?.restingPulse)
                val at = Instant.parse("2026-11-15T10:00:00Z")
                database.garmin().put(listOf(GarminSample.of("hr#0", SampleKind.HeartRate, at, at, 64, "com.example", DeviceKind.Watch)))
                assertEquals(1, database.garmin().count())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 3 gains the Stehtests and keeps its medications`() {
        createTrackingAsExported(context, version = 3) {
            it.execSQL(
                "INSERT INTO medication (id, name, form, times, purpose, thrombosis_risk, lowers_pulse, side_effects, start) " +
                    "VALUES (1, 'Medikament A', '', '08:00', '', 0, 0, '', '2026-11-01')",
            )
        }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                assertEquals("Medikament A", database.medications().get(1)?.name)
                val id = database.standTests().insert(StandTest(variant = StandVariant.Leaning, startedAt = Instant.parse("2026-11-15T10:00:00Z")))
                database.standTests().insertIntakes(listOf(StandIntake(id, 1, 120)))
                database.standTests().putPoint(StandPoint(id, 1, pulse = 80))
                assertEquals(1, database.standTests().points(id).size)
                assertEquals(120, database.standTests().intakes(id).single().minutesBefore)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 4 gains the events and keeps its Stehtests`() {
        createTrackingAsExported(context, version = 4) {
            it.execSQL(
                "INSERT INTO stand_test (id, variant, started_at, abort_reason, finished) VALUES (1, 'standing', '2026-11-15T10:00:00Z', '', 1)",
            )
        }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                assertEquals(StandVariant.Standing, database.standTests().get(1)?.variant)
                val id = database.events().insert(TrackingEvent(kind = EventKind.Bath, at = Instant.parse("2026-11-15T10:00:00Z"), skinAreas = setOf(SkinArea.Arms)))
                assertEquals(setOf(SkinArea.Arms), database.events().get(id)?.skinAreas)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 5 gains the exertions and episodes and keeps its events`() {
        createTrackingAsExported(context, version = 5) {
            it.execSQL("INSERT INTO tracking_event (id, kind, at, skin_areas) VALUES (1, 'sauna', '2026-11-15T10:00:00Z', '')")
        }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                assertEquals(EventKind.Sauna, database.events().get(1)?.kind)
                val exertion = database.exertions().insert(Exertion(date = LocalDate.parse("2026-11-14"), kind = ExertionKind.Physical, strength = 3))
                val episode = database.episodes().insert(Episode(kind = EpisodeKind.Crash, startDate = LocalDate.parse("2026-11-15")))
                database.episodes().putTriggers(listOf(EpisodeTrigger(episode, exertion, Certainty.Sure)))
                assertEquals(Certainty.Sure, database.episodes().triggers(episode).single().certainty)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 6 gains the social load and keeps its evenings`() {
        createTrackingAsExported(context, version = 6) {
            it.execSQL("INSERT INTO evening_entry (date, load_emotional, note, recorded_on) VALUES ('2026-11-15', 2, '', '2026-11-15')")
        }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                val day = LocalDate.parse("2026-11-15")
                val evening = checkNotNull(database.entries().evening(day))
                assertEquals(2, evening.loadEmotional)
                assertEquals(null, evening.loadSocial)
                database.entries().insertEvening(evening.copy(date = day.minusDays(1), loadSocial = 3))
                assertEquals(3, database.entries().evening(day.minusDays(1))?.loadSocial)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 7 gains the photos of the events and keeps its events`() {
        createTrackingAsExported(context, version = 7) {
            it.execSQL("INSERT INTO tracking_event (id, kind, at, skin_areas) VALUES (1, 'bath', '2026-11-15T10:00:00Z', 'legs')")
        }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                assertEquals(EventKind.Bath, database.events().get(1)?.kind)
                val id = database.photos().insert(EventPhoto(eventId = 1, takenAt = Instant.parse("2026-11-15T10:05:00Z"), file = "0".repeat(32)))
                assertEquals("0".repeat(32), database.photos().file(id))
                database.events().delete(1)
                assertEquals(0, database.photos().count(1))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 8 keeps one's own pacing threshold, as holding from the first day on`() {
        createTrackingAsExported(context, version = 8) {
            it.execSQL("INSERT INTO tracking_setting (`key`, value) VALUES ('pacing.own_threshold', '110'), ('probe', 'kept')")
        }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                assertEquals(listOf(OwnThreshold(LocalDate.parse("1970-01-01"), 110)), database.exertions().ownThresholds())
                assertEquals(null, database.settings().get("pacing.own_threshold"))
                assertEquals("kept", database.settings().get("probe"))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 8 without an own pacing threshold has none`() {
        createTrackingAsExported(context, version = 8) {
            it.execSQL("INSERT INTO tracking_setting (`key`, value) VALUES ('pacing.own_threshold', '')")
        }

        val database = testTrackingDatabase(context)
        try {
            runBlocking { assertEquals(emptyList<OwnThreshold>(), database.exertions().ownThresholds()) }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 9 moves the photos out of the database into files, once Tracking is open`() {
        createTrackingAsExported(context, version = 9) {
            it.execSQL("INSERT INTO tracking_event (id, kind, at, skin_areas) VALUES (1, 'bath', '2026-11-15T10:00:00Z', 'legs')")
            it.execSQL("INSERT INTO event_photo (id, event_id, taken_at, jpeg) VALUES (4, 1, '2026-11-15T10:05:00Z', x'010203'), (5, 1, '2026-11-15T10:06:00Z', x'0405')")
        }
        val files = IslandOwnFiles(File(context.cacheDir, "moved")).store(ByteArray(32) { 7 })

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                EventPhotos.moveOutOfDatabase(database, files)
                val moved = database.photos().of(1).map { checkNotNull(files.read(checkNotNull(database.photos().file(it.id)))).toList() }
                assertEquals(listOf(listOf<Byte>(1, 2, 3), listOf<Byte>(4, 5)), moved)
                assertEquals(listOf(4L, 5L), database.photos().of(1).map { it.id })
                val left = database.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE name = '${EventPhotos.TO_MOVE}'").use { it.count }
                assertEquals("nothing left in the database", 0, left)
                // Once more, nothing to do.
                EventPhotos.moveOutOfDatabase(database, files)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `with no room for the files, the photos stay where they wait, are read there, and move the next time`() {
        createTrackingAsExported(context, version = 9) {
            it.execSQL("INSERT INTO tracking_event (id, kind, at, skin_areas) VALUES (1, 'bath', '2026-11-15T10:00:00Z', 'legs')")
            it.execSQL("INSERT INTO event_photo (id, event_id, taken_at, jpeg) VALUES (4, 1, '2026-11-15T10:05:00Z', x'010203'), (5, 1, '2026-11-15T10:06:00Z', x'0405'), (6, 1, '2026-11-15T10:07:00Z', x'06')")
        }
        val key = ByteArray(32) { 7 }
        // Where the folder would be, a file: no file can be written, as with the phone's storage full.
        val full = IslandOwnFiles(File(context.cacheDir, "full").apply { writeText("") }).store(key)
        val room = IslandOwnFiles(File(context.cacheDir, "room")).store(key)

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                EventPhotos.moveOutOfDatabase(database, full)
                val waiting = database.photos().of(1).map { checkNotNull(EventPhotos.read(database, full, checkNotNull(database.photos().file(it.id)))).toList() }
                assertEquals(listOf(listOf<Byte>(1, 2, 3), listOf<Byte>(4, 5), listOf<Byte>(6)), waiting)
                database.photos().delete(6)

                EventPhotos.moveOutOfDatabase(database, room)

                val moved = database.photos().of(1).map { checkNotNull(room.read(checkNotNull(database.photos().file(it.id)))).toList() }
                assertEquals(listOf(listOf<Byte>(1, 2, 3), listOf<Byte>(4, 5)), moved)
                assertEquals("the one deleted meanwhile is not moved", 2, File(context.cacheDir, "room").list()!!.size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 10 gets the monthly questionnaires, the rest kept`() {
        createTrackingAsExported(context, version = 10) {
            it.execSQL("INSERT INTO tracking_setting (`key`, value) VALUES ('probe', 'kept')")
        }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                assertEquals("kept", database.settings().get("probe"))
                assertEquals(null, database.funcap().draft())
                val id = database.funcap().insert(FuncapForm(startedOn = LocalDate.parse("2026-11-16")))
                database.funcap().put(FuncapAnswer(id, 1, 6))
                assertEquals(listOf(FuncapAnswer(id, 1, 6)), database.funcap().answers(id))
                database.funcap().delete(id)
                assertEquals(emptyList<FuncapAnswer>(), database.funcap().answers(id))
            }
        } finally {
            database.close()
        }
    }
}
