package de.emilyshub.stats

import de.emilyshub.routines.OccurrenceOutcome
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Dropped
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.routines.OccurrenceStatus.Open
import de.emilyshub.routines.OccurrenceStatus.Skipped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The flame atop Heute (SPEC.md §1): the overall streak of the statistics, grey while today is still open. */
class StreakFlameTest {

    private val today = LocalDate.parse("2026-10-23")
    private val before = arrayOf("2026-10-21" to Done, "2026-10-22" to Done)

    @Test
    fun `while today is still open, the flame shows the streak so far, grey`() {
        assertEquals(StreakFlame(days = 2, todayOpen = true), flame(*before, "2026-10-23" to Open))
    }

    @Test
    fun `once something is done today, the flame counts today too and is lit`() {
        assertEquals(StreakFlame(days = 3, todayOpen = false), flame(*before, "2026-10-23" to Done, "2026-10-23" to Open))
    }

    @Test
    fun `with all of today skipped, the flame is lit and today neither counts nor breaks it`() {
        assertEquals(StreakFlame(days = 2, todayOpen = false), flame(*before, "2026-10-23" to Skipped, "2026-10-23" to Skipped))
    }

    @Test
    fun `with nothing due today, the flame is lit`() {
        assertEquals(StreakFlame(days = 2, todayOpen = false), flame(*before))
    }

    @Test
    fun `with yesterday missed, the flame stands at 0, grey, naming the last streak, until today is done`() {
        val missedYesterday = arrayOf("2026-10-21" to Done, "2026-10-22" to Missed)
        val last = LastStreak(days = 1, until = LocalDate.parse("2026-10-21"))

        assertEquals(StreakFlame(days = 0, todayOpen = true, last = last), flame(*missedYesterday, "2026-10-23" to Open))
        assertEquals(StreakFlame(days = 1, todayOpen = false), flame(*missedYesterday, "2026-10-23" to Done))
    }

    @Test
    fun `missed or dropped today, the streak is over and the flame stands at 0, grey`() {
        val last = LastStreak(days = 2, until = LocalDate.parse("2026-10-22"))
        assertEquals(StreakFlame(days = 0, todayOpen = false, last = last), flame(*before, "2026-10-23" to Done, "2026-10-23" to Missed))
        assertEquals(StreakFlame(days = 0, todayOpen = false, last = last), flame(*before, "2026-10-23" to Dropped))
        assertFalse(flame(*before, "2026-10-23" to Dropped)!!.lit)
    }

    @Test
    fun `without anything ever due, there is no flame`() {
        assertNull(flame())
    }

    @Test
    fun `with something due but never a streak, the flame stands at 0 without a last one`() {
        assertEquals(StreakFlame(days = 0, todayOpen = true), flame("2026-10-22" to Skipped, "2026-10-23" to Open))
        assertEquals(StreakFlame(days = 0, todayOpen = false), flame("2026-10-22" to Missed))
    }

    @Test
    fun `the flame is lit only with a streak and today no longer open`() {
        assertTrue(StreakFlame(days = 2, todayOpen = false).lit)
        assertFalse(StreakFlame(days = 2, todayOpen = true).lit)
        assertFalse(StreakFlame(days = 0, todayOpen = false).lit)
    }

    @Test
    fun `the flame shows the same number as the statistics, over all routines`() {
        val history = RoutineHistory(
            listOf(
                OccurrenceOutcome(1, LocalDate.parse("2026-10-21"), Done),
                OccurrenceOutcome(2, LocalDate.parse("2026-10-22"), Done),
                OccurrenceOutcome(1, LocalDate.parse("2026-10-23"), Open),
                OccurrenceOutcome(2, LocalDate.parse("2026-10-23"), Skipped),
            ),
        )

        assertEquals(StreakFlame(days = history.streaks().current, todayOpen = true), history.flame(today))
    }

    private fun flame(vararg days: Pair<String, OccurrenceStatus>) =
        RoutineHistory(days.map { (date, status) -> OccurrenceOutcome(1, LocalDate.parse(date), status) }).flame(today)
}
