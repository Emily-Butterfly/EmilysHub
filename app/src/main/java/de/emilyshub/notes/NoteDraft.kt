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
    /** What it is linked to; one deleted meanwhile is left out on saving. */
    val links: Set<NoteLink> = emptySet(),
    /** Its tags (SPEC.md §8); one deleted meanwhile is left out on saving. */
    val tagIds: Set<Long> = emptySet(),
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
    fun cleaned() = copy(title = InputText.singleLine(title), text = InputText.multiLine(text))

    /**
     * Without a title, the first line of its text that says anything as one (SPEC.md §8): without the marks of a
     * heading, list item, task or quote in front, cut to [TITLE_MAX] characters; the text stays as it is. With a title,
     * as it is.
     */
    fun withTitleFromText(): NoteDraft {
        if (InputText.isVisible(title)) return this
        val line = text.lineSequence()
            .map { InputText.singleLine(it).replace(LINE_MARKS, "").trim() }
            .firstOrNull(InputText::isVisible)
            ?: return this
        val cut = if (InputText.lengthOf(line) > TITLE_MAX) line.substring(0, line.offsetByCodePoints(0, TITLE_MAX)) else line
        return copy(title = cut.trim())
    }

    /**
     * The note this draft makes, in [container], at [position] there, [changedAt] then, [pinned] there and [onToday] or
     * not, as the draft has nothing of either; [id] if saved already.
     */
    fun toNote(container: Container, position: Int, changedAt: Instant, pinned: Boolean, onToday: Boolean, id: Long = 0) =
        Note(id, title, text, container.tabId, changedAt, container.folderId, position, pinned, onToday)

    companion object {
        const val TITLE_MAX = 80

        /** Some fifty pages, as a note is no book. */
        const val TEXT_MAX = 100_000

        /** The marks in front of a heading, a list item, a task or a quote, one after the other as in "- [ ] ". */
        private val LINE_MARKS = Regex("""^(\s*(#+|-|>|\[ ?]))+""")
    }
}

/** The note as the editor starts out with it, with what it is linked to, its [links], and its [tagIds]. */
fun Note.toDraft(links: Set<NoteLink>, tagIds: Set<Long>) = NoteDraft(title, text, Container(tabId, folderId), links, tagIds)
