package de.emilyshub.wishes

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.todos.TodoDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext

/**
 * The wishlist (SPEC.md §6): things to do some day, deliberately without reminders, so that nothing here tells the
 * reminders of a change. Wishes are goals, with subgoals to any depth and to-dos hanging under them; a goal is done once
 * all of its parts are. [database] is only called on [io], since the first call opens it. Writes run to the end once
 * started, even if the screen that started them goes away.
 */
class Wishes(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().wishes()

    /** The goals at the top not yet done, each with its parts: those in progress first, then the open ones, each by title. */
    fun current(): Flow<List<Goal>> =
        tree().map { all -> all.filter { it.wish.status != WishStatus.Done }.sortedWith(inProgressFirst) }

    /**
     * Of the goals [current] has, those with the tag [tagId], at any depth, each with its parts, ordered as [current]
     * orders them; one below another with the tag shows with that one only.
     */
    fun withTag(current: List<Goal>, tagId: Long): List<Goal> =
        GoalTree.highest(current) { it.wish.status != WishStatus.Done && tagId in it.tagIds }.sortedWith(inProgressFirst)

    /** The review (SPEC.md §6): the goals come true, at any depth, the last first, then by title. */
    fun fulfilled(): Flow<List<FulfilledWish>> = tree().map { all ->
        GoalTree.withParents(all)
            .filter { (goal, _) -> goal.wish.status == WishStatus.Done }
            .sortedWith(compareBy(lastDoneFirst) { (goal, _) -> goal })
            .map { (goal, parent) -> FulfilledWish(goal.wish, partOf = parent?.wish?.title) }
    }

    /** The goal with its parts, or null once it is deleted. */
    fun goal(id: Long): Flow<Goal?> = tree().map { all -> all.firstNotNullOfOrNull { it.find(id) } }

    /**
     * The goals to choose from, such as the one a to-do hangs under, as the tree has them: those not done first, as
     * [current] orders them, then those come true. Without [excluding] and those below it, which cannot be its parent.
     */
    fun goalChoices(excluding: Long? = null): Flow<List<GoalChoice>> = tree().map { all ->
        val (done, open) = all.partition { it.wish.status == WishStatus.Done }
        GoalTree.choices(open.sortedWith(inProgressFirst) + done.sortedWith(lastDoneFirst), excluding)
    }

    /** The wish, or null once it is deleted. */
    suspend fun get(id: Long): Wish? = withContext(io) { dao.get(id) }

    /** The wish's tags (SPEC.md §8). */
    suspend fun tagIdsOf(id: Long): Set<Long> = withContext(io) { dao.tagIdsOf(id).toSet() }

    /**
     * A new wish; refuses a draft with problems, which the editor shows before. Under a goal, it is a part of it, which
     * opens that goal again if it was done.
     */
    suspend fun create(draft: WishDraft): Long = write {
        val cleaned = checked(draft)
        database().withTransaction {
            val parentId = dao.stillThere(cleaned.parentId)
            val id = dao.insert(cleaned.copy(parentId = parentId).toWish(today()))
            saveTags(id, cleaned.tagIds)
            settleGoals(GoalPart.None, GoalPart(parentId, open = cleaned.status != WishStatus.Done))
            id
        }
    }

    /**
     * Changes the wish as [draft] has it, as [create] checks it; refuses to hang it under itself or one of the goals
     * below it (SPEC.md §6). The goals it was and is part of are settled anew.
     */
    suspend fun update(id: Long, draft: WishDraft) = write {
        val cleaned = checked(draft)
        database().withTransaction {
            val wish = dao.get(id) ?: return@withTransaction
            val parentId = dao.stillThere(cleaned.parentId)
            if (parentId != null) {
                val parents = dao.all().associate { it.id to it.parentId }
                require(GoalTree.canHangUnder(id, parentId, parents::get)) { "A goal cannot hang under itself" }
            }
            dao.update(cleaned.copy(parentId = parentId).toWish(today(), id))
            saveTags(id, cleaned.tagIds)
            settleGoals(wish.asPart(), GoalPart(parentId, open = cleaned.status != WishStatus.Done))
        }
    }

    /** Deletes the wish for good, with its tags and its subgoals; the to-dos of all of them stay, hanging at no goal. */
    suspend fun delete(id: Long) = write {
        database().withTransaction {
            val wish = dao.get(id) ?: return@withTransaction
            dao.delete(id)
            settleGoals(wish.asPart(), GoalPart.None)
        }
    }

    /**
     * Deletes the wish for good, with its tags, but not its parts: its subgoals and to-dos move up a level, to the goal it
     * was part of, or to the top and to no goal.
     */
    suspend fun deleteMovingPartsUp(id: Long) = write {
        database().withTransaction {
            val wish = dao.get(id) ?: return@withTransaction
            val partsOpen = dao.partsDone(id).any { !it }
            dao.moveSubgoals(from = id, to = wish.parentId)
            dao.moveTodos(from = id, to = wish.parentId)
            dao.delete(id)
            // In its place, its parts: open if one of them is.
            settleGoals(wish.asPart(), GoalPart(wish.parentId, open = partsOpen))
        }
    }

    /** A to-do made of the wish (SPEC.md §6), to be saved as one: with its title, description and tags, hanging under it. */
    suspend fun todoDraftOf(id: Long): TodoDraft = withContext(io) {
        val wish = checkNotNull(dao.get(id)) { "No wish $id" }
        TodoDraft(wish.title, wish.description, tagIds = dao.tagIdsOf(id).toSet(), goalId = id)
    }

    /** The day it is now, which a wish done comes true on unless another is chosen, and none after. */
    fun today(): LocalDate = LocalDate.now(clock)

    private fun tree(): Flow<List<Goal>> =
        flow { emitAll(combine(dao.observeAll(), dao.observeGoalTodos(), GoalTree::build)) }.flowOn(io)

    private suspend fun settleGoals(before: GoalPart, after: GoalPart) = database().settleGoals(before, after, today())

    private fun Wish.asPart() = GoalPart(parentId, open = status != WishStatus.Done)

    private suspend fun saveTags(wishId: Long, tagIds: Set<Long>) {
        dao.untagAll(wishId)
        dao.tag(wishId, tagIds)
    }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    private fun checked(draft: WishDraft): WishDraft {
        require(draft.problems.isEmpty()) { "Invalid wish: ${draft.problems}" }
        return draft.cleaned()
    }

    private fun Goal.find(id: Long): Goal? = if (wish.id == id) this else subgoals.firstNotNullOfOrNull { it.find(id) }

    private companion object {
        val inProgressFirst = compareBy<Goal> { it.wish.status != WishStatus.InProgress }.thenBy(InputText.byName) { it.wish.title }
        val lastDoneFirst = compareByDescending<Goal> { it.wish.doneOn }.thenBy(InputText.byName) { it.wish.title }
    }
}
