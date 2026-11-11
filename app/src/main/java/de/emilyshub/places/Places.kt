package de.emilyshub.places

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.data.Saving
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
 * The named places (SPEC.md §4), recognised by their WLANs and GPS areas. [database] is only called on [io], since the first call
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

    /** The place [id] as it is saved, to edit (SPEC.md §4), with its WLANs and GPS area; null once it is deleted. */
    suspend fun editing(id: Long): PlaceEditing? = withContext(io) { dao.all().find { it.id == id }?.let { PlaceEditing(id, it.toDraft()) } }

    /**
     * Saves [draft] as [editing] has it: a new place, or the changes to the one opened; refused, with nothing saved, for
     * its problems, the name checked against the other places as they are saved, not as a screen last saw them.
     */
    suspend fun save(editing: PlaceEditing, draft: PlaceDraft): Saving<PlaceProblem> = write {
        database().withTransaction {
            val problems = draft.problems(others = dao.all().filter { it.id != editing.id })
            if (problems.isNotEmpty()) return@withTransaction Saving.Refused(problems)
            val id = editing.id?.also { replace(it, draft.cleaned()) } ?: insert(draft.cleaned())
            Saving.Saved(id)
        }
    }

    /** A new place; refuses one with problems, which the editor shows before. */
    suspend fun create(draft: PlaceDraft): Long = write {
        database().withTransaction { insert(checked(draft, others = dao.all())) }
    }

    /** Renames the place and gives it the WLANs and GPS area of [draft], as [create] checks them. */
    suspend fun update(id: Long, draft: PlaceDraft) = write {
        database().withTransaction { replace(id, checked(draft, others = dao.all().filter { it.id != id })) }
    }

    /** Deletes the place with its WLANs; the routines bound to it are bound to it no more. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block().also { onChange() } }

    private suspend fun insert(place: PlaceDraft): Long {
        val id = dao.insert(Place(name = place.name, area = place.area()))
        dao.insertWifis(place.wifis.map { PlaceWifi(id, it) })
        return id
    }

    private suspend fun replace(id: Long, place: PlaceDraft) {
        dao.update(Place(id, place.name, place.area()))
        dao.deleteWifisOf(id)
        dao.insertWifis(place.wifis.map { PlaceWifi(id, it) })
    }

    private fun checked(draft: PlaceDraft, others: List<PlaceWithWifis>): PlaceDraft {
        val problems = draft.problems(others)
        require(problems.isEmpty()) { "Invalid place: $problems" }
        return draft.cleaned()
    }
}
