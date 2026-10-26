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

/**
 * A tab of notes (SPEC.md §7), made by the user, at its [position] among the others, what lies at its top in the order
 * of its [sort]. "Allgemein", the tab every note is in that is in none of these, is no row: so it can be neither renamed
 * nor deleted.
 */
@Entity(tableName = "note_tabs")
data class NoteTab(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    @ColumnInfo(defaultValue = "'changed'") val sort: NoteSort = NoteSort.Changed,
) {
    companion object {
        /** As long as a tag's name, so that it fits on a tab. */
        const val NAME_MAX = 30

        /**
         * The names of the tabs the app has, as the screens show them, which no other can have; the app speaks German only
         * (SPEC.md §1).
         */
        val FIXED_NAMES = listOf("Alle", "Allgemein")
    }
}

/**
 * What keeps [name], on a single line, from naming a tab besides the [others]; none if it can. "Alle" and "Allgemein"
 * are taken always, by the tabs the app has, and two tabs read differently, as two tags do.
 */
fun tabNameProblem(name: String, others: Collection<NoteTab>): NameProblem? =
    InputText.nameProblem(name, NoteTab.NAME_MAX, others.map { it.name } + NoteTab.FIXED_NAMES)

/**
 * A note (SPEC.md §7): a [title] and its [text] in Markdown, in the tab [tabId], none: in "Allgemein", and there in the
 * folder [folderId], none: at the top of the tab. When it was [changedAt] last orders the notes, unless the folder is
 * sorted by name or by hand, by its [position] among the notes there.
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(entity = NoteTab::class, parentColumns = ["id"], childColumns = ["tab_id"], onDelete = ForeignKey.SET_NULL),
        // No action: what is in a folder is moved or deleted before the folder is.
        ForeignKey(entity = NoteFolder::class, parentColumns = ["id"], childColumns = ["folder_id"]),
    ],
    indices = [Index(value = ["tab_id"]), Index(value = ["folder_id"])],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    @ColumnInfo(name = "tab_id") val tabId: Long? = null,
    @ColumnInfo(name = "changed_at") val changedAt: Instant,
    @ColumnInfo(name = "folder_id") val folderId: Long? = null,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
)

/** Which notes a tab shows: all of them, those in "Allgemein", or those in a tab made by the user. */
sealed interface TabSelection : Serializable {
    data object All : TabSelection

    data object General : TabSelection

    data class Own(val tabId: Long) : TabSelection

    /** The top of the tab, where its folders and notes start; "Alle" has none, as it shows the notes only. */
    val top: Container?
        get() = when (this) {
            All -> null
            General -> Container()
            is Own -> Container(tabId)
        }
}
