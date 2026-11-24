package de.emilyshub.warum

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import java.io.Serializable

/**
 * A Grund in Mein Warum (SPEC.md §18): its [text], plain, perhaps empty beside a [photo], and its [position] on the
 * board, the lowest first. The photo is kept encrypted in the [de.emilyshub.languages.AttachmentStore] under that name,
 * as the lessons' attachments are. Nothing of when it was made or looked at is kept.
 */
@Entity(tableName = "reasons", indices = [Index(value = ["photo"], unique = true)])
data class Reason(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val position: Int,
    val photo: String? = null,
)

/** What keeps a [ReasonDraft] from being saved. */
enum class ReasonProblem {
    /** Neither a text nor a photo. */
    Empty,

    /** More than [ReasonDraft.TEXT_MAX] characters. */
    TextTooLong,

    /** The reason edited was deleted meanwhile. */
    Gone,

    /** [Reasons.MAX_PHOTOS] other reasons have a photo already. */
    TooManyPhotos,

    /** Its photo is no longer there to be taken in, or is another's. */
    PhotoGone,
}

/**
 * A reason as typed into its editor, before it is checked (SPEC.md §9: strict input checks); saved [cleaned].
 * Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class ReasonDraft(
    val text: String = "",
    /** The name its photo is kept under, saved already or imported for it; none: it has none. */
    val photo: String? = null,
) : Serializable {

    val problems: Set<ReasonProblem>
        get() = with(cleaned()) {
            buildSet {
                if (!InputText.isVisible(text) && photo == null) add(ReasonProblem.Empty)
                if (InputText.lengthOf(text) > TEXT_MAX) add(ReasonProblem.TextTooLong)
            }
        }

    /** On its lines, without spaces or empty lines at its edges: it stands in the middle on the board. */
    fun cleaned() = copy(text = InputText.multiLine(text).trim())

    companion object {
        /** The longest text of a reason, in characters (SPEC.md §18). */
        const val TEXT_MAX = 500
    }
}
