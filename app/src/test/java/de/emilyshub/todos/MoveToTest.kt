package de.emilyshub.todos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Where a to-do moves to from Heute or the To-dos tab (SPEC.md §1, Menü je Eintrag; #97), from today on. */
class MoveToTest {

    /** Monday, 19 October 2026, to Sunday, the 25th. */
    private val week = (19..25).map { LocalDate.of(2026, 10, it) }

    @Test
    fun `tomorrow is the day after today`() {
        week.forEach { today -> assertEquals(today.toString(), today.plusDays(1), MoveTo.Tomorrow.from(today)) }
    }

    @Test
    fun `the weekend is the coming Saturday, and on Saturday and Sunday the one after`() {
        val saturday = LocalDate.of(2026, 10, 24)
        val nextSaturday = LocalDate.of(2026, 10, 31)

        assertEquals(
            listOf(saturday, saturday, saturday, saturday, saturday, nextSaturday, nextSaturday),
            week.map { MoveTo.Weekend.from(it) },
        )
    }

    @Test
    fun `next week is the coming Monday, on a Monday the one after`() {
        val nextMonday = LocalDate.of(2026, 10, 26)

        assertEquals(List(7) { nextMonday }, week.map { MoveTo.NextWeek.from(it) })
    }
}
