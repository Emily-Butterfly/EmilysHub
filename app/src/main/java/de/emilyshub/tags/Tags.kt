package de.emilyshub.tags

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
import kotlin.coroutines.CoroutineContext

/**
 * The tags shared by all areas (SPEC.md §8). [database] is only called on [io], since the first call opens it.
 * Writes run to the end once started, even if the screen that started them goes away.
 */
class Tags(private val database: () -> HubDatabase, private val io: CoroutineContext = Dispatchers.IO) {
    private val dao get() = database().tags()

    /** All tags by name. */
    fun all(): Flow<List<Tag>> = flow {
        emitAll(dao.observeAll().map { all -> all.sortedWith(compareBy(InputText.byName, Tag::name)) })
    }.flowOn(io)

    /** A new tag named [name] on a single line; refuses a name with a problem, which the screens show before. */
    suspend fun create(name: String): Long = write {
        database().withTransaction { dao.insert(Tag(name = checked(name, others = dao.all()))) }
    }

    /** Renames the tag, as [create] names one; wherever it is given, it carries the new name. */
    suspend fun rename(id: Long, name: String) = write {
        database().withTransaction { dao.rename(id, checked(name, others = dao.all().filter { it.id != id })) }
    }

    /** Deletes the tag: it is taken off everything that has it, which stays as it was. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    private fun checked(name: String, others: List<Tag>): String {
        val problem = tagNameProblem(name, others)
        require(problem == null) { "Invalid tag name: $problem" }
        return InputText.singleLine(name)
    }
}
