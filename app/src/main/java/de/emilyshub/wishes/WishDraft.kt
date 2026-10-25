package de.emilyshub.wishes

import de.emilyshub.data.InputText
import java.io.Serializable
import java.time.LocalDate

/** What keeps a [WishDraft] from being saved, each shown at its field in the editor. */
enum class WishProblem {
    TitleMissing,
    TitleTooLong,
    DescriptionTooLong,
    PeriodTooLong,
}

/**
 * A wish as typed into the editor, before it is checked. The checks apply to the [cleaned] draft, and saving refuses a
 * draft with [problems] (SPEC.md §9: strict input checks). The day it came true counts only once it is done, today if
 * none is given. Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class WishDraft(
    val title: String,
    val description: String = "",
    /** When, in the user's words ("2027", "Sommer"): no date, so nothing reminds of it. */
    val period: String = "",
    val status: WishStatus = WishStatus.Open,
    val doneOn: LocalDate? = null,
    /** Its tags; one deleted meanwhile is left out on saving. */
    val tagIds: Set<Long> = emptySet(),
) : Serializable {

    val problems: Set<WishProblem>
        get() = with(cleaned()) {
            buildSet {
                if (!InputText.isVisible(title)) add(WishProblem.TitleMissing)
                if (InputText.lengthOf(title) > TITLE_MAX) add(WishProblem.TitleTooLong)
                if (InputText.lengthOf(description) > DESCRIPTION_MAX) add(WishProblem.DescriptionTooLong)
                if (InputText.lengthOf(period) > PERIOD_MAX) add(WishProblem.PeriodTooLong)
            }
        }

    /** Without surrounding spaces; the title and the period on a single line. */
    fun cleaned() = copy(title = InputText.singleLine(title), description = description.trim(), period = InputText.singleLine(period))

    /** The wish this draft makes, [id] if saved already, done on [doneOn] if it is done. */
    fun toWish(id: Long = 0, doneOn: LocalDate?) = Wish(id, title, description, period, status, doneOn)

    companion object {
        const val TITLE_MAX = 80
        const val DESCRIPTION_MAX = 1000

        /** Enough for "Sommer 2027" or "irgendwann im Ruhestand". */
        const val PERIOD_MAX = 40
    }
}

/** The wish with its [tagIds] as the editor starts out with it. */
fun Wish.toDraft(tagIds: Set<Long>) = WishDraft(title, description, period, status, doneOn, tagIds)
