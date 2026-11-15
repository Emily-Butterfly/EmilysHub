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

/**
 * The overall streak (SPEC.md §8; #109) and the flame atop Heute that shows it (§1): a day counts from a number of
 * things done, routines and to-dos together, or from a share; only what was missed or dropped counts against it.
 */
class StreakFlameTest {

    private val today = LocalDate.parse("2026-10-23")
    private val yesterday = "2026-10-22"

    @Test
    fun `from the first thing done, by default, a day with something missed still counts`() {
        val history = overall(StreakRule.DEFAULT, "2026-10-21" to Done, yesterday to Done, yesterday to Missed)

        assertEquals(Streaks(current = 2, longest = 2), history.streaks())
    }

    @Test
    fun `from a number, reached exactly a day counts, one short it breaks the streak`() {
        val three = StreakRule.Count(3)
        assertEquals(1, overall(three, yesterday to Done, yesterday to Done, yesterday to Done).streaks().current)
        val short = overall(three, "2026-10-21" to Done, "2026-10-21" to Done, "2026-10-21" to Done, yesterday to Done, yesterday to Done)
        assertEquals(Streaks(current = 0, longest = 1), short.streaks())
        assertEquals("nothing missed, but too little", DayOutcome.Short, short.days[LocalDate.parse(yesterday)])
    }

    @Test
    fun `a day with only something missed breaks the streak from the first thing done`() {
        assertEquals(0, overall(StreakRule.DEFAULT, "2026-10-21" to Done, yesterday to Missed).streaks().current)
        assertEquals(0, overall(StreakRule.DEFAULT, "2026-10-21" to Done, yesterday to Dropped).streaks().current)
    }

    @Test
    fun `a to-do ticked off alone makes a day count, and never counts against one`() {
        val history = OverallHistory(
            routines = listOf(outcome("2026-10-21", Done)),
            todosDone = listOf(LocalDate.parse(yesterday)),
            rule = StreakRule.DEFAULT,
            today = today,
        )

        assertEquals(2, history.streaks().current)
    }

    @Test
    fun `from a share, to-dos count among what was done, and the share reached exactly counts, just missed breaks`() {
        val half = StreakRule.Share(50)
        val reached = OverallHistory(listOf(outcome(yesterday, Missed), outcome(yesterday, Missed)), List(2) { LocalDate.parse(yesterday) }, half, today)
        assertEquals("2 of 4", 1, reached.streaks().current)
        val missed = OverallHistory(
            listOf(outcome(yesterday, Missed), outcome(yesterday, Missed), outcome(yesterday, Dropped)),
            List(2) { LocalDate.parse(yesterday) }, half, today,
        )
        assertEquals("2 of 5", 0, missed.streaks().current)
        assertEquals(DayOutcome.Missed, missed.days[LocalDate.parse(yesterday)])
    }

    @Test
    fun `a share needs something done, and at 100 percent a day counts only with nothing missed or dropped`() {
        val all = StreakRule.Share(100)
        assertEquals(1, overall(all, yesterday to Done, yesterday to Skipped).streaks().current)
        assertEquals(0, overall(all, "2026-10-21" to Done, yesterday to Done, yesterday to Dropped).streaks().current)
        assertEquals("nothing done", DayOutcome.Missed, overall(StreakRule.Share(10), yesterday to Missed).days[LocalDate.parse(yesterday)])
    }

    @Test
    fun `what was skipped, or is still open, counts neither for nor against a day`() {
        val history = overall(StreakRule.Share(100), "2026-10-21" to Done, yesterday to Skipped, yesterday to Skipped)

        assertEquals(1, history.streaks().current)
        assertEquals(DayOutcome.Skipped, history.days[LocalDate.parse(yesterday)])
        assertEquals(1, overall(StreakRule.Share(100), yesterday to Done, yesterday to Skipped).streaks().current)
    }

    @Test
    fun `a day with nothing at all neither counts nor breaks the streak`() {
        assertEquals(2, overall(StreakRule.DEFAULT, "2026-10-20" to Done, "2026-10-21" to Skipped, yesterday to Done).streaks().current)
    }

    @Test
    fun `today counts once it keeps the rule, and does not break the streak before midnight`() {
        val before = arrayOf("2026-10-21" to Done, yesterday to Done)

        assertEquals(StreakFlame(days = 2, todayOpen = true), overall(StreakRule.DEFAULT, *before, "2026-10-23" to Open).flame())
        assertEquals(StreakFlame(days = 2, todayOpen = true), overall(StreakRule.DEFAULT, *before, "2026-10-23" to Missed).flame())
        assertEquals(StreakFlame(days = 2, todayOpen = true), overall(StreakRule.DEFAULT, *before).flame())
        assertEquals(StreakFlame(days = 3, todayOpen = false), overall(StreakRule.DEFAULT, *before, "2026-10-23" to Done).flame())
        // Two each day before; today one short of two, so far.
        assertEquals(StreakFlame(days = 2, todayOpen = true), overall(StreakRule.Count(2), *before, *before, "2026-10-23" to Done).flame())
    }

    @Test
    fun `with yesterday broken, the flame stands at 0, grey, naming the last streak, until today counts`() {
        val broken = arrayOf("2026-10-21" to Done, yesterday to Missed)
        val last = LastStreak(days = 1, until = LocalDate.parse("2026-10-21"))

        assertEquals(StreakFlame(days = 0, todayOpen = true, last = last), overall(StreakRule.DEFAULT, *broken, "2026-10-23" to Open).flame())
        assertEquals(StreakFlame(days = 1, todayOpen = false), overall(StreakRule.DEFAULT, *broken, "2026-10-23" to Done).flame())
    }

    @Test
    fun `there is a flame once anything ever counted for or against a day, to-dos alone too`() {
        assertNull(overall(StreakRule.DEFAULT).flame())
        assertNull(overall(StreakRule.DEFAULT, yesterday to Skipped, "2026-10-23" to Open).flame())
        assertEquals(StreakFlame(days = 0, todayOpen = true), overall(StreakRule.DEFAULT, yesterday to Missed).flame())
        val todos = OverallHistory(emptyList(), listOf(LocalDate.parse(yesterday)), StreakRule.DEFAULT, today)
        assertEquals(StreakFlame(days = 1, todayOpen = true), todos.flame())
    }

    @Test
    fun `the rule changed, the whole history is reckoned anew, and a routine's own streak stays as it was`() {
        val days = listOf(outcome("2026-10-21", Done), outcome("2026-10-21", Missed), outcome(yesterday, Done))

        assertEquals(2, OverallHistory(days, emptyList(), StreakRule.DEFAULT, today).streaks().current)
        assertEquals(1, OverallHistory(days, emptyList(), StreakRule.Share(100), today).streaks().current)
        assertEquals(1, RoutineHistory(days).streaks().current)
    }

    @Test
    fun `the flame is lit only with a streak and today counting`() {
        assertTrue(StreakFlame(days = 2, todayOpen = false).lit)
        assertFalse(StreakFlame(days = 2, todayOpen = true).lit)
        assertFalse(StreakFlame(days = 0, todayOpen = false).lit)
    }

    @Test
    fun `the rule is kept as text, anything unknown or out of range as the default`() {
        listOf(StreakRule.Count(1), StreakRule.Count(30), StreakRule.Share(10), StreakRule.Share(100)).forEach {
            assertEquals(it, StreakRule.of(it.stored))
        }
        listOf(null, "", "count:0", "count:31", "share:55", "share:0", "share:110", "nonsense").forEach {
            assertEquals(it.toString(), StreakRule.DEFAULT, StreakRule.of(it))
        }
    }

    private fun outcome(date: String, status: OccurrenceStatus) = OccurrenceOutcome(1, LocalDate.parse(date), status)

    private fun overall(rule: StreakRule, vararg days: Pair<String, OccurrenceStatus>) =
        OverallHistory(days.map { (date, status) -> outcome(date, status) }, emptyList(), rule, today)
}
