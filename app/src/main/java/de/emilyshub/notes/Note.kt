package de.emilyshub.notes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A tab of notes (SPEC.md §7), made by the user, at its [position] among the others. "Allgemein", the tab every note is
 * in that is in none of these, is no row: so it can be neither renamed nor deleted.
 */
@Entity(tableName = "note_tabs")
data class NoteTab(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
) {
    companion object {
        /** As long as a tag's name, so that it fits on a tab. */
        const val NAME_MAX = 30

        /** The names of the tabs the app has, which no other can have; the app speaks German only (SPEC.md §1). */
        val FIXED_NAMES = listOf("Alle", "Allgemein")
    }
}

/**
 * A note (SPEC.md §7): a [title] and its [text] in Markdown, in the tab [tabId], none: in "Allgemein". When it was
 * [changedAt] last orders the notes.
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(entity = NoteTab::class, parentColumns = ["id"], childColumns = ["tab_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["tab_id"])],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    @ColumnInfo(name = "tab_id") val tabId: Long? = null,
    @ColumnInfo(name = "changed_at") val changedAt: Instant,
)

/** Which notes a tab shows: all of them, those in "Allgemein", or those in a tab made by the user. */
sealed interface TabSelection : java.io.Serializable {
    data object All : TabSelection

    data object General : TabSelection

    data class Own(val tabId: Long) : TabSelection

    /** Whether the note [inTab] is shown here. */
    fun shows(inTab: Long?): Boolean = when (this) {
        All -> true
        General -> inTab == null
        is Own -> inTab == tabId
    }
}
