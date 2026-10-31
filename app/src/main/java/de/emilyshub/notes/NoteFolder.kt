package de.emilyshub.notes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem
import java.io.Serializable
import java.time.Instant

/** How what lies in a folder is ordered (SPEC.md §7): by name, the one changed last first, or as put in order by hand. */
enum class NoteSort(val stored: String) { Name("name"), Changed("changed"), Manual("manual") }

/**
 * A folder of notes (SPEC.md §7), in the tab [tabId], none: in "Allgemein", and there in the folder [parentId], none: at
 * the top of the tab, to any depth. What lies in it is in the order of its [sort]; by hand, by the [position] of each
 * there. It was [changedAt] when made or renamed last. [pinned], it comes first there, before all notes.
 */
@Entity(
    tableName = "note_folders",
    foreignKeys = [
        ForeignKey(entity = NoteTab::class, parentColumns = ["id"], childColumns = ["tab_id"], onDelete = ForeignKey.SET_NULL),
        // No action: what is in a folder is moved or deleted before the folder is.
        ForeignKey(entity = NoteFolder::class, parentColumns = ["id"], childColumns = ["parent_id"]),
    ],
    indices = [Index(value = ["tab_id"]), Index(value = ["parent_id"])],
)
data class NoteFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "tab_id") val tabId: Long?,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    val position: Int,
    val sort: NoteSort = NoteSort.Changed,
    @ColumnInfo(name = "changed_at") val changedAt: Instant,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
) {
    /** What lies in this folder. */
    val inside: Container get() = Container(tabId, id)

    companion object {
        /** Long enough for a name of a few words, short enough for a path of several of them. */
        const val NAME_MAX = 50
    }
}

/**
 * What a folder or note lies in: the top of the tab [tabId], none: of "Allgemein", or the folder [folderId] in it.
 * Serializable, so that the screens keep the folder open across rotation and the lock.
 */
data class Container(val tabId: Long? = null, val folderId: Long? = null) : Serializable

/**
 * How the top of "Allgemein" is sorted, as a tab of the user's keeps it in its row: "Allgemein" has none among them. A
 * single row, [ID], once sorted otherwise than by last change.
 */
@Entity(tableName = "note_general_tab")
data class GeneralTab(@PrimaryKey val id: Int = ID, val sort: NoteSort) {
    companion object {
        const val ID = 1
    }
}

/** What keeps [name], on a single line, from naming a folder; none if it can. Two folders may have the same name. */
fun folderNameProblem(name: String): NameProblem? = InputText.nameProblem(name, NoteFolder.NAME_MAX, taken = emptyList())

/**
 * Whether [folders] and [notes] hold together as the app keeps them, as a backup is checked strictly (SPEC.md §10):
 * each in the tab of the folder it is in, and no folder within itself, however deep.
 */
fun notesHoldTogether(folders: List<NoteFolder>, notes: List<Note>): Boolean {
    val byId = folders.associateBy { it.id }
    fun inTabOf(folderId: Long?, tabId: Long?) = folderId == null || byId[folderId]?.tabId == tabId
    if (!folders.all { inTabOf(it.parentId, it.tabId) } || !notes.all { inTabOf(it.folderId, it.tabId) }) return false
    // Up from each folder, past those known to reach the top already: coming upon one again on the way is a loop.
    val reachTop = mutableSetOf<Long>()
    for (folder in folders) {
        val way = mutableSetOf<Long>()
        var up: NoteFolder? = folder
        while (up != null && up.id !in reachTop) {
            if (!way.add(up.id)) return false
            up = up.parentId?.let(byId::get)
        }
        reachTop += way
    }
    return true
}

/**
 * Whether [tabs], [folders] and [notes] keep the rules the editors keep them to, as a backup is checked strictly (SPEC.md
 * §9, §10): each name and title there and not too long, no tab named as one the app has nor as another, no text too
 * long.
 */
fun notesKeepTheRules(tabs: List<NoteTab>, folders: List<NoteFolder>, notes: List<Note>): Boolean =
    tabs.all { tab -> tabNameProblem(tab.name, tabs - tab) == null } &&
        folders.all { folderNameProblem(it.name) == null } &&
        notes.all { NoteDraft(it.title, it.text).problems.isEmpty() }
