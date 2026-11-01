package de.emilyshub.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** The heatmap under Heute (SPEC.md §1): what was done each day of the last 26 weeks, in four levels. */
class HeatmapTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.parse("2026-10-23") // a Friday

    @Test
    fun `26 weeks from Monday to Sunday, the current week last, the days after today in the future`() {
        val heatmap = heatmap()

        assertEquals(26, heatmap.weeks.size)
        assertTrue(heatmap.weeks.all { week -> week.map { it.date.dayOfWeek } == DayOfWeek.entries })
        assertEquals(LocalDate.parse("2026-10-19"), heatmap.weeks.last().first().date)
        assertEquals(LocalDate.parse("2026-04-27"), heatmap.weeks.first().first().date)
        val thisWeek = heatmap.weeks.last()
        assertEquals(listOf(false, false, false, false, false, true, true), thisWeek.map { it.future })
    }

    @Test
    fun `a day counts the routines done that day and the to-dos ticked off on it`() {
        val heatmap = heatmap(
            routinesDone = listOf("2026-10-21", "2026-10-21", "2026-10-22"),
            todosDone = listOf("2026-10-21T08:00:00Z"),
        )

        assertEquals(3, heatmap.day("2026-10-21").done)
        assertEquals(1, heatmap.day("2026-10-22").done)
        assertEquals(0, heatmap.day("2026-10-20").done)
    }

    @Test
    fun `a to-do counts on the local day it was ticked off, around midnight and in the time zone given`() {
        // 23:30 and 00:30 in Berlin, in summer time on the 21st to 22nd of October.
        val todos = listOf("2026-10-21T21:30:00Z", "2026-10-21T22:30:00Z")

        val inBerlin = heatmap(todosDone = todos)
        assertEquals(1, inBerlin.day("2026-10-21").done)
        assertEquals(1, inBerlin.day("2026-10-22").done)

        val inNewYork = heatmap(todosDone = todos, zone = ZoneId.of("America/New_York"))
        assertEquals(2, inNewYork.day("2026-10-21").done)
    }

    @Test
    fun `what was done before the 26 weeks or after today does not count`() {
        val heatmap = heatmap(routinesDone = listOf("2026-04-26", "2026-10-24", "2026-10-23"))

        assertEquals(1, heatmap.weeks.flatten().sumOf { it.done })
        assertEquals(1, heatmap.max)
    }

    @Test
    fun `the levels go from empty to four, measured against the fullest day`() {
        val heatmap = heatmap(
            routinesDone = List(8) { "2026-10-22" } + List(4) { "2026-10-21" } + List(1) { "2026-10-20" } +
                List(6) { "2026-10-19" } + List(2) { "2026-10-18" },
        )

        assertEquals(8, heatmap.max)
        assertEquals(4, heatmap.day("2026-10-22").level)
        assertEquals(3, heatmap.day("2026-10-19").level)
        assertEquals(2, heatmap.day("2026-10-21").level)
        assertEquals(1, heatmap.day("2026-10-18").level)
        assertEquals(1, heatmap.day("2026-10-20").level)
        assertEquals(0, heatmap.day("2026-10-17").level)
    }

    @Test
    fun `with nothing done, every day is empty`() {
        val heatmap = heatmap()

        assertEquals(0, heatmap.max)
        assertTrue(heatmap.weeks.flatten().all { it.level == 0 })
    }

    @Test
    fun `a month is named over the week its first day falls in`() {
        val months = heatmap().months

        assertEquals(YearMonth.of(2026, 5), months.first().month)
        assertEquals(0, months.first().week) // 1 May 2026 is the Friday of the first week
        assertEquals(YearMonth.of(2026, 10), months.last().month)
        assertEquals(22, months.last().week) // 1 October 2026 is a Thursday, in the week from 28 September
        assertEquals(6, months.size)
    }

    private fun heatmap(
        routinesDone: List<String> = emptyList(),
        todosDone: List<String> = emptyList(),
        zone: ZoneId = berlin,
    ) = Heatmap.of(today, routinesDone.map(LocalDate::parse), todosDone.map(Instant::parse), zone)

    private fun Heatmap.day(date: String) = weeks.flatten().single { it.date == LocalDate.parse(date) }
}
