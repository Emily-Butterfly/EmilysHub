package de.emilyshub.stats

import de.emilyshub.routines.OccurrenceOutcome
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Dropped
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.routines.OccurrenceStatus.Open
import de.emilyshub.routines.OccurrenceStatus.Skipped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `with yesterday missed, there is no flame until today is done`() {
        val missedYesterday = arrayOf("2026-10-21" to Done, "2026-10-22" to Missed)

        assertNull(flame(*missedYesterday, "2026-10-23" to Open))
        assertEquals(StreakFlame(days = 1, todayOpen = false), flame(*missedYesterday, "2026-10-23" to Done))
    }

    @Test
    fun `missed or dropped today, the streak is over and there is no flame`() {
        assertNull(flame(*before, "2026-10-23" to Done, "2026-10-23" to Missed))
        assertNull(flame(*before, "2026-10-23" to Dropped))
    }

    @Test
    fun `without a streak, there is no flame`() {
        assertNull(flame())
        assertNull(flame("2026-10-22" to Skipped, "2026-10-23" to Open))
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
