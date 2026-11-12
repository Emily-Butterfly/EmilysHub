package de.emilyshub.wishes

import de.emilyshub.data.HubDatabase
import de.emilyshub.routines.RoutineWithOutcomes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Watches the parts of goals (SPEC.md §6) through a change, so that their goals are settled once it is over: what
 * changes a to-do, routine, subgoal or exam names it here before it does ([todo], [routine], [goal], [exam]), or once
 * it made it ([todoAdded], [routineAdded], [goalAdded], [examAdded]), and the watch takes how it was then. At the end of [watchingGoals], it
 * settles the goal of each, as [settleGoals] has it, from how it was to how it is: no caller holds on to a part.
 *
 * A watch started within another, in the same change, joins it: the goals are settled once, at the end of the outer
 * one, so that a part closed and opened again within it leaves its goal as it was.
 */
internal class GoalWatch private constructor(
    private val database: HubDatabase,
) : AbstractCoroutineContextElement(GoalWatch) {

    /** How each part named was before the change, by which part it is. */
    private val before = linkedMapOf<Part, GoalPart>()

    /** The to-do [id] is about to change (SPEC.md §5, §6). */
    suspend fun todo(id: Long) = watch(Part.Todo(id))

    /** The routine [id] is about to change, its occurrences too, which count towards its target (SPEC.md §6). */
    suspend fun routine(id: Long) = watch(Part.Routine(id))

    /** The goal [id] is about to change, as a part of the goal above it (SPEC.md §6). */
    suspend fun goal(id: Long) = watch(Part.Goal(id))

    /** The exam [id] of Sprachen is about to change, taken or not, or moved to another goal (SPEC.md §6, §15). */
    suspend fun exam(id: Long) = watch(Part.Exam(id))

    /** The exam [id] was just made: part of no goal before (SPEC.md §15). */
    fun examAdded(id: Long) = added(Part.Exam(id))

    /** The to-do [id] was just made: part of no goal before (SPEC.md §6). */
    fun todoAdded(id: Long) = added(Part.Todo(id))

    /** The routine [id] was just made: part of no goal before (SPEC.md §6). */
    fun routineAdded(id: Long) = added(Part.Routine(id))

    /** The goal [id] was just made: part of no goal above before (SPEC.md §6). */
    fun goalAdded(id: Long) = added(Part.Goal(id))

    /**
     * The to-do [id] takes the place of [replaced] in its goal (SPEC.md §5, §6), as the next of a series does when
     * one is ticked off, and the one ticked off when it is opened again and takes its place back: it was before as
     * that one was at the start of the change, or is now if not named yet, so that one in the place of the other
     * changes nothing, and one moved to another goal in the same change counts as moved. One named already stays as
     * it was.
     */
    suspend fun todoTakingPlaceOf(id: Long, replaced: Long) {
        val part = Part.Todo(id)
        if (part !in before) before[part] = before[Part.Todo(replaced)] ?: partOf(Part.Todo(replaced))
    }

    /** Takes how [part] is now as how it was before, unless it was named already in this change. */
    private suspend fun watch(part: Part) {
        if (part !in before) before[part] = partOf(part)
    }

    /** Takes [part], just made, as part of no goal before, unless it was named already in this change. */
    private fun added(part: Part) {
        before.putIfAbsent(part, GoalPart.None)
    }

    /** Settles the goals of the parts named, from how they were to how they are, [today]. */
    private suspend fun settle(today: LocalDate) {
        before.forEach { (part, was) -> database.settleGoals(was, partOf(part), today) }
    }

    /** How [part] is now as a part of its goal; [GoalPart.None] once it is gone. */
    private suspend fun partOf(part: Part): GoalPart = when (part) {
        // One ticked off whose next came in its place counts no more.
        is Part.Todo -> database.todos().get(part.id)?.let { todo ->
            GoalPart(todo.goalId, open = todo.doneAt == null, counts = todo.nextId == null)
        }
        // Counting with a target, and open while it has not reached that; one without a target only shows.
        is Part.Routine -> database.routines().get(part.id)?.let { routine ->
            if (routine.goalId == null || routine.target == null) {
                GoalPart(routine.goalId, open = false, counts = false)
            } else {
                val outcomes = database.routines().outcomesOf(part.id)
                val reached = GoalRoutine.of(RoutineWithOutcomes(routine, outcomes))?.reached ?: false
                GoalPart(routine.goalId, open = !reached)
            }
        }
        is Part.Goal -> database.wishes().get(part.id)?.let { GoalPart(it.parentId, open = it.status != WishStatus.Done) }
        // Done once taken, whatever its topics.
        is Part.Exam -> database.languages().exam(part.id)?.let { GoalPart(it.goalId, open = !it.taken) }
    } ?: GoalPart.None

    /** A part of a goal, by what it is (SPEC.md §6). */
    private sealed interface Part {
        /** A to-do, of the goal it hangs under. */
        data class Todo(val id: Long) : Part

        /** A routine, of the goal it aims at. */
        data class Routine(val id: Long) : Part

        /** A subgoal, of the goal above it. */
        data class Goal(val id: Long) : Part

        /** An exam of Sprachen, of the goal it is part of. */
        data class Exam(val id: Long) : Part
    }

    /** Finds the watch running in a change, as the change's coroutines carry it. */
    companion object Key : CoroutineContext.Key<GoalWatch> {
        /** Runs [change] with a watch, or with the one already running, and settles at the end of the outermost one. */
        internal suspend fun <T> watching(database: HubDatabase, today: LocalDate, change: suspend (GoalWatch) -> T): T {
            currentCoroutineContext()[GoalWatch]?.let { return change(it) }
            val watch = GoalWatch(database)
            return withContext(watch) { change(watch) }.also { watch.settle(today) }
        }
    }
}

/**
 * Runs [change] to parts of goals, which it names to the watch, and then settles their goals [today], as [GoalWatch]
 * has it. Within the transaction of the change.
 */
internal suspend fun <T> HubDatabase.watchingGoals(today: LocalDate, change: suspend (GoalWatch) -> T): T =
    GoalWatch.watching(this, today, change)
