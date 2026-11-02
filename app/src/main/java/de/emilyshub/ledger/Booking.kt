package de.emilyshub.ledger

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth

/** Whether a booking takes money out or brings it in (SPEC.md §13), in the order the screens show them. */
enum class BookingKind(val stored: String) {
    Expense("expense"),
    Income("income"),
}

/**
 * A category of the household book (SPEC.md §13), for bookings of its [kind]. Each kind has one "Sonstiges" ([isOther])
 * that cannot be deleted: the bookings of a category deleted go there. One of expenses can have a monthly budget
 * ([budgetCents]), which holds for every month open.
 */
@Entity(tableName = "ledger_categories")
data class LedgerCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: BookingKind,
    val name: String,
    @ColumnInfo(name = "is_other", defaultValue = "0") val isOther: Boolean = false,
    @ColumnInfo(name = "budget_cents") val budgetCents: Long? = null,
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

/**
 * The opening balance of a [month] of the household book (SPEC.md §13): a value of the month, not a booking, so that it
 * never counts as income; [cents] may be zero or below. [carried]: carried over as the end balance of the month before;
 * else set by hand. It stays as it was carried, whatever the month before does after.
 */
@Entity(tableName = "ledger_opening_balances")
data class OpeningBalance(
    @PrimaryKey val month: YearMonth,
    val cents: Long,
    @ColumnInfo(defaultValue = "0") val carried: Boolean = false,
)

/**
 * A month with what came in and went out, and its opening balance if it has one: in the archive, or in the year
 * overview (SPEC.md §13).
 */
data class MonthSums(val month: YearMonth, val income: Long, val expenses: Long, val opening: Long? = null) {
    val balance: Long get() = income - expenses

    /** What it ends with: only with an opening balance. */
    val closingBalance: Long? get() = opening?.let { it + balance }
}

/** A booking with its category. */
data class BookingWithCategory(
    @Embedded val booking: Booking,
    @Relation(parentColumn = "category_id", entityColumn = "id") val category: LedgerCategory,
)

/** What a month's expenses in one [category] come to, and its budget that month, if it has one. */
data class CategoryTotal(val category: LedgerCategory, val cents: Long, val budgetCents: Long? = null) {
    /** How much of its budget is used, if it has one. */
    val use: BudgetUse? get() = budgetCents?.let { BudgetUse(it, cents) }
}

/**
 * A month of the household book (SPEC.md §13): what came in and went out, the balance, the expenses by category, the
 * largest first, those with a budget but no expenses last, and all its bookings, the latest first.
 */
data class MonthSummary(
    val income: Long,
    val expenses: Long,
    val expensesByCategory: List<CategoryTotal>,
    val bookings: List<BookingWithCategory>,
) {
    val balance: Long get() = income - expenses
}

/**
 * A year of the household book (SPEC.md §13): each of its months, January first, with what came in and went out, and
 * its expenses by category, the largest first. Months open and closed count the same.
 */
data class YearSummary(val year: Year, val months: List<MonthSums>, val expensesByCategory: List<CategoryTotal>) {
    /** The opening balance of January, if it has one; it counts in no sum. */
    val openingOfJanuary: Long? get() = months.first().opening

    /** The end balance of December, if it has an opening balance. */
    val closingOfDecember: Long? get() = months.last().closingBalance

    val income: Long get() = months.sumOf { it.income }
    val expenses: Long get() = months.sumOf { it.expenses }
    val balance: Long get() = income - expenses

    /** The share of the year's expenses that [total] has, in whole percent. */
    fun shareOf(total: CategoryTotal): Int = Money.percent(total.cents, expenses)
}
