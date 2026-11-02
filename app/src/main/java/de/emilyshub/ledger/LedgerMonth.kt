package de.emilyshub.ledger

import java.time.YearMonth

/**
 * A month of the household book as its page shows it (SPEC.md §13), all of it from one read of the months closed, so
 * that no part of it says open while another says closed: its [summary]; its [opening] balance, if it has one; the
 * month before it ([previous]), whose end balance it may carry over, and the one after ([next]), into which its own
 * may be carried when it is closed; whether it is [closed]; whether it [canClose]
 * now, gone by or on its last day; how many fixed bookings it [skipped] while closed, to enter now it is open again,
 * none while closed; and the earlier months [stillOpen], itself not among them, the latest first.
 */
data class LedgerMonth(
    val month: YearMonth,
    val summary: MonthSummary,
    val opening: OpeningBalance?,
    val previous: PreviousMonth,
    val next: NextMonth,
    val closed: Boolean,
    val canClose: Boolean,
    val skipped: Int,
    val stillOpen: List<YearMonth>,
) {
    /** What it ends with: its opening balance, none counting as zero, and what came in less what went out. */
    val endBalance: Long get() = (opening?.cents ?: 0) + summary.balance

    /** Its end balance as the page shows it: only with an opening balance. */
    val closingBalance: Long? get() = opening?.let { endBalance }

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

/** The month before one (SPEC.md §13): whether it is [closed], and the [endBalance] it would carry over now. */
data class PreviousMonth(val month: YearMonth, val closed: Boolean, val endBalance: Long)

/** The month after one (SPEC.md §13): whether it is [closed], and its [opening] balance, if it has one. */
data class NextMonth(val month: YearMonth, val closed: Boolean, val opening: OpeningBalance?)
