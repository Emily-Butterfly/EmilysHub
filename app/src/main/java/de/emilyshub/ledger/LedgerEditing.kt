package de.emilyshub.ledger

import java.io.Serializable
import java.time.YearMonth

/**
 * A booking being edited (SPEC.md §13): a new one ([New]), or the one saved under [id] as it was when the editor opened
 * ([original]). Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class BookingEditing(val id: Long?, val original: BookingDraft?) : Serializable {

    /** The month it was saved in, which locks it too while closed; none for a new one. */
    val savedMonth: YearMonth? get() = original?.date?.let(YearMonth::from)

    /**
     * The month among those [closed] that keeps [draft] from being saved as this, and the booking from being deleted:
     * neither made, changed nor deleted in a month closed, nor moved out of one or into one; none if both are open.
     */
    fun closedMonth(draft: BookingDraft, closed: Set<YearMonth>): YearMonth? =
        listOfNotNull(savedMonth, YearMonth.from(draft.date)).firstOrNull { it in closed }

    companion object {
        val New = BookingEditing(id = null, original = null)
    }
}

/**
 * A fixed booking being edited (SPEC.md §13): a new one ([New]), or the one saved under [id] as it was when the editor
 * opened ([original]), whose first month it may keep. Serializable, so that the editor keeps it.
 */
data class RecurringEditing(val id: Long?, val original: RecurringDraft?) : Serializable {
    companion object {
        val New = RecurringEditing(id = null, original = null)
    }
}
