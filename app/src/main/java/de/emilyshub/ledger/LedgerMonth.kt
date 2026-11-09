package de.emilyshub.ledger

import java.time.YearMonth

/**
 * A month of the household book as its page shows it (SPEC.md §13), all of it from one read of the months closed, so
 * that no part of it says open while another says closed: its [summary]; its [opening] balance, if it has one; the
 * month before it ([previous]), whose end balance it may carry over, and the one after ([next]), into which its own
 * may be carried when it is closed; whether it is [closed]; whether it [canClose]
 * now, gone by or on its last day; how many fixed bookings it [skipped] while closed, to enter now it is open again,
 * none while closed; the earlier months [stillOpen], itself not among them, the latest first; and for this month
 * while open, what the fixed bookings still to come in it bring in less what they take out ([upcoming]), none if none
 * is to come, nor for any other month; and while open, the [budgets] of its expense categories, to change in it
 * alone, none while closed.
 */
data class LedgerMonth(
    val month: YearMonth,
    val summary: MonthSummary,
    val opening: OpeningBalance?,
    val previous: PreviousMonth,
    val next: NextMonth,
    val closed: Boolean,
    val canClose: Boolean,
    val upcoming: Long?,
    val skipped: Int,
    val stillOpen: List<YearMonth>,
    val budgets: List<CategoryBudget> = emptyList(),
) {
    /** What it ends with: its opening balance, none counting as zero, and what came in less what went out. */
    val endBalance: Long get() = endBalance(opening?.cents, summary.balance)

    /** Its end balance as the page shows it: only with an opening balance. */
    val closingBalance: Long? get() = opening?.let { endBalance }

    /**
     * What it is foreseen to end with: its end balance with the fixed bookings still to come in it (SPEC.md §13); only
     * for this month while open, and while one is still to come.
     */
    val foreseenEnd: Long? get() = upcoming?.let { endBalance + it }

    /** What can be done with it now; all its page offers, so that the page need not ask whether it is [closed]. */
    val offers: MonthOffer
        get() = MonthOffer(
            book = !closed,
            closing = when {
                closed -> MonthClosing.Reopen
                canClose -> MonthClosing.Close
                else -> MonthClosing.None
            },
            enterSkipped = !closed && skipped > 0,
            editOpening = !closed,
            carry = when {
                closed || opening != null -> null
                previous.closed -> Carry.AtOnce
                else -> Carry.AskFirst
            },
            carryOnClosing = !next.closed,
            budgets = budgets.isNotEmpty(),
        )

    companion object {
        /** What a month ends with: its [opening] balance, none counting as zero, and its [balance]. The one formula. */
        fun endBalance(opening: Long?, balance: Long): Long = (opening ?: 0) + balance
    }
}

/**
 * What a month offers (SPEC.md §13): a booking made in it ([book]); closing it or opening it again ([closing]); entering
 * the fixed bookings it skipped while closed ([enterSkipped]); setting, changing or removing its opening balance by hand
 * ([editOpening]); carrying over the end balance of the month before, without one ([carry]); carrying its own into the
 * next month as it is closed, unless that one is closed ([carryOnClosing]); and changing its budgets ([budgets]).
 */
data class MonthOffer(
    val book: Boolean,
    val closing: MonthClosing,
    val enterSkipped: Boolean,
    val editOpening: Boolean,
    val carry: Carry?,
    val carryOnClosing: Boolean,
    val budgets: Boolean,
)

/**
 * How the end balance of the month before is carried over: at once, as that month is closed and its end balance final;
 * or after asking, as it is still open and may change (SPEC.md §13).
 */
enum class Carry { AtOnce, AskFirst }

/** Whether a month is closed now, opened again, or neither: one not yet gone by. */
enum class MonthClosing { Close, Reopen, None }

/** The month before one (SPEC.md §13): whether it is [closed], and the [endBalance] it would carry over now. */
data class PreviousMonth(val month: YearMonth, val closed: Boolean, val endBalance: Long)

/** The month after one (SPEC.md §13): whether it is [closed], and its [opening] balance, if it has one. */
data class NextMonth(val month: YearMonth, val closed: Boolean, val opening: OpeningBalance?)
