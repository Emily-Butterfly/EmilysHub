package de.emilyshub.ledger

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.YearMonth

/** Whether a booking takes money out or brings it in (SPEC.md §13), in the order the screens show them. */
enum class BookingKind(val stored: String) {
    Expense("expense"),
    Income("income"),
}

/**
 * A category of the household book (SPEC.md §13), for bookings of its [kind]. Each kind has one "Sonstiges" ([isOther])
 * that cannot be deleted: the bookings of a category deleted go there.
 */
@Entity(tableName = "ledger_categories")
data class LedgerCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: BookingKind,
    val name: String,
    @ColumnInfo(name = "is_other", defaultValue = "0") val isOther: Boolean = false,
) {
    companion object {
        const val NAME_MAX = 30
    }
}

/**
 * A booking of the household book (SPEC.md §13): [amountCents] above zero on [date] in its category, which says
 * whether it is income or an expense, with a [description] if given.
 */
@Entity(
    tableName = "bookings",
    foreignKeys = [
        ForeignKey(
            entity = LedgerCategory::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["category_id"]), Index(value = ["date"])],
)
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val date: LocalDate,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val description: String = "",
    /** The fixed booking it was entered from, if any (SPEC.md §13); one deleted since leaves it as it is. */
    @ColumnInfo(name = "recurring_id") val recurringId: Long? = null,
)

/** A month of the household book closed by hand (SPEC.md §13): in the archive, only to read, until opened again. */
@Entity(tableName = "ledger_closed_months")
data class ClosedMonth(@PrimaryKey val month: YearMonth)

/** A month in the archive, with what came in and went out. */
data class ArchivedMonth(val month: YearMonth, val income: Long, val expenses: Long) {
    val balance: Long get() = income - expenses
}

/** A booking with its category. */
data class BookingWithCategory(
    @Embedded val booking: Booking,
    @Relation(parentColumn = "category_id", entityColumn = "id") val category: LedgerCategory,
)

/** What a month's expenses in one [category] come to. */
data class CategoryTotal(val category: LedgerCategory, val cents: Long)

/**
 * A month of the household book (SPEC.md §13): what came in and went out, the balance, the expenses by category, the
 * largest first, and all its bookings, the latest first.
 */
data class MonthSummary(
    val income: Long,
    val expenses: Long,
    val expensesByCategory: List<CategoryTotal>,
    val bookings: List<BookingWithCategory>,
) {
    val balance: Long get() = income - expenses
}
