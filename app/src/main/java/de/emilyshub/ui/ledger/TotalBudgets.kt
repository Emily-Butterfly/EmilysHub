package de.emilyshub.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.ledger.BudgetLevel
import de.emilyshub.ledger.BudgetUse
import de.emilyshub.ledger.Money
import de.emilyshub.ledger.TotalBudget

/**
 * The total bar of a month (SPEC.md §13): what is spent of its total budget, in the colours of the categories' bars,
 * orange from 80 %, red above it, with what is left or how much it is over in words. While the month is open
 * ([onOpen]), tapping it opens the budget calculator.
 */
@Composable
internal fun TotalBar(use: BudgetUse, onOpen: (() -> Unit)?) {
    val label = stringResource(R.string.budget_calculator_open)
    Column(
        Modifier
            .then(if (onOpen == null) Modifier else Modifier.clickable(onClickLabel = label, onClick = onOpen))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row {
            Text(stringResource(R.string.total_budget), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.total_budget_of, Money.format(use.spent), Money.format(use.budget)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Bar(share = use.share, color = budgetColor(use.level) ?: MaterialTheme.colorScheme.primary)
        val over = use.level == BudgetLevel.Over
        Text(
            if (over) {
                stringResource(R.string.budget_over, Money.format(-use.left))
            } else {
                stringResource(R.string.budget_left, Money.format(use.left))
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * What a month's expenses use of its total budget, in the archive and the year overview (SPEC.md §13): "1.840,00 € von
 * 2.000,00 € · 160,00 € frei", red with "drüber" above it.
 */
@Composable
internal fun TotalBudgetLine(use: BudgetUse, labelled: Boolean = false) {
    val over = use.left < 0
    val difference = if (over) {
        stringResource(R.string.total_budget_over, Money.format(-use.left))
    } else {
        stringResource(R.string.total_budget_free, Money.format(use.left))
    }
    Text(
        stringResource(
            if (labelled) R.string.total_budget_line_labelled else R.string.total_budget_line,
            Money.format(use.spent), Money.format(use.budget), difference,
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The standard total budget in words, as the categories show it. */
@Composable
internal fun totalBudgetText(budget: TotalBudget): String = when (budget) {
    TotalBudget.None -> stringResource(R.string.total_budget_none)
    is TotalBudget.Fixed -> stringResource(R.string.total_budget_fixed, Money.format(budget.cents))
    is TotalBudget.FromIncome -> stringResource(R.string.total_budget_from_income, Money.format(budget.savingCents))
}

/** A total budget in a few words, as "Wie sonst" names the standard. */
@Composable
internal fun totalKindText(budget: TotalBudget): String = when (budget) {
    TotalBudget.None -> stringResource(R.string.total_kind_none)
    is TotalBudget.Fixed -> stringResource(R.string.total_kind_fixed, Money.format(budget.cents))
    is TotalBudget.FromIncome -> stringResource(R.string.total_kind_from_income, Money.format(budget.savingCents))
}
