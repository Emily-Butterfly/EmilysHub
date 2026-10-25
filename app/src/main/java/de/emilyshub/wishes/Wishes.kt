package de.emilyshub.wishes

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
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
 * reminders of a change. [database] is only called on [io], since the first call opens it. Writes run to the end once
 * started, even if the screen that started them goes away.
 */
class Wishes(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().wishes()

    /** The wishes not yet done, each with its tags: those in progress first, then the open ones, each by title. */
    fun current(): Flow<List<WishWithTags>> = observe { all ->
        all.filter { it.wish.status != WishStatus.Done }
            .sortedWith(compareBy<WishWithTags> { it.wish.status != WishStatus.InProgress }.thenBy(InputText.byName) { it.wish.title })
    }

    /** The review (SPEC.md §6): the wishes come true, the last first, then by title. */
    fun fulfilled(): Flow<List<Wish>> = observe { all ->
        all.map { it.wish }
            .filter { it.status == WishStatus.Done }
            .sortedWith(compareByDescending<Wish> { it.doneOn }.thenBy(InputText.byName) { it.title })
    }

    /** The wish, or null once it is deleted. */
    suspend fun get(id: Long): Wish? = withContext(io) { dao.get(id) }

    /** The wish's tags (SPEC.md §8). */
    suspend fun tagIdsOf(id: Long): Set<Long> = withContext(io) { dao.tagIdsOf(id).toSet() }

    /** A new wish; refuses a draft with problems, which the editor shows before. */
    suspend fun create(draft: WishDraft): Long = write {
        val cleaned = checked(draft)
        database().withTransaction {
            dao.insert(cleaned.toWish(today())).also { id -> saveTags(id, cleaned.tagIds) }
        }
    }

    /** Changes the wish as [draft] has it, as [create] checks it. */
    suspend fun update(id: Long, draft: WishDraft) = write {
        val cleaned = checked(draft)
        database().withTransaction {
            if (dao.get(id) == null) return@withTransaction
            dao.update(cleaned.toWish(today(), id))
            saveTags(id, cleaned.tagIds)
        }
    }

    /** Deletes the wish for good, with its tags. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    /** The day it is now, which a wish done comes true on unless another is chosen, and none after. */
    fun today(): LocalDate = LocalDate.now(clock)

    private suspend fun saveTags(wishId: Long, tagIds: Set<Long>) {
        dao.untagAll(wishId)
        dao.tag(wishId, tagIds)
    }

    private fun <T> observe(select: (List<WishWithTags>) -> T): Flow<T> = flow { emitAll(dao.observeAll().map(select)) }.flowOn(io)

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    private fun checked(draft: WishDraft): WishDraft {
        require(draft.problems.isEmpty()) { "Invalid wish: ${draft.problems}" }
        return draft.cleaned()
    }
}
