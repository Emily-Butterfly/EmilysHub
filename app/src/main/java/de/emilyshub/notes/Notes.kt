package de.emilyshub.notes

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
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
import kotlin.coroutines.CoroutineContext

/**
 * The notes, their tabs and their folders (SPEC.md §7). [database] is only called on [io], since the first call opens
 * it. Writes run to the end once started, even if the screen that started them goes away. What comes to lie somewhere
 * new comes last there; moving a note is no change of it.
 */
class Notes(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().notes()

    /** All tabs, folders and notes, read together anew as any of them changes. */
    fun notebook(): Flow<Notebook> = flow {
        val database = database()
        val changes = database.invalidationTracker.createFlow("note_tabs", "note_general_tab", "note_folders", "notes")
        emitAll(changes.map { database.withTransaction { read() } })
    }.flowOn(io)

    /** The note, or null once it is deleted. */
    suspend fun get(id: Long): Note? = withContext(io) { dao.get(id) }

    /** The tags of the note. */
    suspend fun tagIdsOf(id: Long): Set<Long> = withContext(io) { dao.tagIdsOf(id).toSet() }

    /** What the note is linked to. */
    suspend fun linksOf(id: Long): Set<NoteLink> = withContext(io) {
        (dao.goalIdsOf(id).map(NoteLink::Goal) + dao.todoIdsOf(id).map(NoteLink::Todo) + dao.routineIdsOf(id).map(NoteLink::Routine))
            .toSet()
    }

    /** The goals, to-dos and routines a note can be linked to, each by its name. */
    fun linkCandidates(): Flow<List<LinkCandidate>> = flow {
        val targets = combine(
            dao.observeGoalTargets(),
            dao.observeTodoTargets(),
            dao.observeRoutineTargets(),
        ) { goals, todos, routines ->
            goals.map { LinkCandidate(NoteLink.Goal(it.id), it.name, it.open) } +
                todos.map { LinkCandidate(NoteLink.Todo(it.id), it.name, it.open) } +
                routines.map { LinkCandidate(NoteLink.Routine(it.id), it.name, it.open) }
        }
        emitAll(targets)
    }.flowOn(io)

    /** The notes linked to [link], by title: the other side of the link (SPEC.md §7). */
    fun notesLinkedTo(link: NoteLink): Flow<List<Note>> = flow {
        val linked = when (link) {
            is NoteLink.Goal -> dao.observeNotesOfGoal(link.id)
            is NoteLink.Todo -> dao.observeNotesOfTodo(link.id)
            is NoteLink.Routine -> dao.observeNotesOfRoutine(link.id)
        }
        emitAll(linked.map { notes -> notes.sortedWith(compareBy(InputText.byName) { it.title }) })
    }.flowOn(io)

    /** The notes pinned on Heute, the one changed last first. */
    fun onToday(): Flow<List<Note>> = flow { emitAll(dao.observeOnToday()) }.flowOn(io)

    /** A new note, changed now, last where the draft has it; refuses a draft with problems, which the editor shows before. */
    suspend fun create(draft: NoteDraft): Long = write {
        val cleaned = checked(draft)
        database().withTransaction {
            val book = read()
            val into = book.stillThere(cleaned.container)
            val id = dao.insert(cleaned.toNote(into, book.endOfNotes(into), clock.instant(), pinned = false, onToday = false))
            saveLinks(id, cleaned.links)
            dao.tag(id, cleaned.tagIds)
            id
        }
    }

    /** Changes the note as [draft] has it, as [create] checks it; it is changed now, and last where it is moved to. */
    suspend fun update(id: Long, draft: NoteDraft) = write {
        val cleaned = checked(draft)
        database().withTransaction {
            val note = dao.get(id) ?: return@withTransaction
            val book = read()
            val into = book.stillThere(cleaned.container)
            val position = if (into == book.containerOf(note)) note.position else book.endOfNotes(into)
            dao.update(cleaned.toNote(into, position, clock.instant(), note.pinned, note.onToday, id))
            saveLinks(id, cleaned.links)
            dao.untagAll(id)
            dao.tag(id, cleaned.tagIds)
        }
    }

    /** Deletes the note for good, and its links. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    /** Pins the note in what it lies in, or [pinned] not, no more. */
    suspend fun pinNote(id: Long, pinned: Boolean) = write { dao.pinNote(id, pinned) }

    /** Pins the note on Heute, or [pinned] not, no more. */
    suspend fun pinOnToday(id: Long, pinned: Boolean) = write { dao.pinOnToday(id, pinned) }

    /** Moves the note to the end of [to], or of the top of its tab if that folder is gone. */
    suspend fun moveNote(id: Long, to: Container) = write {
        database().withTransaction {
            val note = dao.get(id) ?: return@withTransaction
            val book = read()
            val into = book.stillThere(to)
            if (into != book.containerOf(note)) dao.moveNote(id, into.tabId, into.folderId, book.endOfNotes(into))
        }
    }

    /** Puts the notes in [container] in the order of [ids]; those not among them follow, as they were. */
    suspend fun arrangeNotes(container: Container, ids: List<Long>) = write {
        database().withTransaction {
            ordered(ids, read().notesIn(container).map { it.id }).forEachIndexed { position, id -> dao.placeNote(id, position) }
        }
    }

    /** A new folder named [name] on a single line, last in [into]; refuses a name with a problem, which the screens show before. */
    suspend fun createFolder(name: String, into: Container): Long = write {
        val checked = checkedFolderName(name)
        database().withTransaction {
            val book = read()
            val container = book.stillThere(into)
            dao.insertFolder(
                NoteFolder(
                    name = checked,
                    tabId = container.tabId,
                    parentId = container.folderId,
                    position = book.endOfFolders(container),
                    changedAt = clock.instant(),
                ),
            )
        }
    }

    /** Pins the folder in what it lies in, or [pinned] not, no more. */
    suspend fun pinFolder(id: Long, pinned: Boolean) = write { dao.pinFolder(id, pinned) }

    /** Renames the folder, as [createFolder] names one. */
    suspend fun renameFolder(id: Long, name: String) = write { dao.renameFolder(id, checkedFolderName(name), clock.instant()) }

    /**
     * Moves the folder with all that is in it to the end of [to], or of the top of its tab if that folder is gone, also to
     * another tab; refuses to move it into itself or a folder in it, which the screens do not offer.
     */
    suspend fun moveFolder(id: Long, to: Container) = write {
        database().withTransaction {
            val book = read()
            val folder = book.folder(id) ?: return@withTransaction
            val into = book.stillThere(to)
            val moving = book.inAndBelow(id)
            require(into.folderId == null || into.folderId !in moving) { "A folder cannot go into itself" }
            if (into == book.containerOf(folder)) return@withTransaction
            dao.moveFolder(id, into.tabId, into.folderId, book.endOfFolders(into))
            if (into.tabId != folder.tabId) {
                moving.chunked(CHUNK).forEach { ids ->
                    dao.putFoldersInTab(ids, into.tabId)
                    dao.putNotesInTab(ids, into.tabId)
                }
            }
        }
    }

    /** Puts the folders in [container] in the order of [ids]; those not among them follow, as they were. */
    suspend fun arrangeFolders(container: Container, ids: List<Long>) = write {
        database().withTransaction {
            ordered(ids, read().foldersIn(container).map { it.id }).forEachIndexed { position, id -> dao.placeFolder(id, position) }
        }
    }

    /**
     * Orders what lies in [container] by [sort]. By hand, it is in the order last put by hand, what came there since
     * after it, as it came; so that sorting otherwise for a while loses nothing.
     */
    suspend fun sort(container: Container, sort: NoteSort) = write {
        when {
            container.folderId != null -> dao.sortFolder(container.folderId, sort)
            container.tabId != null -> dao.sortTab(container.tabId, sort)
            else -> dao.putGeneralTab(GeneralTab(sort = sort))
        }
    }

    /** Deletes the folder, what is in it moving up a level, to the end there. */
    suspend fun deleteFolderMovingUp(id: Long) = write {
        database().withTransaction {
            val book = read()
            val folder = book.folder(id) ?: return@withTransaction
            val up = book.containerOf(folder)
            val inside = folder.inside
            val firstFolder = book.endOfFolders(up)
            book.foldersIn(inside).forEachIndexed { index, it -> dao.moveFolder(it.id, up.tabId, up.folderId, firstFolder + index) }
            val firstNote = book.endOfNotes(up)
            book.notesIn(inside).forEachIndexed { index, it -> dao.moveNote(it.id, up.tabId, up.folderId, firstNote + index) }
            dao.deleteFolders(listOf(id))
        }
    }

    /** Deletes the folder with all that is in it, at any depth, for good. */
    suspend fun deleteFolderWithContents(id: Long) = write {
        database().withTransaction {
            val ids = read().inAndBelow(id).toList()
            ids.chunked(CHUNK).forEach { dao.deleteNotesIn(it) }
            ids.chunked(CHUNK).forEach { dao.detachChildrenOf(it) }
            ids.chunked(CHUNK).forEach { dao.deleteFolders(it) }
        }
    }

    /** A new tab named [name] on a single line, last of all; refuses a name with a problem, which the screens show before. */
    suspend fun createTab(name: String): Long = write {
        database().withTransaction {
            val tabs = dao.tabs()
            dao.insertTab(NoteTab(name = checkedTabName(name, tabs), position = (tabs.maxOfOrNull { it.position } ?: -1) + 1))
        }
    }

    /** Renames the tab, as [createTab] names one. */
    suspend fun renameTab(id: Long, name: String) = write {
        database().withTransaction { dao.renameTab(id, checkedTabName(name, dao.tabs().filter { it.id != id })) }
    }

    /** Puts the tabs in the order of [ids]; those not among them follow, as they were. */
    suspend fun arrangeTabs(ids: List<Long>) = write {
        database().withTransaction {
            ordered(ids, dao.tabs().map { it.id }).forEachIndexed { position, id -> dao.placeTab(id, position) }
        }
    }

    /**
     * Deletes the tab, its folders and notes moving to "Allgemein", after what lies at its top: they are in no tab once
     * theirs is gone.
     */
    suspend fun deleteTabMovingContents(id: Long) = write {
        database().withTransaction {
            val book = read()
            val top = Container(id)
            val firstFolder = book.endOfFolders(Container())
            book.foldersIn(top).forEachIndexed { index, folder -> dao.placeFolder(folder.id, firstFolder + index) }
            val firstNote = book.endOfNotes(Container())
            book.notesIn(top).forEachIndexed { index, note -> dao.placeNote(note.id, firstNote + index) }
            dao.deleteTab(id)
        }
    }

    /** Deletes the tab with its folders and notes, for good. */
    suspend fun deleteTabWithContents(id: Long) = write {
        database().withTransaction {
            dao.deleteNotesInTab(id)
            dao.deleteFoldersInTab(id)
            dao.deleteTab(id)
        }
    }

    /** Links the note to what [links] has, and to nothing else; one deleted meanwhile is left out. */
    private suspend fun saveLinks(noteId: Long, links: Set<NoteLink>) {
        dao.unlinkGoals(noteId)
        dao.unlinkTodos(noteId)
        dao.unlinkRoutines(noteId)
        dao.linkGoals(noteId, links.filterIsInstance<NoteLink.Goal>().map { it.id })
        // A to-do come again since the editor opened is linked as it is now, too, as the next has the links of the one before.
        dao.linkTodos(noteId, links.filterIsInstance<NoteLink.Todo>().flatMap { series(it.id) })
        dao.linkRoutines(noteId, links.filterIsInstance<NoteLink.Routine>().map { it.id })
    }

    /** The to-do [id] and those that came again after it, one after the other. */
    private suspend fun series(id: Long): List<Long> {
        val ids = mutableListOf(id)
        while (true) ids += dao.nextOf(ids.last()) ?: return ids
    }

    private suspend fun read() = Notebook(
        tabs = dao.tabs(),
        folders = dao.folders(),
        notes = dao.notes(),
        generalSort = dao.generalTab()?.sort ?: NoteSort.Changed,
    )

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    private fun checked(draft: NoteDraft): NoteDraft {
        require(draft.problems.isEmpty()) { "Invalid note: ${draft.problems}" }
        return draft.cleaned()
    }

    private fun checkedTabName(name: String, others: List<NoteTab>): String {
        val problem = tabNameProblem(name, others)
        require(problem == null) { "Invalid tab name: $problem" }
        return InputText.singleLine(name)
    }

    private fun checkedFolderName(name: String): String {
        val problem = folderNameProblem(name)
        require(problem == null) { "Invalid folder name: $problem" }
        return InputText.singleLine(name)
    }

    private companion object {
        /** At most so many ids in one query, well below what SQLite takes. */
        const val CHUNK = 500

        /** [ids] that are among [all], in their order, and then the others of [all], as they were. */
        fun ordered(ids: List<Long>, all: List<Long>): List<Long> = ids.filter { it in all }.distinct() + all.filterNot { it in ids }
    }
}
