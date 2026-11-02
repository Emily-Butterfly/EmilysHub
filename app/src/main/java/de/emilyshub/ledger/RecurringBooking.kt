package de.emilyshub.ledger

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.emilyshub.todos.RecurrenceEnd
import java.time.LocalDate
import java.time.YearMonth

/** How often a fixed booking comes (SPEC.md §13), every so many [months]. */
enum class BookingInterval(val months: Int, val stored: String) {
    Monthly(1, "monthly"),
    Quarterly(3, "quarterly"),
    HalfYearly(6, "half-yearly"),
    Yearly(12, "yearly"),
}

/**
 * A fixed booking (SPEC.md §13), such as rent, salary or a subscription: a template the household book enters a
 * booking from on its [day] of the month, every [interval] from its [firstMonth] on, in shorter months on their last
 * day. [enteredThrough] is the month of the last one entered, so that each comes exactly once, also when one entered
 * was deleted since. It may end ([end], #52): after [endsAfter] bookings from [firstMonth] on, or with the last by
 * [endsOn], that day included.
 */
@Entity(
    tableName = "recurring_bookings",
    foreignKeys = [
        ForeignKey(
            entity = LedgerCategory::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["category_id"])],
)
data class RecurringBooking(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val description: String = "",
    val interval: BookingInterval,
    val day: Int,
    @ColumnInfo(name = "first_month") val firstMonth: YearMonth,
    @ColumnInfo(name = "entered_through") val enteredThrough: YearMonth? = null,
    @ColumnInfo(name = "ends_after") val endsAfter: Int? = null,
    @ColumnInfo(name = "ends_on") val endsOn: LocalDate? = null,
) {
    /** When it ends, from [endsAfter] and [endsOn]. */
    val end: RecurrenceEnd
        get() = endsAfter?.let(RecurrenceEnd::AfterTimes) ?: endsOn?.let(RecurrenceEnd::OnDate) ?: RecurrenceEnd.Never

    /** All the dates it comes on, from its first month on, up to its end, if it has one. */
    fun dates(): Sequence<LocalDate> = schedule(interval, day, firstMonth).until(end)

    /** Which of its dates [date] is, the first counting 1. */
    fun numberOf(date: LocalDate): Int = schedule(interval, day, firstMonth).indexOf(date) + 1

    /**
     * Changed on [today] after [previous], the month entered last before: so that the change holds from the next one
     * on, the months of its new schedule before today count as entered, unless none was entered yet, when it is as new.
     */
    fun enteredAfterChange(previous: YearMonth?, today: LocalDate): YearMonth? {
        previous ?: return null
        val gone = schedule(interval, day, firstMonth).takeWhile { it.isBefore(today) }.lastOrNull()?.let(YearMonth::from)
        return if (gone != null && gone.isAfter(previous)) gone else previous
    }

    /** The booking entered from it on [date]. */
    fun entryOn(date: LocalDate) = Booking(0, amountCents, date, categoryId, description, recurringId = id)

    /** The dates it is still to be entered on, from the first after the month last entered. */
    fun dueDates(): Sequence<LocalDate> =
        dates().filter { date -> enteredThrough?.let { YearMonth.from(date).isAfter(it) } ?: true }

    companion object {
        val DAYS = 1..31
    }
}

/**
 * A fixed booking not entered on [date], as its month was closed then (SPEC.md §13): offered once the month is open
 * again. Deleting the fixed booking takes it along.
 */
@Entity(
    tableName = "ledger_skipped",
    primaryKeys = ["recurring_id", "date"],
    foreignKeys = [
        ForeignKey(
            entity = RecurringBooking::class, parentColumns = ["id"], childColumns = ["recurring_id"], onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SkippedEntry(@ColumnInfo(name = "recurring_id") val recurringId: Long, val date: LocalDate)

/** A fixed booking with its category. */
data class RecurringWithCategory(
    @Embedded val recurring: RecurringBooking,
    @Relation(parentColumn = "category_id", entityColumn = "id") val category: LedgerCategory,
)

/**
 * A fixed booking as the list shows it: as the editor starts out with it, in its [category], next due on [next], the
 * [seriesNumber]th of its dates; neither once it has ended.
 */
data class RecurringTemplate(
    val id: Long,
    val draft: RecurringDraft,
    val amountCents: Long,
    val category: LedgerCategory,
    val next: LocalDate?,
    val seriesNumber: Int? = null,
)

/**
 * The dates of a booking on [day] of the month every [interval] from [first] on, without end: on the last day of a
 * month too short for [day].
 */
fun schedule(interval: BookingInterval, day: Int, first: YearMonth): Sequence<LocalDate> =
    generateSequence(first) { it.plusMonths(interval.months.toLong()) }.map { it.atDay(minOf(day, it.lengthOfMonth())) }

/** These dates up to [end]: so many of them, or those by its date, that day included. */
private fun Sequence<LocalDate>.until(end: RecurrenceEnd): Sequence<LocalDate> = when (end) {
    RecurrenceEnd.Never -> this
    is RecurrenceEnd.AfterTimes -> take(end.times)
    is RecurrenceEnd.OnDate -> takeWhile { !it.isAfter(end.date) }
}
