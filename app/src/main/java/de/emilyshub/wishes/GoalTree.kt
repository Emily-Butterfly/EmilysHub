package de.emilyshub.wishes

import androidx.room.ColumnInfo
import de.emilyshub.data.InputText
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
 * A goal (SPEC.md §6): a wish with the [subgoals] and [todos] hanging under it, its parts. How far it has come, its
 * [progress], is worked out from them, all the way down, unless set by hand ([Wish.manualProgress]); a goal done has
 * come all the way.
 */
class Goal(val details: WishWithTags, val subgoals: List<Goal>, val todos: List<GoalTodo>) {

    val wish: Wish get() = details.wish

    val hasParts: Boolean get() = subgoals.isNotEmpty() || todos.isNotEmpty()

    /** Whether it has parts and all of them are done: each subgoal and each to-do. */
    val allPartsDone: Boolean
        get() = hasParts && subgoals.all { it.wish.status == WishStatus.Done } && todos.all { it.done }

    /** From 0 to 1, the mean of its parts' progress; nothing without parts. */
    val automaticProgress: Double by lazy {
        val parts = subgoals.map { it.progress } + todos.map { it.progress }
        if (parts.isEmpty()) 0.0 else parts.average()
    }

    /** From 0 to 1: all of it once done, else as set by hand, else [automaticProgress]. */
    val progress: Double by lazy {
        if (wish.status == WishStatus.Done) 1.0 else wish.manualProgress?.let { it / PERCENT.toDouble() } ?: automaticProgress
    }

    /** [progress] in whole percent: 100 only when all of it is done and 0 only when nothing is, so rounding hides neither. */
    val percent: Int get() = percentOf(progress)

    val automaticPercent: Int get() = percentOf(automaticProgress)

    private fun percentOf(fraction: Double): Int = (fraction * PERCENT).roundToInt().coerceIn(
        minimumValue = if (fraction > 0) 1 else 0,
        maximumValue = if (fraction < 1) PERCENT - 1 else PERCENT,
    )

    private companion object {
        const val PERCENT = 100
    }
}

/** A goal to choose, such as the one a to-do hangs under, at its [depth] in the tree, for it to show indented. */
data class GoalChoice(val id: Long, val title: String, val depth: Int)

object GoalTree {

    /**
     * The goals at the top, each with its parts below it, subgoals by title and then to-dos by title. A goal whose parent
     * is gone, or that is caught in a cycle, which the app never makes, is at the top too, so that each shows once.
     */
    fun build(wishes: List<WishWithTags>, todos: List<GoalTodo>): List<Goal> {
        val byId = wishes.associateBy { it.wish.id }
        val subgoalsOf = wishes.groupBy { it.wish.parentId }
        val todosOf = todos.groupBy { it.goalId }
        val placed = mutableSetOf<Long>()

        fun goal(details: WishWithTags): Goal {
            placed += details.wish.id
            val subgoals = subgoalsOf[details.wish.id].orEmpty()
                .filter { it.wish.id !in placed }
                .sortedWith(compareBy(InputText.byName) { it.wish.title })
                .map(::goal)
            val parts = todosOf[details.wish.id].orEmpty().sortedWith(compareBy(InputText.byName) { it.title })
            return Goal(details, subgoals, parts)
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
