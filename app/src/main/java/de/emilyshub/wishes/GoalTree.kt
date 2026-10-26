package de.emilyshub.wishes

import androidx.room.ColumnInfo
import de.emilyshub.data.InputText
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.routines.RoutineTarget
import de.emilyshub.routines.RoutineWithOutcomes
import de.emilyshub.stats.RoutineHistory
import kotlin.math.roundToInt

/**
 * A to-do hanging at a goal (SPEC.md §6), as far as the goal's progress goes: [done], or with [ticked] of the [items]
 * of its checklist ticked off.
 */
data class GoalTodo(
    val id: Long,
    val title: String,
    @ColumnInfo(name = "goal_id") val goalId: Long,
    val done: Boolean,
    val items: Int = 0,
    val ticked: Int = 0,
) {
    /** From 0 to 1. */
    val progress: Double get() = if (done) 1.0 else if (items > 0) ticked.toDouble() / items else 0.0
}

/**
 * A routine hanging at a goal (SPEC.md §2, §6), with what it aims at, its [target], if any, and how far its history has
 * come towards that; paused or archived, as its [status] says, it counts all the same. Without a target it only shows
 * with the goal and counts towards nothing.
 */
class GoalRoutine(
    val id: Long,
    val name: String,
    val goalId: Long,
    val target: RoutineTarget?,
    history: RoutineHistory,
    val status: RoutineStatus,
) {
    private val streaks = history.streaks()
    private val timesDone = history.timesDone

    /** Towards its target, the days done in a row up to now, or the times done; none without a target. */
    val soFar: Int? = when (target) {
        is RoutineTarget.Streak -> streaks.current
        is RoutineTarget.TimesDone -> timesDone
        null -> null
    }

    /** Whether it reached its target: so many days done in a row once, though broken since, or done so many times. */
    val reached: Boolean = when (target) {
        is RoutineTarget.Streak -> streaks.longest >= target.count
        is RoutineTarget.TimesDone -> timesDone >= target.count
        null -> false
    }

    /** From 0 to 1, how far it has come towards its target, all of it once reached; none without a target. */
    val progress: Double? = target?.let { if (reached) 1.0 else (soFar ?: 0).toDouble() / it.count }

    companion object {
        /** The routine at its goal, with how far it has come by how its occurrences ended; none if it hangs under none. */
        fun of(routine: RoutineWithOutcomes): GoalRoutine? = with(routine.routine) {
            goalId?.let { GoalRoutine(id, name, it, target, RoutineHistory(routine.outcomes), status) }
        }
    }
}

/**
 * A goal (SPEC.md §6): a wish with the [subgoals], [todos] and [routines] hanging under it, its parts. How far it has
 * come, its [progress], is worked out from them, all the way down, unless set by hand ([Wish.manualProgress]); a goal
 * done has come all the way. A routine counts only with a target.
 */
class Goal(
    private val withTags: WishWithTags,
    val subgoals: List<Goal>,
    val todos: List<GoalTodo>,
    val routines: List<GoalRoutine>,
) {

    val wish: Wish get() = withTags.wish

    val tagIds: Set<Long> get() = withTags.tagIds

    /** Whether anything hangs under it, to show below it. */
    val hasParts: Boolean get() = subgoals.isNotEmpty() || todos.isNotEmpty() || routines.isNotEmpty()

    /** Whether anything under it counts towards its progress: all but routines without a target. */
    val hasCountingParts: Boolean get() = countingProgress.isNotEmpty()

    private val countingProgress: List<Double> by lazy {
        subgoals.map { it.progress } + todos.map { it.progress } + routines.mapNotNull { it.progress }
    }

    /** From 0 to 1, the mean of the progress of the parts that count; nothing without such. */
    val automaticProgress: Double by lazy { if (countingProgress.isEmpty()) 0.0 else countingProgress.average() }

    /** From 0 to 1: all of it once done, else as set by hand, else [automaticProgress]. */
    val progress: Double by lazy {
        if (wish.status == WishStatus.Done) 1.0 else wish.manualProgress?.let { it / FULL.toDouble() } ?: automaticProgress
    }

    /** [progress] in whole percent: 100 only when all of it is done and 0 only when nothing is, so rounding hides neither. */
    val percent: Int get() = percentOf(progress)

    val automaticPercent: Int get() = percentOf(automaticProgress)

    private fun percentOf(fraction: Double): Int = (fraction * FULL).roundToInt().coerceIn(
        minimumValue = if (fraction > 0) 1 else 0,
        maximumValue = if (fraction < 1) FULL - 1 else FULL,
    )

    private companion object {
        const val FULL = WishDraft.PROGRESS_MAX
    }
}

/** A goal to choose, such as the one a to-do hangs under, at its [depth] in the tree, for it to show indented. */
data class GoalChoice(val id: Long, val title: String, val depth: Int)

/** A wish come true, for the review (SPEC.md §6), with the title of the goal it is part of, if any. */
data class FulfilledWish(val wish: Wish, val partOf: String?)

object GoalTree {

    /**
     * The goals at the top, each with its parts below it, subgoals by title, then to-dos by title, then routines by name.
     * A goal whose parent is gone, or that is caught in a cycle, which the app never makes, is at the top too, so that
     * each shows once.
     */
    fun build(wishes: List<WishWithTags>, todos: List<GoalTodo>, routines: List<GoalRoutine>): List<Goal> {
        val byId = wishes.associateBy { it.wish.id }
        val subgoalsOf = wishes.groupBy { it.wish.parentId }
        val todosOf = todos.groupBy { it.goalId }
        val routinesOf = routines.groupBy { it.goalId }
        val placed = mutableSetOf<Long>()

        fun goal(details: WishWithTags): Goal {
            placed += details.wish.id
            val subgoals = subgoalsOf[details.wish.id].orEmpty()
                .filter { it.wish.id !in placed }
                .sortedWith(compareBy(InputText.byName) { it.wish.title })
                .map(::goal)
            val todos = todosOf[details.wish.id].orEmpty().sortedWith(compareBy(InputText.byName) { it.title })
            val routines = routinesOf[details.wish.id].orEmpty().sortedWith(compareBy(InputText.byName) { it.name })
            return Goal(details, subgoals, todos, routines)
        }

        val tops = wishes.filter { it.wish.parentId == null || it.wish.parentId !in byId }.map(::goal)
        // Placing one caught in a cycle places those below it too.
        val stranded = buildList { wishes.forEach { if (it.wish.id !in placed) add(goal(it)) } }
        return tops + stranded
    }

    /** Each goal of [tree] in its order, the goals below each after it, but [excluding] one and those below it. */
    fun choices(tree: List<Goal>, excluding: Long? = null): List<GoalChoice> {
        fun choicesFrom(goal: Goal, depth: Int): List<GoalChoice> =
            if (goal.wish.id == excluding) {
                emptyList()
            } else {
                listOf(GoalChoice(goal.wish.id, goal.wish.title, depth)) + goal.subgoals.flatMap { choicesFrom(it, depth + 1) }
            }
        return tree.flatMap { choicesFrom(it, 0) }
    }

    /** Each goal of [tree] with the goal it is part of, if any, in the order of the tree. */
    fun withParents(tree: List<Goal>): List<Pair<Goal, Goal?>> {
        fun from(goal: Goal, parent: Goal?): List<Pair<Goal, Goal?>> =
            listOf(goal to parent) + goal.subgoals.flatMap { from(it, parent = goal) }
        return tree.flatMap { from(it, parent = null) }
    }

    /** The goals of [tree] that [match], each with its parts, in the order of the tree; those below one of them not again. */
    fun highest(tree: List<Goal>, match: (Goal) -> Boolean): List<Goal> =
        tree.flatMap { if (match(it)) listOf(it) else highest(it.subgoals, match) }

    /** Whether [goal] may hang under [parent]: not under itself, nor under any goal below it, as [parentOf] says. */
    fun canHangUnder(goal: Long, parent: Long, parentOf: (Long) -> Long?): Boolean {
        val seen = mutableSetOf<Long>()
        var above: Long? = parent
        while (above != null && seen.add(above)) {
            if (above == goal) return false
            above = parentOf(above)
        }
        return true
    }
}
