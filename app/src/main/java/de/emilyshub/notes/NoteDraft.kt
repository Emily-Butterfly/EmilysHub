package de.emilyshub.notes

import de.emilyshub.data.InputText
import java.io.Serializable
import java.time.Instant

/** What keeps a [NoteDraft] from being saved, each shown at its field in the editor. */
enum class NoteProblem {
    TitleMissing,
    TitleTooLong,
    TextTooLong,
}

/**
 * A note as typed into the editor, before it is checked. The checks apply to the [cleaned] draft, and saving refuses a
 * draft with [problems] (SPEC.md §9: strict input checks). Serializable, so that the editor keeps it across rotation and
 * the app lock.
 */
data class NoteDraft(
    val title: String,
    val text: String = "",
    /** Where it lies; a tab or folder deleted meanwhile is left out on saving, for the top of the tab or "Allgemein". */
    val container: Container = Container(),
) : Serializable {

    val problems: Set<NoteProblem>
        get() = with(cleaned()) {
            buildSet {
                if (!InputText.isVisible(title)) add(NoteProblem.TitleMissing)
                if (InputText.lengthOf(title) > TITLE_MAX) add(NoteProblem.TitleTooLong)
                if (InputText.lengthOf(text) > TEXT_MAX) add(NoteProblem.TextTooLong)
            }
        }

    /** The title on a single line without surrounding spaces; the text without trailing ones, as Markdown keeps leading ones. */
    fun cleaned() = copy(title = InputText.singleLine(title), text = text.trimEnd())

    /** The note this draft makes, in [container], at [position] there, [changedAt] then, [id] if saved already. */
    fun toNote(container: Container, position: Int, changedAt: Instant, id: Long = 0) =
        Note(id, title, text, container.tabId, changedAt, container.folderId, position)

    companion object {
        const val TITLE_MAX = 80

        /** Some fifty pages, as a note is no book. */
        const val TEXT_MAX = 100_000
    }
}

/** The note as the editor starts out with it. */
fun Note.toDraft() = NoteDraft(title, text, Container(tabId, folderId))
