package de.emilyshub.warum

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.Saving
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Mein Warum (SPEC.md §18): the Gründe on the board, in the order the user lays them, a new one on top. Nothing keeps
 * when it was opened or looked at, and the search passes it by (§8). [database] is only called on [io], since the first
 * call opens it. Writes run to the end once started, even if the screen that started them goes away.
 */
class Reasons(
    private val database: () -> HubDatabase,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().reasons()

    /** The reasons in the board's order. */
    fun board(): Flow<List<Reason>> = flow { emitAll(dao.observeAll()) }.flowOn(io)

    /** The reason [id] as its editor starts with it; none once it is gone. */
    suspend fun editing(id: Long): ReasonDraft? = withContext(io) { dao.find(id)?.let { ReasonDraft(it.text) } }

    /**
     * Saves [draft] as a new reason, on top of the board, with [id] none, or in place of the reason [id], where it is;
     * refused, with nothing saved, for its problems, or if that reason is gone.
     */
    suspend fun save(id: Long?, draft: ReasonDraft): Saving<ReasonProblem> {
        val problems = draft.problems
        if (problems.isNotEmpty()) return Saving.Refused(problems)
        val text = draft.cleaned().text
        return write {
            database().withTransaction {
                if (id == null) {
                    Saving.Saved(dao.insert(Reason(text = text, position = (dao.firstPosition() ?: 1) - 1)))
                } else {
                    val saved = dao.find(id) ?: return@withTransaction Saving.Refused(setOf(ReasonProblem.Gone))
                    dao.update(saved.copy(text = text))
                    Saving.Saved(id)
                }
            }
        }
    }

    /** Deletes the reason [id], if it is still there. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    /** Moves the reason [id] one place towards the top ("Nach vorn"); the first stays. */
    suspend fun forward(id: Long) = moveBy(id, -1)

    /** Moves the reason [id] one place towards the end ("Nach hinten"); the last stays. */
    suspend fun back(id: Long) = moveBy(id, 1)

    /** Moves the reason [id] to [index] on the board, as dragged there; past the end, it goes last. */
    suspend fun move(id: Long, index: Int) = write {
        database().withTransaction {
            val order = dao.all().toMutableList()
            val reason = order.find { it.id == id } ?: return@withTransaction
            order.remove(reason)
            order.add(index.coerceIn(0, order.size), reason)
            order.forEachIndexed { position, it -> if (it.position != position) dao.setPosition(it.id, position) }
        }
    }

    private suspend fun moveBy(id: Long, step: Int) = write {
        database().withTransaction {
            val index = dao.all().indexOfFirst { it.id == id }
            if (index >= 0) move(id, (index + step).coerceAtLeast(0))
        }
    }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }
}
