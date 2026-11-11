package de.emilyshub.ledger

import java.io.Serializable
import java.time.YearMonth

/**
 * An expense [category] in the budget calculator (SPEC.md §13): its [budget], none for "ohne Budget", and the [average]
 * of its expenses in the last three months closed, none without a month closed.
 */
data class PlanLine(val category: LedgerCategory, val budget: Long?, val average: Long?)

/**
 * The budget calculator as it opens (SPEC.md §13): for the monthly budgets against the [standard] total budget, with
 * [month] null; or for the budgets of the open [month] against its total budget. Its [lines], every expense category;
 * the total budget it has to change, [total]: the standard, or the month's own, null if it follows the standard; and the
 * [income] of [incomeMonth] a total budget from income is reckoned with: that of the month, or of this month for the
 * standard. The averages are of [averageMonths], up to three, none without a month closed.
 */
data class BudgetPlan(
    val month: YearMonth?,
    val lines: List<PlanLine>,
    val total: TotalBudget?,
    val standard: TotalBudget,
    val income: Long,
    val incomeMonth: YearMonth,
    val averageMonths: Int = 0,
)

/** How the total budget is chosen in the calculator: as usual (a month only), none, a fixed amount or from income. */
enum class TotalChoice { AsUsual, None, Fixed, FromIncome }

/**
 * What is typed into the calculator (SPEC.md §13): a budget by category id, in euros with a comma, empty for none; the
 * [choice] of total budget, with its [amount] if fixed and its [saving] if from income.
 */
data class PlanTyped(
    val budgets: Map<Long, String>,
    val choice: TotalChoice,
    val amount: String,
    val saving: String,
) : Serializable {
    fun withBudget(categoryId: Long, typed: String) = copy(budgets = budgets + (categoryId to typed))

    fun withTotal(choice: TotalChoice) = copy(choice = choice)

    companion object {
        /** The calculator as it opens: with the budgets and the total budget [plan] has. */
        fun of(plan: BudgetPlan): PlanTyped {
            val total = plan.total
            return PlanTyped(
                budgets = plan.lines.associate { it.category.id to it.budget?.let(Money::typed).orEmpty() },
                choice = when (total) {
                    null -> TotalChoice.AsUsual
                    TotalBudget.None -> TotalChoice.None
                    is TotalBudget.Fixed -> TotalChoice.Fixed
                    is TotalBudget.FromIncome -> TotalChoice.FromIncome
                },
                amount = (total as? TotalBudget.Fixed)?.cents?.let(Money::typed).orEmpty(),
                saving = (total as? TotalBudget.FromIncome)?.savingCents?.let(Money::typed) ?: "0",
            )
        }
    }
}

/**
 * The calculator's [plan] with what is [typed] (SPEC.md §13): each field judged by [BudgetInput], the sum of the budgets,
 * those without counting nothing, and what is left of the total budget, below zero by as much as they are over it.
 */
class PlanCheck(val plan: BudgetPlan, val typed: PlanTyped) {
    private val inputs: Map<Long, BudgetInput> = plan.lines.associate { line ->
        line.category.id to BudgetInput(typed.budgets[line.category.id].orEmpty())
    }

    /** The fields with a problem, by category id. */
    val problems: Map<Long, BudgetProblem> = inputs.mapNotNull { (id, input) -> input.problem?.let { id to it } }.toMap()

    /** What keeps the fixed total budget typed from being set: none typed counts as no amount. */
    val amountProblem: BudgetProblem? = if (typed.choice != TotalChoice.Fixed) {
        null
    } else {
        val input = BudgetInput(typed.amount)
        input.problem ?: BudgetProblem.AmountInvalid.takeIf { input.budget == null }
    }

    /** What keeps the saving typed from being set: zero is one, none typed too. */
    val savingProblem: BudgetProblem? =
        BudgetProblem.AmountInvalid.takeIf { typed.choice == TotalChoice.FromIncome && savingCents == null }

    private val savingCents: Long? get() = if (typed.saving.isBlank()) 0 else Money.parse(typed.saving)

    /** Whether all of it can be saved: no field has a problem, over the total budget too. */
    val canSave: Boolean get() = problems.isEmpty() && amountProblem == null && savingProblem == null

    /** The budgets as they are saved, by category id: none for one empty. Only if it [canSave]. */
    val budgets: Map<Long, Long?> get() = inputs.mapValues { (_, input) -> input.budget }

    /** The total budget chosen, null for as usual; none while it has a problem. */
    val total: TotalBudget?
        get() = when (typed.choice) {
            TotalChoice.AsUsual -> null
            TotalChoice.None -> TotalBudget.None
            TotalChoice.Fixed -> BudgetInput(typed.amount).budget?.takeIf { amountProblem == null }?.let(TotalBudget::Fixed)
            TotalChoice.FromIncome -> savingCents?.let(TotalBudget::FromIncome)
        }

    /** The total budget that holds: that chosen, else the standard; none while it has a problem. */
    val effective: TotalBudget? get() = if (typed.choice == TotalChoice.AsUsual) plan.standard else total

    /** The amount the total budget comes to; none without one. */
    val totalAmount: Long? get() = effective?.amount(plan.income)

    /** What the budgets come to, those empty or with a problem counting nothing. */
    val sum: Long get() = inputs.values.sumOf { if (it.problem == null) it.budget ?: 0 else 0 }

    /** What is left of the total budget, below zero by as much as the budgets are over it; none without one. */
    val left: Long? get() = totalAmount?.let { it - sum }
}
