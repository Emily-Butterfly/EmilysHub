package de.emilyshub.wishes

import de.emilyshub.data.HubDatabase
import de.emilyshub.routines.Routine
import java.time.LocalDate

/** A to-do, subgoal or routine as a part of the goal [goalId], if any, and whether it is [open]: not done. */
internal data class GoalPart(val goalId: Long?, val open: Boolean) {
    companion object {
        /** Not a part of any goal: not there yet, or gone. */
        val None = GoalPart(goalId = null, open = false)
    }
}

/**
 * Settles the goals (SPEC.md §6) of a part that changed from [before] to [after], in the transaction that changed it:
 * a part open that was not makes its goal, if done, in progress again; the last part open closed, done or gone, makes it
 * done [today]. So on up the tree, as far as that changes a goal. Anything else leaves a goal as the user set it: one
 * without parts, one set done by hand while parts are still open, and one set open again with all of them done, until
 * one of them is done anew.
 */
internal suspend fun HubDatabase.settleGoals(before: GoalPart, after: GoalPart, today: LocalDate) {
    if (before.goalId == after.goalId) {
        settleGoal(after.goalId, PartChange.of(before.open, after.open), today)
    } else {
        settleGoal(before.goalId, PartChange.of(before.open, isOpen = false), today)
        settleGoal(after.goalId, PartChange.of(wasOpen = false, after.open), today)
    }
}

/** How a part of a goal changed, as far as the goal being done goes. */
private enum class PartChange {
    /** Open, and it was not. */
    Opened,

    /** Not open anymore. */
    Closed,
    ;

    companion object {
        fun of(wasOpen: Boolean, isOpen: Boolean): PartChange? = when {
            wasOpen == isOpen -> null
            isOpen -> Opened
            else -> Closed
        }
    }
}

/** Settles the goal [goalId] after [change] to one of its parts, and then those above it, as far as that changes them. */
private suspend fun HubDatabase.settleGoal(goalId: Long?, change: PartChange?, today: LocalDate) {
    if (change == null) return
    val seen = mutableSetOf<Long>()
    var id = goalId
    while (id != null && seen.add(id)) {
        val goal = wishes().get(id) ?: return
        val done = goal.status == WishStatus.Done
        when {
            change == PartChange.Opened && done -> wishes().setStatus(id, WishStatus.InProgress, doneOn = null)
            change == PartChange.Closed && !done && allPartsDone(id) -> wishes().setStatus(id, WishStatus.Done, today)
            else -> return
        }
        id = goal.parentId
    }
}

/** Whether the goal has parts that count and all of them are done. */
private suspend fun HubDatabase.allPartsDone(goalId: Long): Boolean =
    partsDoneOf(goalId).let { it.isNotEmpty() && it.all { done -> done } }

/**
 * Whether each part of the goal that counts is done (SPEC.md §6): its subgoals, its to-dos, and its routines with a
 * target, done once they reached it; routines without one only show.
 */
internal suspend fun HubDatabase.partsDoneOf(goalId: Long): List<Boolean> {
    val routinesDone = routines().withGoal(goalId).mapNotNull { goalRoutineOf(it)?.takeIf { part -> part.target != null }?.reached }
    return wishes().partsDone(goalId) + routinesDone
}

/** The routine as a part of its goal: open while it has a target not reached yet; without one it holds nothing open. */
internal suspend fun HubDatabase.goalPartOfRoutine(routineId: Long): GoalPart {
    val routine = routines().get(routineId) ?: return GoalPart.None
    val part = goalRoutineOf(routine) ?: return GoalPart.None
    return GoalPart(part.goalId, open = part.target != null && !part.reached)
}

private suspend fun HubDatabase.goalRoutineOf(routine: Routine): GoalRoutine? =
    GoalRoutine.of(routine, routines().outcomesOf(routine.id))
