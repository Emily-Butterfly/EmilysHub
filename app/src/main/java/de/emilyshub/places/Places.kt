package de.emilyshub.places

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
 * The named places (SPEC.md §4), recognised by their WLANs. [database] is only called on [io], since the first call
 * opens it. Writes run to the end once started, even if the screen that started them goes away. After each write,
 * [onChange] runs, for the reminders to follow.
 */
class Places(
    private val database: () -> HubDatabase,
    private val io: CoroutineContext = Dispatchers.IO,
    private val onChange: suspend () -> Unit = {},
) {
    private val dao get() = database().places()

    /** All places by name, each with its WLANs. */
    fun all(): Flow<List<PlaceWithWifis>> = flow {
        emitAll(dao.observeAll().map { all -> all.sortedWith(compareBy(InputText.byName, PlaceWithWifis::name)) })
    }.flowOn(io)

    suspend fun get(id: Long): PlaceWithWifis? = withContext(io) { dao.all().find { it.id == id } }

    /** A new place; refuses one with problems, which the editor shows before. */
    suspend fun create(draft: PlaceDraft): Long = write {
        database().withTransaction {
            val place = checked(draft, others = dao.all())
            dao.insert(Place(name = place.name)).also { id -> dao.insertWifis(place.wifis.map { PlaceWifi(id, it) }) }
        }
    }

    /** Renames the place and gives it the WLANs of [draft], as [create] checks them. */
    suspend fun update(id: Long, draft: PlaceDraft) = write {
        database().withTransaction {
            val place = checked(draft, others = dao.all().filter { it.id != id })
            dao.rename(id, place.name)
            dao.deleteWifisOf(id)
            dao.insertWifis(place.wifis.map { PlaceWifi(id, it) })
        }
    }

    /** Deletes the place with its WLANs; the routines bound to it are bound to it no more. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block().also { onChange() } }

    private fun checked(draft: PlaceDraft, others: List<PlaceWithWifis>): PlaceDraft {
        val problems = draft.problems(others)
        require(problems.isEmpty()) { "Invalid place: $problems" }
        return draft.cleaned()
    }
}
