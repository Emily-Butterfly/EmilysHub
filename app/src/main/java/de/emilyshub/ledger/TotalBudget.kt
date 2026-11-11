package de.emilyshub.ledger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.YearMonth

/** How a total budget is stored: its kind, and the cents it goes with. */
enum class TotalBudgetKind(val stored: String) { None("none"), Fixed("fixed"), FromIncome("income") }

/**
 * The total budget of all expenses of a month (SPEC.md §13): [None]; a [Fixed] amount above zero; or one [FromIncome],
 * the month's income less a saving, never below zero. The standard for every month open, which each of them can have
 * one of its own in place of.
 */
sealed interface TotalBudget {
    /** No total budget, as before there were any. */
    data object None : TotalBudget {
        override val cents: Long get() = 0
    }

    /** [cents] above zero, up to [Money.MAX]. */
    data class Fixed(override val cents: Long) : TotalBudget

    /**
     * The income of the month, what is entered and what fixed bookings still bring in by its end, less [savingCents],
     * zero or above, up to [Money.MAX]; the opening balance does not count.
     */
    data class FromIncome(val savingCents: Long = 0) : TotalBudget {
        override val cents: Long get() = savingCents
    }

    /** The amount it comes to in a month whose [income] is so much; none for [None]. */
    fun amount(income: Long): Long? = when (this) {
        None -> null
        is Fixed -> cents
        is FromIncome -> (income - savingCents).coerceAtLeast(0)
    }

    /** Whether it can be set: a fixed amount above zero, a saving not below it, both up to [Money.MAX]. */
    val fits: Boolean
        get() = when (this) {
            None -> true
            is Fixed -> cents in 1..Money.MAX
            is FromIncome -> savingCents in 0..Money.MAX
        }

    /** Its kind as stored. */
    val kind: TotalBudgetKind
        get() = when (this) {
            None -> TotalBudgetKind.None
            is Fixed -> TotalBudgetKind.Fixed
            is FromIncome -> TotalBudgetKind.FromIncome
        }

    /** The cents stored with its [kind]: the amount, the saving, or zero for none. */
    val cents: Long

    companion object {
        /** The total budget stored as [kind] with [cents]. */
        fun of(kind: TotalBudgetKind, cents: Long): TotalBudget = when (kind) {
            TotalBudgetKind.None -> None
            TotalBudgetKind.Fixed -> Fixed(cents)
            TotalBudgetKind.FromIncome -> FromIncome(cents)
        }
    }
}

/**
 * The standard total budget of the household book (SPEC.md §13), for every month open without one of its own; a
 * single row ([id] [ONLY]), none at all meaning [TotalBudget.None].
 */
@Entity(tableName = "ledger_total_budget")
data class StandardTotalBudget(
    @PrimaryKey val id: Int = ONLY,
    val kind: TotalBudgetKind,
    val cents: Long,
) {
    /** The total budget it stores. */
    val budget: TotalBudget get() = TotalBudget.of(kind, cents)

    companion object {
        /** The id of the one row. */
        const val ONLY = 1

        /** [budget] as the one row stores it. */
        fun of(budget: TotalBudget) = StandardTotalBudget(ONLY, budget.kind, budget.cents)
    }
}

/**
 * The total budget [month] has of its own (SPEC.md §13), in place of the standard: another amount, one from income with
 * a saving of its own, or none. Set while the month is open; it stays when the month is closed, which keeps the amount
 * it comes to, and holds again once the month is opened again.
 */
@Entity(tableName = "ledger_month_total_budgets")
data class MonthTotalBudget(
    @PrimaryKey val month: YearMonth,
    val kind: TotalBudgetKind,
    val cents: Long,
) {
    /** The total budget it stores. */
    val budget: TotalBudget get() = TotalBudget.of(kind, cents)

    companion object {
        /** [budget] as [month]'s own, stored. */
        fun of(month: YearMonth, budget: TotalBudget) = MonthTotalBudget(month, budget.kind, budget.cents)
    }
}
