package de.emilyshub.tracking

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The events of Tracking (SPEC.md §17, "Ereignisse"), with made-up values and names only. The fixture's clock stands at
 * Sunday, 15 November 2026, 11:00 in Berlin.
 */
@RunWith(AndroidJUnit4::class)
class TrackingEventsTest {

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext())
    private val events = fixture.events
    private val now = Instant.parse("2026-11-15T10:00:00Z")

    @After
    fun close() = fixture.close()

    private fun create(kind: EventKind = EventKind.Shower) = checkNotNull(runBlocking { events.create(kind) })

    private fun stored(id: Long) = checkNotNull(runBlocking { events.get(id) })

    @Test
    fun `a new event is of its kind, now, with the hours since today's intakes`() = runBlocking {
        fixture.medications.save(Medication(name = "Medikament A", times = listOf(LocalTime.of(8, 0)), start = LocalDate.parse("2026-11-15")))

        val id = create(EventKind.Sauna)

        val report = checkNotNull(events.report(id))
        assertEquals(EventKind.Sauna, report.event.kind)
        assertEquals(now, report.event.at)
        assertEquals(mapOf("Medikament A" to 180), report.intakes.associate { it.first.name to it.second })
    }

    @Test
    fun `fields are added later, and values out of their range are left out`() = runBlocking {
        val id = create()
        assertTrue(events.save(stored(id).copy(water = WaterTemperature.Hot, durationMinutes = 15, posture = Posture.Standing, pulseBefore = 80)))

        fixture.at("2026-11-15T11:30:00Z")
        assertTrue(
            events.save(
                stored(id).copy(
                    skinAreas = setOf(SkinArea.Legs, SkinArea.FingersToes),
                    skinPattern = SkinPattern.Reticular,
                    skinFadeMinutes = 45,
                    recoveredMinutes = 60,
                    spo2 = 140,
                    dizziness = 7,
                ),
            ),
        )

        val event = stored(id)
        assertEquals(WaterTemperature.Hot, event.water)
        assertEquals(80, event.pulseBefore)
        assertEquals(setOf(SkinArea.Legs, SkinArea.FingersToes), event.skinAreas)
        assertEquals(SkinPattern.Reticular, event.skinPattern)
        assertEquals(45, event.skinFadeMinutes)
        assertEquals(60, event.recoveredMinutes)
        assertNull(event.spo2)
        assertNull(event.dizziness)
    }

    @Test
    fun `a water temperature is kept only for water`() = runBlocking {
        val id = create(EventKind.Heat)

        events.save(stored(id).copy(water = WaterTemperature.Warm))

        assertNull(stored(id).water)
    }

    @Test
    fun `ending starts the timer now, once`() = runBlocking {
        val id = create()
        fixture.at("2026-11-15T10:12:00Z")

        assertTrue(events.end(id))
        fixture.at("2026-11-15T10:13:00Z")
        assertFalse(events.end(id))

        assertEquals(Instant.parse("2026-11-15T10:12:00Z"), stored(id).endedAt)
    }

    @Test
    fun `the curve runs from 15 minutes before to 30 after, each with its tier, B in motion`() = runBlocking {
        fixture.health.grantedNow = HealthSource.PERMISSIONS
        val id = create()
        fixture.at("2026-11-15T10:10:00Z")
        events.end(id)
        val moving = Instant.parse("2026-11-15T10:05:00Z")
        fixture.health.samples += listOf(
            heartRate("too early", Instant.parse("2026-11-15T09:44:00Z"), 60),
            heartRate("resting", Instant.parse("2026-11-15T09:50:00Z"), 65),
            heartRate("moving", moving, 110),
            heartRate("too late", Instant.parse("2026-11-15T10:41:00Z"), 70),
            GarminSample.of("steps", SampleKind.Steps, moving.minusSeconds(30), moving.plusSeconds(30), 40, HealthSource.GARMIN_CONNECT, DeviceKind.Watch),
            GarminSample.of("steps later", SampleKind.Steps, Instant.parse("2026-11-15T10:50:00Z"), Instant.parse("2026-11-15T10:51:00Z"), 0, HealthSource.GARMIN_CONNECT, DeviceKind.Watch),
        )
        fixture.at("2026-11-15T11:00:00Z")
        fixture.garmin.read()

        val curve = checkNotNull(events.report(id)).curve

        assertEquals(listOf(65 to Tier.A, 110 to Tier.B), curve.map { it.bpm to it.tier })
    }

    @Test
    fun `the list holds the events, the latest first, and one deleted is gone`() = runBlocking {
        val first = create()
        fixture.at("2026-11-15T12:00:00Z")
        val second = create(EventKind.LongStanding)

        assertEquals(listOf(second, first), events.list()!!.map { it.id })

        events.delete(second)
        assertEquals(listOf(first), events.list()!!.map { it.id })
    }

    @Test
    fun `the time of an event is changed in it, and the list follows`() = runBlocking {
        val first = create()
        val second = create()
        events.save(stored(first).copy(at = Instant.parse("2026-11-15T12:00:00Z")))

        assertEquals(listOf(first, second), events.list()!!.map { it.id })
    }

    private fun heartRate(key: String, at: Instant, bpm: Long) =
        GarminSample.of(key, SampleKind.HeartRate, at, at, bpm, HealthSource.GARMIN_CONNECT, DeviceKind.Watch)
}
