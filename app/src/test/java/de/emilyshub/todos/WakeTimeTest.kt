package de.emilyshub.todos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * When a to-do wakes (SPEC.md §5): some days before it is due at a time, on the local wall clock of the phone's time
 * zone, or at a fixed moment, also without a due.
 */
class WakeTimeTest {

    @Test
    fun `days before counts back from the due day, across the end of a month, 0 being the due day itself`() {
        val due = Due.On(day("2026-11-01"))

        assertEquals(instant("2026-10-30T17:00:00Z"), WakeTime.Before(2, time("18:00")).moment(due, BERLIN))
        assertEquals(instant("2026-11-01T17:00:00Z"), WakeTime.Before(0, time("18:00")).moment(due, BERLIN))
    }

    @Test
    fun `days before keeps the time of the wall clock across a change to summer time`() {
        // Summer time begins on Sunday, 28 March 2027, in Berlin.
        val due = Due.At(day("2027-03-29"), time("09:00"))

        assertEquals(instant("2027-03-27T17:00:00Z"), WakeTime.Before(2, time("18:00")).moment(due, BERLIN))
        assertEquals(instant("2027-03-28T16:00:00Z"), WakeTime.Before(1, time("18:00")).moment(due, BERLIN))
    }

    @Test
    fun `a time skipped as the clocks spring forward wakes right after the jump`() {
        val due = Due.On(day("2027-03-29"))

        assertEquals(instant("2027-03-28T01:30:00Z"), WakeTime.Before(1, time("02:30")).moment(due, BERLIN))
    }

    @Test
    fun `it wakes on the wall clock of the time zone the phone is in`() {
        val due = Due.On(day("2026-11-14"))

        assertEquals(instant("2026-11-13T17:00:00Z"), WakeTime.Before(1, time("18:00")).moment(due, BERLIN))
        assertEquals(instant("2026-11-13T23:00:00Z"), WakeTime.Before(1, time("18:00")).moment(due, ZoneId.of("America/New_York")))
    }

    @Test
    fun `days before has no moment without a due day, a fixed moment has one also without`() {
        assertEquals(null, WakeTime.Before(1, time("18:00")).moment(Due.None, BERLIN))
        assertEquals(instant("2026-11-13T17:00:00Z"), WakeTime.At(day("2026-11-13"), time("18:00")).moment(Due.None, BERLIN))
        assertEquals(
            instant("2026-11-13T17:00:00Z"),
            WakeTime.At(day("2026-11-13"), time("18:00")).moment(Due.On(day("2026-12-24")), BERLIN),
        )
    }

    @Test
    fun `a to-do stores how it wakes and reads it back`() {
        val before = Todo(title = "Müll", description = "", dueDate = day("2026-11-14")).withWake(WakeTime.Before(1, time("18:00")))
        val at = Todo(title = "Müll", description = "").withWake(WakeTime.At(day("2026-11-13"), time("18:00")))

        assertEquals(WakeTime.Before(1, time("18:00")), before.wake)
        assertEquals(WakeTime.At(day("2026-11-13"), time("18:00")), at.wake)
        assertEquals(null, at.withWake(null).wake)
    }

    private fun day(text: String) = LocalDate.parse(text)

    private fun time(text: String) = LocalTime.parse(text)

    private fun instant(text: String) = Instant.parse(text)

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
