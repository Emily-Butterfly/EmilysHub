package de.emilyshub.wishes

import de.emilyshub.routines.OccurrenceOutcome
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.routines.OccurrenceStatus.Skipped
import de.emilyshub.routines.RoutineTarget
import de.emilyshub.stats.RoutineHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Routines hanging under goals (SPEC.md §2, §6): with a target, so many days done in a row or done so many times, they
 * count towards the goal by how far they have come, and as done once they reached it; without one they only show.
 */
class GoalRoutineTest {

    @Test
    fun `aiming at a streak, a routine has come as far as the days done in a row up to now`() {
        val vocabulary = routine(RoutineTarget.Streak(10), Done, Done, Missed, Done, Skipped, Done, Done)

        assertEquals(3, vocabulary.count)
        assertEquals(0.3, checkNotNull(vocabulary.progress), 1e-9)
        assertFalse(vocabulary.reached)
    }

    @Test
    fun `a streak reached once stays reached, though broken since`() {
        val days = List(10) { Done } + Missed + Done
        val vocabulary = routine(RoutineTarget.Streak(10), *days.toTypedArray())

        assertTrue(vocabulary.reached)
        assertEquals(1.0, checkNotNull(vocabulary.progress), 0.0)
        assertEquals(1, vocabulary.count)
    }

    @Test
    fun `aiming at times done, a routine has come as far as the times it was done, skipped and missed ones not counting`() {
        val yoga = routine(RoutineTarget.TimesDone(4), Done, Skipped, Missed, Done, Done)

        assertEquals(3, yoga.count)
        assertEquals(0.75, checkNotNull(yoga.progress), 1e-9)
        assertFalse(yoga.reached)

        val more = routine(RoutineTarget.TimesDone(4), Done, Done, Done, Done, Done)
        assertTrue(more.reached)
        assertEquals("All of it, not more", 1.0, checkNotNull(more.progress), 0.0)
    }

    @Test
    fun `without a target a routine counts towards nothing`() {
        val walk = routine(target = null, Done, Done)

        assertNull(walk.progress)
        assertNull(walk.count)
        assertFalse(walk.reached)
    }

    @Test
    fun `a goal's progress counts its routines with a target with its other parts, those without only show`() {
        // As in SPEC.md §6: the vocabulary by a routine of 20 minutes a day, aiming at 10 days in a row, 6 of them done.
        val goal = GoalTree.build(
            listOf(WishWithTags(Wish(1, "Wortschatz 1000 Wörter", "", "", WishStatus.Open), emptyList())),
            listOf(GoalTodo(10, "Anki einrichten", goalId = 1, done = true)),
            listOf(
                GoalRoutine(20, "Täglich 20 min Vokabeln", goalId = 1, RoutineTarget.Streak(10), history(*Array(6) { Done })),
                GoalRoutine(21, "Podcast hören", goalId = 1, target = null, history(Done)),
            ),
        ).single()

        assertEquals(80, goal.percent)
        assertEquals(listOf("Podcast hören", "Täglich 20 min Vokabeln"), goal.routines.map { it.name })
    }

    @Test
    fun `a goal with routines without a target only has nothing that counts towards its progress`() {
        val goal = GoalTree.build(
            listOf(WishWithTags(Wish(1, "Fit bleiben", "", "", WishStatus.Open), emptyList())),
            emptyList(),
            listOf(GoalRoutine(20, "Spazieren", goalId = 1, target = null, history(Done))),
        ).single()

        assertTrue(goal.hasParts)
        assertFalse(goal.hasCountingParts)
        assertEquals(0, goal.percent)
    }

    private fun routine(target: RoutineTarget?, vararg days: OccurrenceStatus) = GoalRoutine(1, "Routine", 1, target, history(*days))

    /** One occurrence a day, from 1 October 2026 on. */
    private fun history(vararg days: OccurrenceStatus) =
        RoutineHistory(days.mapIndexed { index, status -> OccurrenceOutcome(1, START.plusDays(index.toLong()), status) })

    private companion object {
        val START: LocalDate = LocalDate.parse("2026-10-01")
    }
}
