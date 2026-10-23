package de.emilyshub.routines

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DueTimeTest {

    @Test
    fun `the day changes at local midnight, not at midnight UTC`() {
        val halfPastMidnightInBerlin = Instant.parse("2026-10-22T22:30:00Z")

        assertEquals(LocalDate.parse("2026-10-23"), today(halfPastMidnightInBerlin, BERLIN))
    }

    @Test
    fun `a routine is due at its time on the local clock, also after flying to another time zone`() {
        val date = LocalDate.parse("2026-10-23")
        val eight = LocalTime.of(8, 0)

        assertEquals(Instant.parse("2026-10-23T06:00:00Z"), dueAt(date, eight, BERLIN).toInstant())
        assertEquals(Instant.parse("2026-10-23T12:00:00Z"), dueAt(date, eight, NEW_YORK).toInstant())
    }

    @Test
    fun `a daily routine stays at its time on the local clock across the change to winter time`() {
        val eight = LocalTime.of(8, 0)
        val saturday = dueAt(LocalDate.parse("2026-10-24"), eight, BERLIN)
        val sunday = dueAt(LocalDate.parse("2026-10-25"), eight, BERLIN)
        val monday = dueAt(LocalDate.parse("2026-10-26"), eight, BERLIN)

        assertEquals(listOf(eight, eight, eight), listOf(saturday, sunday, monday).map { it.toLocalTime() })
        // The night the clocks go back has 25 hours.
        assertEquals(Duration.ofHours(25), Duration.between(saturday, sunday))
        assertEquals(Duration.ofHours(24), Duration.between(sunday, monday))
    }

    @Test
    fun `a time in the hour skipped by the change to summer time is due right after the jump`() {
        val due = dueAt(LocalDate.parse("2027-03-28"), LocalTime.of(2, 30), BERLIN)

        assertEquals(LocalTime.of(3, 30), due.toLocalTime())
        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), due.toInstant())
    }

    @Test
    fun `a time in the hour repeated by the change to winter time is due the first time round`() {
        val due = dueAt(LocalDate.parse("2026-10-25"), LocalTime.of(2, 30), BERLIN)

        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), due.toInstant())
    }

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
    }
}
