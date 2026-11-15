package de.emilyshub.ledger

import de.emilyshub.data.InputText
import de.emilyshub.todos.RecurrenceEnd
import java.io.Serializable
import java.time.LocalDate
import java.time.YearMonth

/** What keeps a [RecurringDraft] from being saved, each shown at its field in the editor. */
enum class RecurringProblem {
    AmountMissing,
    AmountInvalid,
    AmountNotAboveZero,
    CategoryMissing,
    DescriptionTooLong,
    DayOutOfRange,

    /** An end after fewer than 1 or more than 999 bookings. */
    EndTimesOutOfRange,

    /** An end on a day before its first booking. */
    EndBeforeFirst,

    /** More than a year back, unless saved so before (SPEC.md §13): only when saving, by [Ledger.problems]. */
    FirstMonthTooEarly,
}

/**
 * A fixed booking as typed into its editor (SPEC.md §13): a booking of [kind] without a date, coming every [interval]
 * on [day] of the month from [firstMonth] on, until its [end], if it has one. Its checks are those of a booking, a day
 * from 1 to 31, and an end after 1 to 999 bookings or not before the first.
 */
data class RecurringDraft(
    val kind: BookingKind,
    val amount: String,
    val categoryId: Long? = null,
    val description: String = "",
    val interval: BookingInterval = BookingInterval.Monthly,
    val day: Int = 1,
    val firstMonth: YearMonth,
    val end: RecurrenceEnd = RecurrenceEnd.Never,
) : Serializable {

    /** As one of its bookings, on the first day of [firstMonth], for the checks they share. */
    val asBooking: BookingDraft get() = BookingDraft(kind, amount, firstMonth.atDay(1), categoryId, description)

    val problems: Set<RecurringProblem>
        get() = buildSet {
            asBooking.problems.forEach { problem ->
                when (problem) {
                    BookingProblem.AmountMissing -> add(RecurringProblem.AmountMissing)
                    BookingProblem.AmountInvalid -> add(RecurringProblem.AmountInvalid)
                    BookingProblem.AmountNotAboveZero -> add(RecurringProblem.AmountNotAboveZero)
                    BookingProblem.CategoryMissing -> add(RecurringProblem.CategoryMissing)
                    BookingProblem.DescriptionTooLong -> add(RecurringProblem.DescriptionTooLong)
                    // A draft's own problems have none: it depends on the months closed.
                    BookingProblem.MonthClosed -> Unit
                }
            }
            if (day !in RecurringBooking.DAYS) add(RecurringProblem.DayOutOfRange)
            when (end) {
                RecurrenceEnd.Never -> Unit
                is RecurrenceEnd.AfterTimes -> if (end.times !in RecurrenceEnd.TIMES) add(RecurringProblem.EndTimesOutOfRange)
                is RecurrenceEnd.OnDate -> if (day in RecurringBooking.DAYS && end.date < firstDate) add(RecurringProblem.EndBeforeFirst)
            }
        }

    val cleanedDescription: String get() = InputText.singleLine(description)

    /** As it is saved: the amount without spaces around it, the description on a single line. */
    fun cleaned() = copy(amount = amount.trim(), description = cleanedDescription)

    /** The day of its first booking; with a day out of range, the first of its first month. */
    val firstDate: LocalDate
        get() = if (day in RecurringBooking.DAYS) schedule(interval, day, firstMonth).first() else firstMonth.atDay(1)
}

/** The template as its editor starts out with it, of [kind], that of its category. */
fun RecurringBooking.toDraft(kind: BookingKind) =
    RecurringDraft(kind, Money.typed(amountCents), categoryId, description, interval, day, firstMonth, end)
