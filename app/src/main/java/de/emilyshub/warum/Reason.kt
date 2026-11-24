package de.emilyshub.warum

import androidx.room.Entity
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import java.io.Serializable

/**
 * A Grund in Mein Warum (SPEC.md §18): its [text], plain, and its [position] on the board, the lowest first. Nothing of
 * when it was made or looked at is kept.
 */
@Entity(tableName = "reasons")
data class Reason(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val position: Int,
)

/** What keeps a [ReasonDraft] from being saved. */
enum class ReasonProblem {
    /** Nothing to see. */
    Empty,

    /** More than [ReasonDraft.TEXT_MAX] characters. */
    TextTooLong,

    /** The reason edited was deleted meanwhile. */
    Gone,
}

/**
 * A reason as typed into its editor, before it is checked (SPEC.md §9: strict input checks); saved [cleaned].
 * Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class ReasonDraft(val text: String = "") : Serializable {

    val problems: Set<ReasonProblem>
        get() = with(cleaned()) {
            buildSet {
                if (!InputText.isVisible(text)) add(ReasonProblem.Empty)
                if (InputText.lengthOf(text) > TEXT_MAX) add(ReasonProblem.TextTooLong)
            }
        }

    /** On its lines, without spaces or empty lines at its edges: it stands in the middle of its tile. */
    fun cleaned() = copy(text = InputText.multiLine(text).trim())

    companion object {
        /** The longest text of a reason, in characters (SPEC.md §18). */
        const val TEXT_MAX = 500
    }
}
