package de.emilyshub.stats

import de.emilyshub.routines.OccurrenceOutcome
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.routines.OccurrenceStatus.Open
import de.emilyshub.routines.OccurrenceStatus.Skipped
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The statistics of routines (SPEC.md §8): streaks and the done rate, counting only the days they were due. */
class RoutineHistoryTest {

    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `a day is missed once anything was missed, else done once anything was done, else open while anything is, else skipped`() {
        assertEquals(DayOutcome.Missed, DayOutcome.of(listOf(Done, Missed)))
        assertEquals(DayOutcome.Done, DayOutcome.of(listOf(Done, Skipped)))
        assertEquals(DayOutcome.Done, DayOutcome.of(listOf(Done, Open)))
        assertEquals(DayOutcome.Open, DayOutcome.of(listOf(Skipped, Open)))
        assertEquals(DayOutcome.Skipped, DayOutcome.of(listOf(Skipped, Skipped)))
    }

    @Test
    fun `the streak counts the days done in a row, and a missed day breaks it`() {
        val history = history(
            "2026-10-16" to Done, "2026-10-17" to Done, "2026-10-18" to Done,
            "2026-10-19" to Missed,
            "2026-10-20" to Done, "2026-10-21" to Done,
        )

        assertEquals(Streaks(current = 2, longest = 3), history.streaks())
    }

    @Test
    fun `skipped days, days not due and paused days neither break the streak nor count towards it`() {
        val history = history(
            "2026-10-16" to Done, "2026-10-17" to Skipped,
            // Not due, or paused, on the 18th and 19th: no occurrences.
            "2026-10-20" to Done, "2026-10-21" to Skipped, "2026-10-22" to Done,
        )

        assertEquals(Streaks(current = 3, longest = 3), history.streaks())
    }

    @Test
    fun `today, still open, does not break the streak, done it counts, and missed it ends it`() {
        val before = arrayOf("2026-10-21" to Done, "2026-10-22" to Done)

        assertEquals(Streaks(current = 2, longest = 2), history(*before, "2026-10-23" to Open).streaks())
        assertEquals(Streaks(current = 3, longest = 3), history(*before, "2026-10-23" to Done).streaks())
        assertEquals(Streaks(current = 0, longest = 2), history(*before, "2026-10-23" to Missed).streaks())
    }

    @Test
    fun `with several times a day, a day counts once, and only if none of its times was missed`() {
        val history = history(
            "2026-10-21" to Done, "2026-10-21" to Done,
            "2026-10-22" to Done, "2026-10-22" to Skipped,
            "2026-10-23" to Done, "2026-10-23" to Missed,
        )

        assertEquals(Streaks(current = 0, longest = 2), history.streaks())
    }

    @Test
    fun `without anything done, there is no streak`() {
        assertEquals(Streaks(current = 0, longest = 0), history().streaks())
        assertEquals(Streaks(current = 0, longest = 0), history("2026-10-22" to Skipped, "2026-10-23" to Open).streaks())
    }

    @Test
    fun `the done rate is done among done and missed, skipped and open ones left out, in the days up to today`() {
        val history = history(
            "2026-10-10" to Missed,
            "2026-10-17" to Done, "2026-10-18" to Missed,
            "2026-10-20" to Done, "2026-10-21" to Skipped, "2026-10-22" to Done, "2026-10-23" to Open,
        )

        assertEquals(DoneRate(done = 3, counted = 4), history.doneRate(today, period = 7))
        assertEquals(75, history.doneRate(today, period = 7).percent)
        assertEquals(DoneRate(done = 3, counted = 5), history.doneRate(today, period = 30))
    }

    @Test
    fun `with nothing done or missed in the days, there is no done rate`() {
        val history = history("2026-10-22" to Skipped, "2026-10-23" to Open)

        assertEquals(null, history.doneRate(today, period = 7).percent)
    }

    @Test
    fun `a done rate is rounded to whole percent, but is 100 only with nothing missed and 0 only with nothing done`() {
        assertEquals(67, DoneRate(done = 2, counted = 3).percent)
        assertEquals(33, DoneRate(done = 1, counted = 3).percent)
        assertEquals(100, DoneRate(done = 4, counted = 4).percent)
        assertEquals(99, DoneRate(done = 269, counted = 270).percent)
        assertEquals(1, DoneRate(done = 1, counted = 270).percent)
        assertEquals(0, DoneRate(done = 0, counted = 3).percent)
    }

    @Test
    fun `over all routines, a day counts once something is done and nothing missed, and the rate counts all of them`() {
        val history = RoutineHistory(
            listOf(
                outcome(routine = 1, "2026-10-20", Done), outcome(routine = 2, "2026-10-20", Skipped),
                outcome(routine = 1, "2026-10-21", Done), outcome(routine = 2, "2026-10-21", Missed),
                outcome(routine = 1, "2026-10-22", Done), outcome(routine = 2, "2026-10-22", Done),
                outcome(routine = 2, "2026-10-23", Done),
            ),
        )

        assertEquals(Streaks(current = 2, longest = 2), history.streaks())
        assertEquals(DoneRate(done = 5, counted = 6), history.doneRate(today, period = 7))
    }

    @Test
    fun `the days of the calendar say how each due day ended, the days without an occurrence left out`() {
        val history = history("2026-10-20" to Done, "2026-10-20" to Missed, "2026-10-22" to Skipped, "2026-10-23" to Open)

        assertEquals(
            mapOf(
                LocalDate.parse("2026-10-20") to DayOutcome.Missed,
                LocalDate.parse("2026-10-22") to DayOutcome.Skipped,
                LocalDate.parse("2026-10-23") to DayOutcome.Open,
            ),
            history.days,
        )
    }

    /** The occurrences of a routine on the days given, as they ended. */
    private fun history(vararg days: Pair<String, OccurrenceStatus>) =
        RoutineHistory(days.map { (date, status) -> outcome(routine = 1, date, status) })

    private fun outcome(routine: Long, date: String, status: OccurrenceStatus) =
        OccurrenceOutcome(routine, LocalDate.parse(date), status)
}
