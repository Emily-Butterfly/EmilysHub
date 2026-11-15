package de.emilyshub.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
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
                val id = database.photos().insert(EventPhoto(eventId = 1, takenAt = Instant.parse("2026-11-15T10:05:00Z"), jpeg = byteArrayOf(1, 2, 3)))
                assertEquals(listOf(1.toByte(), 2.toByte(), 3.toByte()), database.photos().jpeg(id)?.toList())
                database.events().delete(1)
                assertEquals(0, database.photos().count(1))
            }
        } finally {
            database.close()
        }
    }
}
