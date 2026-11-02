package de.emilyshub.ledger

import java.time.YearMonth

/**
 * A month of the household book as its page shows it (SPEC.md §13), all of it from one read of the months closed, so
 * that no part of it says open while another says closed: its [summary]; whether it is [closed]; whether it [canClose]
 * now, gone by or on its last day; how many fixed bookings it [skipped] while closed, to enter now it is open again,
 * none while closed; and the earlier months [stillOpen], itself not among them, the latest first.
 */
data class LedgerMonth(
    val month: YearMonth,
    val summary: MonthSummary,
    val closed: Boolean,
    val canClose: Boolean,
    val skipped: Int,
    val stillOpen: List<YearMonth>,
) {
    /** What can be done with it now. */
    val offers: MonthOffer
        get() = MonthOffer(
            book = !closed,
            closing = when {
                closed -> MonthClosing.Reopen
                canClose -> MonthClosing.Close
                else -> MonthClosing.None
            },
            enterSkipped = !closed && skipped > 0,
        )
}

/**
 * What a month offers (SPEC.md §13): a booking made in it ([book]); closing it or opening it again ([closing]); and
 * entering the fixed bookings it skipped while closed ([enterSkipped]).
 */
data class MonthOffer(val book: Boolean, val closing: MonthClosing, val enterSkipped: Boolean)

/** Whether a month is closed now, opened again, or neither: one not yet gone by. */
enum class MonthClosing { Close, Reopen, None }
