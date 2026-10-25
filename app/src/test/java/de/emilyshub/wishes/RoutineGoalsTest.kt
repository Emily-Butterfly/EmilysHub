package de.emilyshub.wishes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.DraftProblem
import de.emilyshub.routines.Occurrence
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.RoutineTarget
import de.emilyshub.routines.Routines
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.Todos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Routines under goals (SPEC.md §2, §6): with a target they count towards their goal and finish it once reached, as its
 * last part open; without one they only show with it.
 */
@RunWith(AndroidJUnit4::class)
class RoutineGoalsTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a routine hangs under a goal with what it aims at, and shows with it`() = runBlocking {
        val vocabulary = wishes.create(WishDraft("Wortschatz 1000 Wörter"))
        routines.create(routine("Vokabeln", goal = vocabulary, target = RoutineTarget.Streak(30)))

        val shown = checkNotNull(wishes.goal(vocabulary).first()).routines.single()

        assertEquals("Vokabeln" to RoutineTarget.Streak(30), shown.name to shown.target)
        assertEquals(0.0, checkNotNull(shown.progress), 0.0)
    }

    @Test
    fun `reaching its target by the day done today finishes the goal, its last part open, and undoing that opens it again`() =
        runBlocking {
            val vocabulary = wishes.create(WishDraft("Wortschatz"))
            val routine = routines.create(routine("Vokabeln", goal = vocabulary, target = RoutineTarget.Streak(3)))
            done(routine, "2026-10-21", "2026-10-22")
            val todayOccurrence = routines.dueOn(today).first().single().occurrenceId

            routines.markDone(todayOccurrence)
            assertEquals(WishStatus.Done to today, wishes.get(vocabulary)?.let { it.status to it.doneOn })

            routines.reopen(todayOccurrence)
            assertEquals(WishStatus.InProgress, wishes.get(vocabulary)?.status)
        }

    @Test
    fun `a routine without a target holds no goal open`() = runBlocking {
        val fit = wishes.create(WishDraft("Fit bleiben"))
        routines.create(routine("Spazieren", goal = fit))
        val shoes = todos.create(TodoDraft("Laufschuhe kaufen", goalId = fit))

        todos.markDone(shoes)

        assertEquals(WishStatus.Done, wishes.get(fit)?.status)
    }

    @Test
    fun `times done count towards the goal, and a target set that is reached already finishes it`() = runBlocking {
        val yoga = wishes.create(WishDraft("Yoga-Routine"))
        val routine = routines.create(routine("Yoga", goal = yoga))
        done(routine, "2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22")

        routines.update(routine, routine("Yoga", goal = yoga, target = RoutineTarget.TimesDone(8)))
        assertEquals(0.5, checkNotNull(wishes.goal(yoga).first()?.routines?.single()?.progress), 1e-9)
        assertEquals(WishStatus.Open, wishes.get(yoga)?.status)

        routines.update(routine, routine("Yoga", goal = yoga, target = RoutineTarget.TimesDone(4)))
        assertEquals(WishStatus.Done, wishes.get(yoga)?.status)
    }

    @Test
    fun `deleting a routine whose target is not reached, the last part open, finishes its goal`() = runBlocking {
        val move = wishes.create(WishDraft("Umzug"))
        todos.markDone(todos.create(TodoDraft("Kisten packen", goalId = move)))
        val routine = routines.create(routine("Ausmisten", goal = move, target = RoutineTarget.TimesDone(10)))
        assertEquals("Done by its to-do, opened again by the routine", WishStatus.InProgress, wishes.get(move)?.status)

        routines.delete(routine)

        assertEquals(WishStatus.Done, wishes.get(move)?.status)
    }

    @Test
    fun `a goal deleted leaves its routines at no goal, and its parts moved up take them along`() = runBlocking {
        val arabic = wishes.create(WishDraft("Arabisch A2"))
        val vocabulary = wishes.create(WishDraft("Wortschatz", parentId = arabic))
        val routine = routines.create(routine("Vokabeln", goal = vocabulary, target = RoutineTarget.Streak(30)))

        wishes.deleteMovingPartsUp(vocabulary)
        assertEquals(arabic, routines.get(routine)?.goalId)

        wishes.delete(arabic)
        assertNull(routines.get(routine)?.goalId)
    }

    @Test
    fun `a target needs a goal and counts from 1 to 999`() {
        assertNull(routine("Vokabeln", target = RoutineTarget.Streak(30)).cleaned().target)
        fun problemsOf(target: RoutineTarget) = routine("Vokabeln", goal = 1, target = target).problems
        assertEquals(setOf(DraftProblem.TargetOutOfRange), problemsOf(RoutineTarget.Streak(0)))
        assertEquals(setOf(DraftProblem.TargetOutOfRange), problemsOf(RoutineTarget.TimesDone(1000)))
        assertEquals(emptySet<DraftProblem>(), problemsOf(RoutineTarget.TimesDone(999)))
    }

    private fun routine(name: String, goal: Long? = null, target: RoutineTarget? = null) =
        RoutineDraft(name, description = "", listOf(EIGHT), Repetition.Daily, goalId = goal, target = target)

    /** The routine done on each of [days], before today. */
    private suspend fun done(routine: Long, vararg days: String) = database.routines().insertOccurrences(
        days.map { Occurrence(routineId = routine, date = LocalDate.parse(it), time = EIGHT, status = OccurrenceStatus.Done) },
    )

    private companion object {
        val EIGHT: LocalTime = LocalTime.of(8, 0)
    }
}
