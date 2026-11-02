package de.emilyshub.notes

import de.emilyshub.data.InputText
import java.time.Instant

/**
 * The tabs, folders and notes as the screens show them (SPEC.md §7): what lies in a folder or at the top of a tab, the
 * folders pinned first, then the notes pinned, the other folders and the other notes, each in the order it is sorted
 * by; the path down to a folder; and all notes, the one changed last first. The folders and notes hold together as
 * [notesHoldTogether] checks it, as the app keeps them and a backup is refused otherwise. What is in the archive (SPEC.md
 * §7) shows nowhere but in it: archived itself, or in a folder archived, at any depth.
 */
class Notebook(
    val tabs: List<NoteTab>,
    folders: List<NoteFolder>,
    private val notes: List<Note>,
    /** How the top of "Allgemein" is sorted, which has no row among the [tabs]. */
    private val generalSort: NoteSort,
) {
    private val folders = folders.associateBy { it.id }
    private val foldersByContainer = folders.groupBy { containerOf(it) }
    private val notesByContainer = notes.groupBy { containerOf(it) }
    private val lastChanges = mutableMapOf<Long, Instant>()

    /** The folders in the archive: archived themselves, or in one archived. */
    private val archivedFolders: Set<Long> = buildSet {
        fun archive(folder: NoteFolder) {
            if (add(folder.id)) foldersByContainer[folder.inside].orEmpty().forEach(::archive)
        }
        folders.filter { it.archivedAt != null }.forEach(::archive)
    }

    fun folder(id: Long): NoteFolder? = folders[id]

    fun note(id: Long): Note? = notes.find { it.id == id }

    fun containerOf(folder: NoteFolder): Container = Container(folder.tabId, folder.parentId)

    fun containerOf(note: Note): Container = Container(note.tabId, note.folderId)

    fun sortOf(container: Container): NoteSort = when {
        container.folderId != null -> folders[container.folderId]?.sort ?: NoteSort.Changed
        container.tabId == null -> generalSort
        else -> tabs.find { it.id == container.tabId }?.sort ?: NoteSort.Changed
    }

    /** The folders in [container], in its order, those pinned first; those in the archive too only [withArchived]. */
    fun foldersIn(container: Container, withArchived: Boolean = false): List<NoteFolder> = foldersByContainer[container].orEmpty()
        .filter { withArchived || it.archivedAt == null }
        .inOrderOf(sortOf(container), NoteFolder::name, ::lastChange, NoteFolder::position, NoteFolder::id)
        .sortedBy { !it.pinned }

    /** The notes in [container], in its order, those pinned first; those in the archive too only [withArchived]. */
    fun notesIn(container: Container, withArchived: Boolean = false): List<Note> = notesByContainer[container].orEmpty()
        .filter { withArchived || it.archivedAt == null }
        .inOrderOf(sortOf(container), Note::title, Note::changedAt, Note::position, Note::id)
        .sortedBy { !it.pinned }

    /**
     * What lies in [container] as shown: the folders pinned, the notes pinned, then the other folders and notes; in a
     * folder in the archive, all of it, [withArchived].
     */
    fun entriesIn(container: Container, withArchived: Boolean = false): List<NoteEntry> {
        val (pinnedFolders, folders) = foldersIn(container, withArchived).partition { it.pinned }
        val (pinnedNotes, notes) = notesIn(container, withArchived).partition { it.pinned }
        return pinnedFolders.map(NoteEntry::OfFolder) + pinnedNotes.map(NoteEntry::OfNote) +
            folders.map(NoteEntry::OfFolder) + notes.map(NoteEntry::OfNote)
    }

    /** Whether nothing lies in [container] but what is in the archive, neither folder nor note. */
    fun isEmpty(container: Container): Boolean = foldersIn(container).isEmpty() && notesIn(container).isEmpty()

    /** Whether the folder is in the archive: archived itself, or in a folder archived. */
    fun isArchived(folder: NoteFolder): Boolean = folder.id in archivedFolders

    /** Whether the note is in the archive: archived itself, or in a folder archived. */
    fun isArchived(note: Note): Boolean = note.archivedAt != null || note.folderId in archivedFolders

    /**
     * What was archived, the last first (SPEC.md §7): each folder and note archived but for those in a folder archived,
     * which are found in it.
     */
    fun archived(): List<NoteEntry> {
        val folders = folders.values.filter { it.archivedAt != null && it.parentId !in archivedFolders }
            .map { NoteEntry.OfFolder(it) to checkNotNull(it.archivedAt) }
        val notes = notes.filter { it.archivedAt != null && it.folderId !in archivedFolders }
            .map { NoteEntry.OfNote(it) to checkNotNull(it.archivedAt) }
        return (folders + notes).sortedByDescending { it.second }.map { it.first }
    }

    /**
     * Where what was archived from [container] comes back to: there, or the nearest folder above it not in the archive,
     * or the top of its tab, or of "Allgemein" if the tab is gone.
     */
    fun restoringTo(container: Container): Container {
        val there = stillThere(container)
        val folder = generateSequence(there.folderId?.let(folders::get)) { it.parentId?.let(folders::get) }
            .firstOrNull { it.id !in archivedFolders }
        return Container(there.tabId, folder?.id)
    }

    /** The folders from the top of the tab down to the folder [id], it last; none for the top itself. */
    fun path(id: Long?): List<NoteFolder> =
        generateSequence(id?.let(folders::get)) { folder -> folder.parentId?.let(folders::getValue) }.toList().asReversed()

    /** The folder [id] and all folders in it, at any depth. */
    fun inAndBelow(id: Long): Set<Long> {
        val found = mutableSetOf<Long>()
        val next = ArrayDeque(listOfNotNull(folders[id]))
        while (next.isNotEmpty()) {
            val folder = next.removeFirst()
            if (found.add(folder.id)) next += foldersByContainer[folder.inside].orEmpty()
        }
        return found
    }

    /** The [folders] not archived and all folders in them not archived, at any depth: not into those archived. */
    fun liveInAndBelow(folders: List<NoteFolder>): Set<Long> {
        val found = mutableSetOf<Long>()
        val next = ArrayDeque(folders.filter { it.archivedAt == null })
        while (next.isNotEmpty()) {
            val folder = next.removeFirst()
            if (found.add(folder.id)) next += foldersByContainer[folder.inside].orEmpty().filter { it.archivedAt == null }
        }
        return found
    }

    /** All notes of all tabs not in the archive, those pinned first, each the one changed last first. */
    fun all(): List<Note> = notes.filterNot(::isArchived).sortedWith(lastChangedFirst).sortedBy { !it.pinned }

    /** [container], or the top of its tab if the folder is gone or no longer in it, or of "Allgemein" if the tab is gone. */
    fun stillThere(container: Container): Container {
        val tabId = container.tabId?.takeIf { id -> tabs.any { it.id == id } }
        // A folder of a tab gone is in "Allgemein" now: what is to go in it still does.
        val folderId = container.folderId?.takeIf { folder(it)?.tabId == tabId }
        return Container(tabId, folderId)
    }

    /** The position after all folders in [container], for one to come last there. */
    fun endOfFolders(container: Container): Int = (foldersByContainer[container].orEmpty().maxOfOrNull { it.position } ?: -1) + 1

    /** The position after all notes in [container], for one to come last there. */
    fun endOfNotes(container: Container): Int = (notesByContainer[container].orEmpty().maxOfOrNull { it.position } ?: -1) + 1

    /** When the folder or anything in it, at any depth, was changed last. */
    private fun lastChange(folder: NoteFolder): Instant = lastChanges.getOrPut(folder.id) {
        val changes = notesByContainer[folder.inside].orEmpty().map { it.changedAt } +
            foldersByContainer[folder.inside].orEmpty().map(::lastChange)
        changes.fold(folder.changedAt, ::maxOf)
    }

    private companion object {
        val lastChangedFirst = compareByDescending<Note> { it.changedAt }.thenBy(InputText.byName) { it.title }

        /** In the order of [sort]: by [name], by when [changed] last, the last first, or by [position]; then by [id]. */
        fun <T> List<T>.inOrderOf(
            sort: NoteSort,
            name: (T) -> String,
            changed: (T) -> Instant,
            position: (T) -> Int,
            id: (T) -> Long,
        ): List<T> = sortedWith(
            when (sort) {
                NoteSort.Name -> compareBy(InputText.byName, name)
                NoteSort.Changed -> compareByDescending(changed).thenBy(InputText.byName, name)
                NoteSort.Manual -> compareBy(position)
            }.thenBy(id),
        )
    }
}

/** A folder or a note, as [Notebook.entriesIn] has them. */
sealed interface NoteEntry {
    data class OfFolder(val folder: NoteFolder) : NoteEntry

    data class OfNote(val note: Note) : NoteEntry
}
