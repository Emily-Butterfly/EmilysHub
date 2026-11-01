package de.emilyshub

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The time of day for all that shows it: the time now each minute and the day it is, anew at midnight and as the
 * phone's clock or time zone changes. The waits run on the test's virtual time; the clock is moved by hand alongside.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayTest {

    // Friday, 23 October 2026, 07:00:20 in Berlin.
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:20Z"), ZoneId.of("Europe/Berlin"))
    private val clockChanges = MutableSharedFlow<Unit>()
    private val day = Day(clock, clockChanges)

    @Test
    fun `today, now and the day of a moment are as the clock has them, in its time zone`() {
        assertEquals(Instant.parse("2026-10-23T05:00:20Z"), day.now())
        assertEquals(LocalDate.of(2026, 10, 23), day.today())
        assertEquals(ZoneId.of("Europe/Berlin"), day.zone())
        // 23:30 on Thursday in Berlin.
        assertEquals(LocalDate.of(2026, 10, 22), day.dayOf(Instant.parse("2026-10-22T21:30:00Z")))

        clock.timeZone = ZoneId.of("Asia/Tokyo")

        // 06:30 on Friday in Tokyo.
        assertEquals(LocalDate.of(2026, 10, 23), day.dayOf(Instant.parse("2026-10-22T21:30:00Z")))
    }

    @Test
    fun `the time now comes at once, then again just after each full minute`() = runTest {
        val seen = collect { day.minutes() }

        assertEquals(listOf(Instant.parse("2026-10-23T05:00:20Z")), seen)

        clock.now = Instant.parse("2026-10-23T05:01:01Z")
        // 40 seconds to the full minute, and one more to be sure it has turned.
        advanceTimeBy(40_999)
        runCurrent()
        assertEquals(1, seen.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(Instant.parse("2026-10-23T05:01:01Z"), seen.last())

        clock.now = Instant.parse("2026-10-23T05:02:01Z")
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf("05:00:20", "05:01:01", "05:02:01").map { Instant.parse("2026-10-23T${it}Z") }, seen)
    }

    @Test
    fun `the day turns within a second of midnight, and each day comes once`() = runTest {
        clock.now = Instant.parse("2026-10-23T21:59:30Z") // 23:59:30 in Berlin
        val seen = collect { day.dates() }

        clock.now = Instant.parse("2026-10-23T22:00:01Z") // 00:00:01 on Saturday
        advanceTimeBy(31_000)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24)), seen)

        clock.now = Instant.parse("2026-10-23T22:01:01Z")
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24)), seen)
    }

    @Test
    fun `a new time zone gives the day it is there at once`() = runTest {
        clock.now = Instant.parse("2026-10-23T20:00:00Z") // Friday 22:00 in Berlin
        val seen = collect { day.dates() }

        clock.timeZone = ZoneId.of("Asia/Tokyo") // Saturday 05:00 there
        clockChanges.emit(Unit)
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24)), seen)
    }

    @Test
    fun `after the clock is set, the time comes at once, and the next minute is measured on the new clock`() = runTest {
        val seen = collect { day.minutes() }

        advanceTimeBy(10_000)
        clock.now = Instant.parse("2026-10-23T05:30:50Z")
        clockChanges.emit(Unit)
        runCurrent()
        assertEquals(Instant.parse("2026-10-23T05:30:50Z"), seen.last())

        // 10 seconds to the full minute on the new clock, and one more, not the 51 left on the old one.
        clock.now = Instant.parse("2026-10-23T05:31:01Z")
        advanceTimeBy(10_999)
        runCurrent()
        assertEquals(2, seen.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(Instant.parse("2026-10-23T05:31:01Z"), seen.last())
    }

    /** What [flow] gives, collected for the rest of the test. */
    private fun <T> TestScope.collect(flow: () -> kotlinx.coroutines.flow.Flow<T>): List<T> {
        val seen = mutableListOf<T>()
        backgroundScope.launch { flow().collect { seen += it } }
        runCurrent()
        return seen
    }
}
