package de.emilyshub.ledger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.YearMonth

/** What keeps a budget typed from being set: no amount ([AmountInvalid]), or none above zero ([NotAboveZero]). */
enum class BudgetProblem { AmountInvalid, NotAboveZero }

/**
 * A category's budget as typed (SPEC.md §13), monthly or in one month alone, in euros with a comma: none if left
 * empty, else an amount above zero, up to [Money.MAX]. The one rule for it, for the dialogs that take it and for
 * [Ledger], which refuses any other.
 */
data class BudgetInput(val typed: String) {
    private val cents: Long? = Money.parse(typed)

    /** What keeps it from being set; none if it can be. */
    val problem: BudgetProblem? = when {
        typed.isBlank() -> null
        cents == null -> BudgetProblem.AmountInvalid
        !fits(cents) -> BudgetProblem.NotAboveZero
        else -> null
    }

    /** The budget it sets, in cents: none if left empty. Only without a [problem]. */
    val budget: Long? get() = cents.takeIf { typed.isNotBlank() }

    companion object {
        /** Whether [cents] is a budget, or none: above zero, up to [Money.MAX]. */
        fun fits(cents: Long?): Boolean = cents == null || cents in 1..Money.MAX
    }
}

/** How far a month's expenses in a category have come in its budget (SPEC.md §13). */
enum class BudgetLevel { Within, Near, Over }

/**
 * What a month has [spent] of a [budget], both in cents: a category's, above zero, or its total budget, which may be
 * zero, coming from income.
 */
data class BudgetUse(val budget: Long, val spent: Long) {
    /** What is left of it; below zero by as much as it is over. */
    val left: Long get() = budget - spent

    /** Within it below 80 %, near from 80 % on, over above 100 %. */
    val level: BudgetLevel
        get() = when {
            spent > budget -> BudgetLevel.Over
            budget > 0 && spent * 100 >= budget * NEAR_PERCENT -> BudgetLevel.Near
            else -> BudgetLevel.Within
        }

    /** How much of it is used, as a bar is filled: at most all of it; all of one of nothing, once anything is spent. */
    val share: Float get() = if (budget == 0L) (if (spent > 0) 1f else 0f) else (spent.toFloat() / budget).coerceAtMost(1f)

    private companion object {
        const val NEAR_PERCENT = 80
    }
}

/**
 * The budget a category had when [month] was closed (SPEC.md §13), which the month keeps until it is opened again;
 * then it has the budget of now. The category is not deleted while a month closed keeps its budget.
 */
@Entity(
    tableName = "ledger_closed_budgets",
    primaryKeys = ["month", "category_id"],
    foreignKeys = [
        ForeignKey(entity = ClosedMonth::class, parentColumns = ["month"], childColumns = ["month"], onDelete = ForeignKey.CASCADE),
        ForeignKey(
            entity = LedgerCategory::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["category_id"])],
)
data class ClosedBudget(
    val month: YearMonth,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "budget_cents") val budgetCents: Long,
)

/**
 * The budget an expense category has in one [month] only (SPEC.md §13), in place of its monthly one: another amount, one
 * where it has none, or none ([budgetCents] null) where it has one. Set while the month is open; it stays when the month
 * is closed, which keeps it as its budget then, and holds again once the month is opened again.
 */
@Entity(
    tableName = "ledger_month_budgets",
    primaryKeys = ["month", "category_id"],
    foreignKeys = [
        ForeignKey(
            entity = LedgerCategory::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["category_id"])],
)
data class MonthBudget(
    val month: YearMonth,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "budget_cents") val budgetCents: Long?,
)

/**
 * The budget of an expense [category] in a month (SPEC.md §13): [inMonth], that of the month alone if it has one of its
 * own, else its monthly one; null for none.
 */
data class CategoryBudget(val category: LedgerCategory, val inMonth: Long?) {
    /** Its monthly one, which every month has that has none of its own. */
    val monthly: Long? get() = category.budgetCents

    /** Whether the month has one of its own, another than the monthly one. */
    val ownInMonth: Boolean get() = inMonth != monthly
}

/** The budget [category] has in a month whose budgets of its [own] are these, by category: its own there, else the monthly one. */
internal fun budgetIn(category: LedgerCategory, own: Map<Long, Long?>): Long? =
    if (category.id in own) own[category.id] else category.budgetCents
