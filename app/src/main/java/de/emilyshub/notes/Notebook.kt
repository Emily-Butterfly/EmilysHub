package de.emilyshub.notes

import de.emilyshub.data.InputText
import java.time.Instant

/**
 * The tabs, folders and notes as the screens show them (SPEC.md §7): what lies in a folder or at the top of a tab, its
 * folders first, then its notes, each in the order it is sorted by; the path down to a folder; and all notes, the one
 * changed last first. A folder or note whose folder is in another tab or gone, or that hangs in a loop, as a backup
 * could have them, lies at the top of its tab: so that all can be reached.
 */
class Notebook(
    val tabs: List<NoteTab>,
    folders: List<NoteFolder>,
    private val notes: List<Note>,
    /** How the top of "Allgemein" is sorted, which has no row of its own to keep it. */
    private val generalSort: NoteSort,
) {
    private val folders = folders.associateBy { it.id }

    /** What each folder lies in, as shown. */
    private val parents: Map<Long, Container> = this.folders.values.associate { it.id to Container(it.tabId, shownParentOf(it)) }

    private val foldersByContainer = this.folders.values.groupBy { parents.getValue(it.id) }
    private val notesByContainer = notes.groupBy(::containerOf)
    private val lastChanges = mutableMapOf<Long, Instant>()

    fun folder(id: Long): NoteFolder? = folders[id]

    /** What the folder lies in, as shown. */
    fun containerOf(folder: NoteFolder): Container = parents.getValue(folder.id)

    /** What the note lies in, as shown. */
    fun containerOf(note: Note): Container =
        Container(note.tabId, note.folderId?.takeIf { folders[it]?.tabId == note.tabId })

    fun sortOf(container: Container): NoteSort = when {
        container.folderId != null -> folders[container.folderId]?.sort ?: NoteSort.Changed
        container.tabId == null -> generalSort
        else -> tabs.find { it.id == container.tabId }?.sort ?: NoteSort.Changed
    }

    /** The folders in [container], in its order. */
    fun foldersIn(container: Container): List<NoteFolder> {
        val inThere = foldersByContainer[container].orEmpty()
        return when (sortOf(container)) {
            NoteSort.Name -> inThere.sortedWith(compareBy(InputText.byName) { it: NoteFolder -> it.name }.thenBy { it.id })
            NoteSort.Changed -> inThere.sortedWith(compareByDescending(::lastChange).thenBy(InputText.byName) { it.name })
            NoteSort.Manual -> inThere.sortedWith(compareBy<NoteFolder> { it.position }.thenBy { it.id })
        }
    }

    /** The notes in [container], in its order. */
    fun notesIn(container: Container): List<Note> {
        val inThere = notesByContainer[container].orEmpty()
        return when (sortOf(container)) {
            NoteSort.Name -> inThere.sortedWith(compareBy(InputText.byName) { it: Note -> it.title }.thenBy { it.id })
            NoteSort.Changed -> inThere.sortedWith(lastChangedFirst)
            NoteSort.Manual -> inThere.sortedWith(compareBy<Note> { it.position }.thenBy { it.id })
        }
    }

    /** The folders from the top of the tab down to the folder [id], it last; none for the top itself. */
    fun path(id: Long?): List<NoteFolder> = generateSequence(id?.let(folders::get)) { folder ->
        parents.getValue(folder.id).folderId?.let(folders::getValue)
    }.toList().asReversed()

    /** The folder [id] and all folders in it, at any depth. */
    fun inAndBelow(id: Long): Set<Long> {
        val found = mutableSetOf<Long>()
        val next = ArrayDeque(listOfNotNull(folders[id]))
        while (next.isNotEmpty()) {
            val folder = next.removeFirst()
            if (found.add(folder.id)) next += foldersByContainer[Container(folder.tabId, folder.id)].orEmpty()
        }
        return found
    }

    /** All notes of all tabs, the one changed last first. */
    fun all(): List<Note> = notes.sortedWith(lastChangedFirst)

    /** When the folder or anything in it, at any depth, was changed last. */
    private fun lastChange(folder: NoteFolder): Instant = lastChanges.getOrPut(folder.id) {
        val inside = Container(folder.tabId, folder.id)
        val changes = notesByContainer[inside].orEmpty().map { it.changedAt } +
            foldersByContainer[inside].orEmpty().map(::lastChange)
        changes.fold(folder.changedAt, ::maxOf)
    }

    /** The folder's parent, unless that is in another tab or gone, or the folder is above it too, in a loop: then none. */
    private fun shownParentOf(folder: NoteFolder): Long? {
        val parent = folder.parentId?.let(folders::get)?.takeIf { it.tabId == folder.tabId } ?: return null
        val seen = mutableSetOf<Long>()
        var up: NoteFolder? = parent
        while (up != null && seen.add(up.id)) {
            if (up.id == folder.id) return null
            up = up.parentId?.let(folders::get)?.takeIf { it.tabId == folder.tabId }
        }
        return parent.id
    }

    private companion object {
        val lastChangedFirst = compareByDescending<Note> { it.changedAt }.thenBy(InputText.byName) { it.title }
    }
}
