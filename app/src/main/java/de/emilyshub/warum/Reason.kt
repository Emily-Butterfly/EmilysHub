package de.emilyshub.warum

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import java.io.Serializable

/**
 * A Grund in Mein Warum (SPEC.md §18): its [text], plain, perhaps empty beside a [photo], and its [position] on the
 * board, the lowest first; how it looks, as [look] reads it from its [font], [size], [background] and [textColor] kept
 * as they were chosen, so that one the app does not know counts as Standard. The photo is kept encrypted in the Ablage ([de.emilyshub.vault.Vault]) under that name,
 * as the lessons' attachments are. Nothing of when it was made or looked at is kept.
 */
@Entity(tableName = "reasons", indices = [Index(value = ["photo"], unique = true)])
data class Reason(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val position: Int,
    val photo: String? = null,
    @ColumnInfo(defaultValue = "'standard'") val font: String = ReasonFont.Standard.stored,
    @ColumnInfo(defaultValue = "'medium'") val size: String = ReasonSize.Medium.stored,
    @ColumnInfo(defaultValue = "'standard'") val background: String = ReasonColor.Standard.stored,
    @ColumnInfo(name = "text_color", defaultValue = "'standard'") val textColor: String = ReasonColor.Standard.stored,
) {
    /** How it looks. */
    val look: ReasonLook get() = ReasonLook.of(font, size, background, textColor)

    /** With [look] as it looks. */
    fun lookingAs(look: ReasonLook) =
        copy(font = look.font.stored, size = look.size.stored, background = look.background.stored, textColor = look.text.stored)
}

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
    val look: ReasonLook = ReasonLook(),
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
