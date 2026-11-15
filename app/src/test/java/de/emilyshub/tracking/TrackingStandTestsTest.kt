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
 * The Stehtests of Tracking (SPEC.md §17, "Stehtest"), stored as they go, with made-up values and names only. The
 * fixture's clock stands at Sunday, 15 November 2026, 11:00 in Berlin.
 */
@RunWith(AndroidJUnit4::class)
class TrackingStandTestsTest {

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext())
    private val standTests = fixture.standTests
    private val all = ChecklistItem.entries.toSet()
    private val today = LocalDate.parse("2026-11-15")

    @After
    fun close() = fixture.close()

    private fun begin(variant: StandVariant = StandVariant.Standing, before: StandBefore = StandBefore()) =
        checkNotNull(runBlocking { standTests.begin(variant, all, before) })

    private fun stoodUp(variant: StandVariant = StandVariant.Standing): Long {
        val id = begin(variant)
        fixture.at("2026-11-15T10:06:00Z")
        assertTrue(runBlocking { standTests.standUp(id, pulse = 60, systolic = 120, diastolic = 80) })
        return id
    }

    @Test
    fun `without the whole checklist ticked no test begins`() = runBlocking {
        assertNull(standTests.begin(StandVariant.Standing, all - ChecklistItem.BesideBed, StandBefore()))
        assertEquals(emptyList<StandTestSummary>(), standTests.list())
    }

    @Test
    fun `it keeps the time, what came before, and the hours since today's intakes`() = runBlocking {
        val a = checkNotNull(fixture.medications.save(Medication(name = "Medikament A", times = listOf(LocalTime.of(7, 30), LocalTime.of(20, 0)), start = today)))
        val b = checkNotNull(fixture.medications.save(Medication(name = "Medikament B", times = listOf(LocalTime.of(9, 0)), start = today)))
        fixture.medications.save(Medication(name = "Medikament C", times = listOf(LocalTime.of(8, 0)), start = today.minusDays(9), end = today.minusDays(1)))
        fixture.medications.save(Medication(name = "Medikament D", times = listOf(LocalTime.of(12, 0)), start = today))

        val id = begin(before = StandBefore(caffeine = 1, mealMinutesBefore = 90, fluidDecilitres = 5))

        val report = checkNotNull(standTests.report(id))
        assertEquals(Instant.parse("2026-11-15T10:00:00Z"), report.test.startedAt)
        assertEquals(1, report.test.caffeine)
        assertEquals(90, report.test.mealMinutesBefore)
        assertEquals(5, report.test.fluidDecilitres)
        assertEquals(
            "A at 07:30, B at 09:00; C ended, D still to come",
            mapOf("Medikament A" to 210, "Medikament B" to 120),
            report.intakes.associate { it.first.name to it.second },
        )
        assertTrue(a != b)
    }

    @Test
    fun `an intake entered today otherwise than planned counts as entered`() = runBlocking {
        val a = checkNotNull(fixture.medications.save(Medication(name = "Medikament A", times = listOf(LocalTime.of(7, 30)), start = today)))
        val b = checkNotNull(fixture.medications.save(Medication(name = "Medikament B", times = listOf(LocalTime.of(9, 0)), start = today)))
        fixture.entries.saveEvening(
            EveningEntry(date = today, recordedOn = today),
            emptyMap(),
            listOf(TrackingEntries.deviation(today, a, LocalTime.of(10, 30)), TrackingEntries.deviation(today, b, null)),
            replacing = null,
        )

        val report = checkNotNull(standTests.report(begin()))

        assertEquals("A at 10:30, B forgotten", mapOf("Medikament A" to 30), report.intakes.associate { it.first.name to it.second })
    }

    @Test
    fun `standing up needs five minutes lying down`() = runBlocking {
        val id = begin()
        fixture.at("2026-11-15T10:04:59Z")
        assertFalse(standTests.standUp(id, 60, null, null))
        fixture.at("2026-11-15T10:05:00Z")
        assertTrue(standTests.standUp(id, 60, null, null))

        val test = checkNotNull(standTests.run(id)).test
        assertEquals(Instant.parse("2026-11-15T10:05:00Z"), test.standingAt)
        assertEquals(60, test.lyingPulse)
        assertNull(test.lyingSystolic)
    }

    @Test
    fun `a test run through is finished with its last point, and evaluated`() = runBlocking {
        val id = stoodUp()
        listOf(1 to 80, 3 to 92, 5 to 95, 8 to 88).forEach { (minute, pulse) ->
            assertTrue(standTests.record(StandPoint(id, minute, pulse = pulse, dizziness = 1)))
            assertTrue(checkNotNull(standTests.run(id)).test.running)
        }
        assertEquals(10, checkNotNull(standTests.run(id)).nextMinute)
        assertTrue(standTests.record(StandPoint(id, 10, pulse = 90, systolic = 110, diastolic = 75, legsDiscoloured = true)))

        val report = checkNotNull(standTests.report(id))
        assertTrue(report.test.finished)
        assertEquals(35, report.result.maxRise)
        assertEquals(listOf(3, 5, 10), report.result.risesAtLeast30)
        assertTrue(report.result.complete)
        assertEquals(listOf(id), standTests.list()!!.map { it.test.id })
        assertFalse("no more points once finished", standTests.record(StandPoint(id, 10, pulse = 70)))
    }

    @Test
    fun `values out of their range are left out, and a minute not of the variant is not kept`() = runBlocking {
        val id = stoodUp()

        assertFalse(standTests.record(StandPoint(id, 2, pulse = 80)))
        assertTrue(standTests.record(StandPoint(id, 1, pulse = 400, systolic = 20, dizziness = 5)))

        val point = checkNotNull(standTests.run(id)).points.single()
        assertNull(point.pulse)
        assertNull(point.systolic)
        assertNull(point.dizziness)
    }

    @Test
    fun `no point before standing up`() = runBlocking {
        val id = begin()
        assertFalse(standTests.record(StandPoint(id, 1, pulse = 80)))
    }

    @Test
    fun `aborted, it keeps what was measured with the minute and the reason`() = runBlocking {
        val id = stoodUp()
        standTests.record(StandPoint(id, 1, pulse = 90))
        standTests.record(StandPoint(id, 3, pulse = 100))
        fixture.at("2026-11-15T10:10:30Z")

        assertTrue(standTests.abort(id, AbortCause.Pressed, "  schwindelig  "))

        val report = checkNotNull(standTests.report(id))
        assertEquals(AbortCause.Pressed, report.test.abortCause)
        assertEquals(4, report.test.abortedMinute)
        assertEquals("schwindelig", report.test.abortReason)
        assertFalse(report.test.running)
        assertEquals(listOf(1, 3), report.result.points.map { it.minute })
        assertFalse("once ended, it stays so", standTests.abort(id, AbortCause.Locked, ""))
        assertFalse(standTests.record(StandPoint(id, 5, pulse = 100)))
    }

    @Test
    fun `aborted lying down, it has no minute`() = runBlocking {
        val id = begin()
        fixture.at("2026-11-15T10:03:00Z")

        assertTrue(standTests.abort(id, AbortCause.Pressed, ""))

        assertNull(checkNotNull(standTests.report(id)).test.abortedMinute)
    }

    @Test
    fun `Garmin's heart rate for the time is the curve, and fills a missing point`() = runBlocking {
        fixture.health.grantedNow = HealthSource.PERMISSIONS
        val standing = Instant.parse("2026-11-15T10:06:00Z")
        fixture.health.samples += listOf(
            heartRate("before", Instant.parse("2026-11-15T09:30:00Z"), 70),
            heartRate("lying", standing.minusSeconds(10), 61),
            heartRate("three", standing.plusSeconds(182), 99),
        )
        val id = stoodUp()
        standTests.record(StandPoint(id, 1, pulse = 80))
        standTests.record(StandPoint(id, 3))
        fixture.at("2026-11-15T10:30:00Z")
        fixture.garmin.read()

        val report = checkNotNull(standTests.report(id))

        assertEquals("from lying down on", listOf(61, 99), report.curve.map { it.bpm })
        assertEquals(99, report.result.points.single { it.minute == 3 }.pulse)
        assertTrue(report.result.points.single { it.minute == 3 }.filled)
    }

    @Test
    fun `the list holds all tests, the latest first`() = runBlocking {
        val first = begin()
        standTests.abort(first, AbortCause.Pressed, "")
        fixture.at("2026-11-15T11:00:00Z")
        val second = begin(StandVariant.Leaning)

        assertEquals(listOf(second, first), standTests.list()!!.map { it.test.id })
    }

    @Test
    fun `aborted with a point typed but not saved, the point is kept first`() = runBlocking {
        val id = stoodUp()
        fixture.at("2026-11-15T10:07:10Z")

        assertTrue(standTests.abort(id, AbortCause.Locked, "", typed = StandPoint(id, 1, pulse = 97)))

        val report = checkNotNull(standTests.report(id))
        assertEquals(AbortCause.Locked, report.test.abortCause)
        assertEquals(97, report.result.points.single().pulse)
    }

    @Test
    fun `the curve of an aborted test ends a minute after the abort`() = runBlocking {
        fixture.health.grantedNow = HealthSource.PERMISSIONS
        val standing = Instant.parse("2026-11-15T10:06:00Z")
        fixture.health.samples += listOf(heartRate("during", standing.plusSeconds(100), 90), heartRate("after", standing.plusSeconds(300), 70))
        val id = stoodUp()
        fixture.at("2026-11-15T10:08:30Z")
        standTests.abort(id, AbortCause.Pressed, "")
        fixture.at("2026-11-15T10:30:00Z")
        fixture.garmin.read()

        assertEquals(listOf(90), checkNotNull(standTests.report(id)).curve.map { it.bpm })
    }

    private fun heartRate(key: String, at: Instant, bpm: Long) =
        GarminSample.of(key, SampleKind.HeartRate, at, at, bpm, HealthSource.GARMIN_CONNECT, DeviceKind.Watch)
}
