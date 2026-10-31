package de.emilyshub.ledger

import de.emilyshub.data.InputText
import java.io.Serializable
import java.time.LocalDate

/** What keeps a [BookingDraft] from being saved, each shown at its field in the editor. */
enum class BookingProblem {
    AmountMissing,
    AmountInvalid,
    AmountNotAboveZero,
    CategoryMissing,
    DescriptionTooLong,
}

/**
 * A booking as typed into the editor (SPEC.md §13), before it is checked: of its [kind], the [amount] as typed with a
 * comma, on [date] in the category [categoryId], which must be one of that kind. Serializable, so that the editor keeps
 * it across rotation and the app lock.
 */
data class BookingDraft(
    val kind: BookingKind,
    val amount: String,
    val date: LocalDate,
    val categoryId: Long? = null,
    val description: String = "",
) : Serializable {

    /** The amount in cents, if it is one. */
    val cents: Long? get() = Money.parse(amount)

    val problems: Set<BookingProblem>
        get() = buildSet {
            val cents = cents
            when {
                !InputText.isVisible(amount) -> add(BookingProblem.AmountMissing)
                cents == null -> add(BookingProblem.AmountInvalid)
                cents <= 0 -> add(BookingProblem.AmountNotAboveZero)
            }
            if (categoryId == null) add(BookingProblem.CategoryMissing)
            if (InputText.lengthOf(cleanedDescription) > DESCRIPTION_MAX) add(BookingProblem.DescriptionTooLong)
        }

    /** The description on a single line, without surrounding spaces. */
    val cleanedDescription: String get() = InputText.singleLine(description)

    companion object {
        const val DESCRIPTION_MAX = 200
    }
}

/** The booking as the editor starts out with it, of [kind], that of its category. */
fun Booking.toDraft(kind: BookingKind) = BookingDraft(kind, Money.typed(amountCents), date, categoryId, description)
