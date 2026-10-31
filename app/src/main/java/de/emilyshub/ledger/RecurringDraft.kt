package de.emilyshub.ledger

import de.emilyshub.data.InputText
import java.io.Serializable
import java.time.YearMonth

/** What keeps a [RecurringDraft] from being saved, each shown at its field in the editor. */
enum class RecurringProblem {
    AmountMissing,
    AmountInvalid,
    AmountNotAboveZero,
    CategoryMissing,
    DescriptionTooLong,
    DayOutOfRange,
}

/**
 * A fixed booking as typed into its editor (SPEC.md §13): a booking of [kind] without a date, coming every [interval]
 * on [day] of the month from [firstMonth] on. Its checks are those of a booking, and a day from 1 to 31.
 */
data class RecurringDraft(
    val kind: BookingKind,
    val amount: String,
    val categoryId: Long? = null,
    val description: String = "",
    val interval: BookingInterval = BookingInterval.Monthly,
    val day: Int = 1,
    val firstMonth: YearMonth,
) : Serializable {

    /** As one of its bookings, on the first day of [firstMonth], for the checks they share. */
    val asBooking: BookingDraft get() = BookingDraft(kind, amount, firstMonth.atDay(1), categoryId, description)

    val problems: Set<RecurringProblem>
        get() = buildSet {
            asBooking.problems.forEach { add(RecurringProblem.valueOf(it.name)) }
            if (day !in RecurringBooking.DAYS) add(RecurringProblem.DayOutOfRange)
        }

    val cleanedDescription: String get() = InputText.singleLine(description)
}

/** The template as its editor starts out with it, of [kind], that of its category. */
fun RecurringBooking.toDraft(kind: BookingKind) =
    RecurringDraft(kind, Money.typed(amountCents), categoryId, description, interval, day, firstMonth)
