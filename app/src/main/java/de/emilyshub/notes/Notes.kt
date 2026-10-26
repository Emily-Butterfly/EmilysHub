package de.emilyshub.notes

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
import kotlin.coroutines.CoroutineContext

/**
 * The notes and their tabs (SPEC.md §7). [database] is only called on [io], since the first call opens it. Writes run
 * to the end once started, even if the screen that started them goes away.
 */
class Notes(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().notes()

    /** The tabs made by the user, in their order, after "Alle" and "Allgemein". */
    fun tabs(): Flow<List<NoteTab>> = flow { emitAll(dao.observeTabs()) }.flowOn(io)

    /** The notes the tab [selection] shows, the one changed last first, then by title. */
    fun notes(selection: TabSelection): Flow<List<Note>> = flow {
        emitAll(dao.observeNotes().map { all -> all.filter { selection.shows(it.tabId) }.sortedWith(lastChangedFirst) })
    }.flowOn(io)

    /** The note, or null once it is deleted. */
    suspend fun get(id: Long): Note? = withContext(io) { dao.get(id) }

    /** A new note, changed now; refuses a draft with problems, which the editor shows before. */
    suspend fun create(draft: NoteDraft): Long = write {
        val cleaned = checked(draft)
        database().withTransaction { dao.insert(cleaned.copy(tabId = dao.tabStillThere(cleaned.tabId)).toNote(clock.instant())) }
    }

    /** Changes the note as [draft] has it, as [create] checks it; it is changed now. */
    suspend fun update(id: Long, draft: NoteDraft) = write {
        val cleaned = checked(draft)
        database().withTransaction {
            if (dao.get(id) == null) return@withTransaction
            dao.update(cleaned.copy(tabId = dao.tabStillThere(cleaned.tabId)).toNote(clock.instant(), id))
        }
    }

    /** Deletes the note for good. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    /** A new tab named [name] on a single line, last of all; refuses a name with a problem, which the screens show before. */
    suspend fun createTab(name: String): Long = write {
        database().withTransaction {
            val tabs = dao.tabs()
            dao.insertTab(NoteTab(name = checkedName(name, tabs), position = (tabs.maxOfOrNull { it.position } ?: -1) + 1))
        }
    }

    /** Renames the tab, as [createTab] names one. */
    suspend fun renameTab(id: Long, name: String) = write {
        database().withTransaction { dao.renameTab(id, checkedName(name, dao.tabs().filter { it.id != id })) }
    }

    /** Puts the tabs in the order of [ids]; those not among them follow, as they were. */
    suspend fun arrangeTabs(ids: List<Long>) = write {
        database().withTransaction {
            val tabs = dao.tabs().map { it.id }
            val order = ids.filter { it in tabs }.distinct() + tabs.filterNot { it in ids }
            order.forEachIndexed { position, id -> dao.placeTab(id, position) }
        }
    }

    /** Deletes the tab, its notes moving to "Allgemein": they are in no tab once theirs is gone. */
    suspend fun deleteTabMovingNotes(id: Long) = write { dao.deleteTab(id) }

    /** Deletes the tab with its notes, for good. */
    suspend fun deleteTabWithNotes(id: Long) = write {
        database().withTransaction {
            dao.deleteInTab(id)
            dao.deleteTab(id)
        }
    }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    private fun checked(draft: NoteDraft): NoteDraft {
        require(draft.problems.isEmpty()) { "Invalid note: ${draft.problems}" }
        return draft.cleaned()
    }

    private fun checkedName(name: String, others: List<NoteTab>): String {
        val problem = tabNameProblem(name, others)
        require(problem == null) { "Invalid tab name: $problem" }
        return InputText.singleLine(name)
    }

    private companion object {
        val lastChangedFirst = compareByDescending<Note> { it.changedAt }.thenBy(InputText.byName) { it.title }
    }
}
