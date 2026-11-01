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
 * changes a to-do, routine or subgoal names it here before it does ([todo], [routine], [goal]), or once it made it
 * ([todoAdded], [routineAdded], [goalAdded]), and the watch takes how it was then. At the end of [watchingGoals], it
 * settles the goal of each, as [settleGoals] has it, from how it was to how it is: no caller holds on to a part.
 *
 * A watch started within another, in the same change, joins it: the goals are settled once, at the end of the outer
 * one, so that a part closed and opened again within it leaves its goal as it was.
 */
internal class GoalWatch private constructor(private val database: HubDatabase) : AbstractCoroutineContextElement(GoalWatch) {

    /** How each part named was before the change, by which part it is. */
    private val before = linkedMapOf<Part, GoalPart>()

    /** The to-do [id] is about to change. */
    suspend fun todo(id: Long) = watch(Part.Todo(id))

    /** The routine [id] is about to change. */
    suspend fun routine(id: Long) = watch(Part.Routine(id))

    /** The goal [id] is about to change, as a part of the goal above it. */
    suspend fun goal(id: Long) = watch(Part.Goal(id))

    /** The to-do [id] was just made: part of no goal before. */
    fun todoAdded(id: Long) = added(Part.Todo(id))

    /** The routine [id] was just made: part of no goal before. */
    fun routineAdded(id: Long) = added(Part.Routine(id))

    /** The goal [id] was just made: part of no goal before. */
    fun goalAdded(id: Long) = added(Part.Goal(id))

    /**
     * The to-do [id] is about to take the place of [replaced] in its goal, as one ticked off and opened again takes its
     * place back from the next of its series: it was before as that one was, so that one in place of the other changes
     * nothing.
     */
    suspend fun todoTakingPlaceOf(id: Long, replaced: Long) {
        val part = Part.Todo(id)
        if (part !in before) before[part] = partOf(Part.Todo(replaced))
    }

    /** Takes how [part] is now as how it was before, unless it was named already in this change. */
    private suspend fun watch(part: Part) {
        if (part !in before) before[part] = partOf(part)
    }

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
        is Part.Todo -> database.todos().get(part.id)?.let { GoalPart(it.goalId, open = it.doneAt == null, counts = it.nextId == null) }
        // Counting with a target, and open while it has not reached that; one without a target only shows.
        is Part.Routine -> database.routines().get(part.id)?.let { routine ->
            if (routine.goalId == null || routine.target == null) {
                GoalPart(routine.goalId, open = false, counts = false)
            } else {
                val reached = GoalRoutine.of(RoutineWithOutcomes(routine, database.routines().outcomesOf(part.id)))?.reached ?: false
                GoalPart(routine.goalId, open = !reached)
            }
        }
        is Part.Goal -> database.wishes().get(part.id)?.let { GoalPart(it.parentId, open = it.status != WishStatus.Done) }
    } ?: GoalPart.None

    /** A part of a goal, by what it is. */
    private sealed interface Part {
        data class Todo(val id: Long) : Part

        data class Routine(val id: Long) : Part

        data class Goal(val id: Long) : Part
    }

    companion object Key : CoroutineContext.Key<GoalWatch> {
        /** Runs [change] with a watch, or with the one already running, and settles at the end of the outermost one. */
        internal suspend fun <T> run(database: HubDatabase, today: LocalDate, change: suspend (GoalWatch) -> T): T {
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
    GoalWatch.run(this, today, change)
