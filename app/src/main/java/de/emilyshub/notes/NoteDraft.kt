package de.emilyshub.notes

import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem
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
    /** The tab it is in; none: "Allgemein". One deleted meanwhile is left out on saving. */
    val tabId: Long? = null,
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

    /** The note this draft makes, [changedAt] then, [id] if saved already. */
    fun toNote(changedAt: Instant, id: Long = 0) = Note(id, title, text, tabId, changedAt)

    companion object {
        const val TITLE_MAX = 80

        /** Some fifty pages, as a note is no book. */
        const val TEXT_MAX = 100_000
    }
}

/** The note as the editor starts out with it. */
fun Note.toDraft() = NoteDraft(title, text, tabId)

/**
 * What keeps [name], on a single line, from naming a tab besides the [others]; none if it can. "Alle" and "Allgemein"
 * are taken always, by the tabs the app has, and two tabs read differently, as two tags do.
 */
fun tabNameProblem(name: String, others: Collection<NoteTab>): NameProblem? =
    InputText.nameProblem(name, NoteTab.NAME_MAX, others.map { it.name } + NoteTab.FIXED_NAMES)
