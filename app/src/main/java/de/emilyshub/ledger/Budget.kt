package de.emilyshub.ledger

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.time.YearMonth

/** How far a month's expenses in a category have come in its budget (SPEC.md §13). */
enum class BudgetLevel { Within, Near, Over }

/** What a month has [spent] in a category of its [budget], above zero, both in cents. */
data class BudgetUse(val budget: Long, val spent: Long) {
    /** What is left of it; below zero by as much as it is over. */
    val left: Long get() = budget - spent

    /** Within it below 80 %, near from 80 % on, over above 100 %. */
    val level: BudgetLevel
        get() = when {
            spent > budget -> BudgetLevel.Over
            spent * 100 >= budget * NEAR_PERCENT -> BudgetLevel.Near
            else -> BudgetLevel.Within
        }

    /** How much of it is used, as a bar is filled: at most all of it. */
    val share: Float get() = (spent.toFloat() / budget).coerceAtMost(1f)

    private companion object {
        const val NEAR_PERCENT = 80
    }
}

/**
 * The budget a category had when [month] was closed (SPEC.md §13), which the month keeps until it is opened again;
 * then it has the budget of now. Deleting the category takes it along.
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
