package de.emilyshub.wishes

import de.emilyshub.data.HubDatabase
import java.time.LocalDate

/**
 * Keeps goals done exactly while all their parts are (SPEC.md §6), after a part of [goalId] changed: done [today] once
 * the last one is, and in progress again once one is open again; and so on up the tree, as far as that changes a goal.
 * A goal without parts stays as the user set it; so does one set done by hand until one of its parts changes. To run in
 * the transaction that changed the part.
 */
internal suspend fun HubDatabase.settleGoal(goalId: Long?, today: LocalDate) {
    val seen = mutableSetOf<Long>()
    var id = goalId
    while (id != null && seen.add(id)) {
        val goal = wishes().get(id) ?: return
        val parts = wishes().partsDone(id)
        if (parts.isEmpty()) return
        val allDone = parts.all { it }
        when {
            allDone && goal.status != WishStatus.Done -> wishes().setStatus(id, WishStatus.Done, today)
            !allDone && goal.status == WishStatus.Done -> wishes().setStatus(id, WishStatus.InProgress, doneOn = null)
            else -> return
        }
        id = goal.parentId
    }
}
