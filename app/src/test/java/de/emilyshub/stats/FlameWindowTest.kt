package de.emilyshub.stats

import de.emilyshub.routines.OccurrenceOutcome
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Missed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The flame read from only as far back as it needs (#108): from the latest day that breaks the overall streak, in
 * windows growing back from today, and the same flame as from the whole history.
 */
class FlameWindowTest {

    private val today = LocalDate.parse("2026-10-23")
    private val reads = mutableListOf<LocalDate>()

    @Test
    fun `a break within the first window, with a day done after it, is read in one go`() {
        val history = daily(from = today.minusDays(400), to = today.minusDays(1), broken = today.minusDays(10))

        val flame = flameFrom(history)

        assertEquals(listOf(today.minusDays(32)), reads)
        assertEquals(whole(history), flame)
        assertEquals(9, flame?.days)
    }

    @Test
    fun `on the window's first day the break is within it, a day before it the next window is read`() {
        val onFirstDay = daily(today.minusDays(400), today.minusDays(1), broken = today.minusDays(32))
        assertEquals(whole(onFirstDay), flameFrom(onFirstDay))
        assertEquals(listOf(today.minusDays(32)), reads)

        reads.clear()
        val dayBefore = daily(today.minusDays(400), today.minusDays(1), broken = today.minusDays(33))
        assertEquals(whole(dayBefore), flameFrom(dayBefore))
        assertEquals(listOf(today.minusDays(32), today.minusDays(64)), reads)
    }

    @Test
    fun `without a streak it reads back to the break before the last streak`() {
        // The last streak, the 20 days from 200 to 181 days ago, after a break 201 days ago; yesterday broke it.
        val history = daily(today.minusDays(200), today.minusDays(181)) +
            outcome(today.minusDays(201), Missed) + outcome(today.minusDays(1), Missed)

        val flame = flameFrom(history)

        assertEquals(whole(history), flame)
        assertEquals(0, flame?.days)
        assertEquals(LastStreak(days = 20, until = today.minusDays(181)), flame?.last)
        assertEquals(listOf(32L, 64, 128, 256).map(today::minusDays), reads)
    }

    @Test
    fun `without a break it reads back to the first day, and no further`() {
        val history = daily(today.minusDays(1000), today.minusDays(1))

        val flame = flameFrom(history)

        assertEquals(whole(history), flame)
        assertEquals(1000, flame?.days)
        assertEquals(listOf(32L, 64, 128, 256, 512, 1024).map(today::minusDays), reads)
    }

    @Test
    fun `with nothing ever there is no flame, read once`() {
        assertNull(flameFrom(emptyList()))
        assertEquals(1, reads.size)
    }

    /** The flame [flameOf] gives over [history], noting each day it reads from. */
    private fun flameFrom(history: List<OccurrenceOutcome>): StreakFlame? = runBlocking {
        flameOf(today, first = history.minOfOrNull { it.date }) { from ->
            reads += from
            OverallHistory(history.filter { !it.date.isBefore(from) }, emptyList(), StreakRule.DEFAULT, today)
        }
    }

    private fun whole(history: List<OccurrenceOutcome>) = OverallHistory(history, emptyList(), StreakRule.DEFAULT, today).flame()

    /** Each day from [from] to [to] done, but the day [broken], missed. */
    private fun daily(from: LocalDate, to: LocalDate, broken: LocalDate? = null) =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .map { outcome(it, if (it == broken) Missed else Done) }.toList()

    private fun outcome(date: LocalDate, status: OccurrenceStatus) = OccurrenceOutcome(routineId = 1, date = date, status = status)
}
