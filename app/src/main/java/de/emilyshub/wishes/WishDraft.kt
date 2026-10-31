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
    ProgressOutOfRange,
}

/**
 * A wish as typed into the editor, before it is checked. The checks apply to the [cleaned] draft, and saving refuses a
 * draft with [problems] (SPEC.md §9: strict input checks). Serializable, so that the editor keeps it across rotation and
 * the app lock.
 */
data class WishDraft(
    val title: String,
    val description: String = "",
    /** When, in the user's words ("2027", "Sommer"): no date, so nothing reminds of it. */
    val period: String = "",
    val status: WishStatus = WishStatus.Open,
    /** The day chosen that it came true; see [cameTrueOn]. */
    val doneOn: LocalDate? = null,
    /** Its tags; one deleted meanwhile is left out on saving. */
    val tagIds: Set<Long> = emptySet(),
    /** The goal it is part of; none: at the top. One deleted meanwhile is left out on saving. */
    val parentId: Long? = null,
    /** How far it has come in percent, set by hand; none: worked out from its parts. */
    val manualProgress: Int? = null,
) : Serializable {

    val problems: Set<WishProblem>
        get() = with(cleaned()) {
            buildSet {
                if (!InputText.isVisible(title)) add(WishProblem.TitleMissing)
                if (InputText.lengthOf(title) > TITLE_MAX) add(WishProblem.TitleTooLong)
                if (InputText.lengthOf(description) > DESCRIPTION_MAX) add(WishProblem.DescriptionTooLong)
                if (InputText.lengthOf(period) > PERIOD_MAX) add(WishProblem.PeriodTooLong)
                if (manualProgress != null && manualProgress !in 0..PROGRESS_MAX) add(WishProblem.ProgressOutOfRange)
            }
        }

    /** Without surrounding spaces; the title and the period on a single line. */
    fun cleaned() = copy(
        title = InputText.singleLine(title),
        description = InputText.multiLine(description).trimStart(),
        period = InputText.singleLine(period),
    )

    /**
     * The day it came true, if it is done, as of [today]: the one chosen, or today. A day still ahead counts as today, as
     * it may be after a move to a time zone further west, or in a backup from one further east.
     */
    fun cameTrueOn(today: LocalDate): LocalDate? =
        if (status == WishStatus.Done) minOf(doneOn ?: today, today) else null

    /** The wish this draft makes as of [today], [id] if saved already. */
    fun toWish(today: LocalDate, id: Long = 0) =
        Wish(id, title, description, period, status, cameTrueOn(today), parentId, manualProgress)

    companion object {
        const val TITLE_MAX = 80
        const val DESCRIPTION_MAX = 1000

        /** Enough for "Sommer 2027" or "irgendwann im Ruhestand". */
        const val PERIOD_MAX = 40

        /** All of it, in percent. */
        const val PROGRESS_MAX = 100
    }
}

/** The wish with its [tagIds] as the editor starts out with it. */
fun Wish.toDraft(tagIds: Set<Long>) = WishDraft(title, description, period, status, doneOn, tagIds, parentId, manualProgress)
