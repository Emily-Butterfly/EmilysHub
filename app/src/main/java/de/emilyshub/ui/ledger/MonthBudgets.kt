package de.emilyshub.ui.ledger

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import de.emilyshub.R
import de.emilyshub.ledger.BudgetInput
import de.emilyshub.ledger.BudgetProblem
import de.emilyshub.ledger.CategoryBudget
import de.emilyshub.ledger.Money
import de.emilyshub.ui.monthAndYear
import java.time.YearMonth

/** The [budgets] of the expense categories in the open [month] (SPEC.md §13), tapping one changes it there alone. */
@Composable
internal fun MonthBudgetsDialog(month: YearMonth, budgets: List<CategoryBudget>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.month_budgets_title, month.monthAndYear())) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                budgets.forEach { budget ->
                    ListItem(
                        headlineContent = { Text(budget.category.name) },
                        supportingContent = {
                            Text(budget.inMonth?.let(Money::format) ?: stringResource(R.string.month_budget_none))
                        },
                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.month_budget_change)) {
                            onPick(budget.category.id)
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The budget of an expense category in the open [month] alone (SPEC.md §13), in euros with a comma: above zero, or none
 * if left empty; the month's own, or the monthly one, which "Wie sonst" brings back.
 */
@Composable
internal fun MonthBudgetDialog(month: YearMonth, budget: CategoryBudget, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf(budget.inMonth?.let(Money::typed).orEmpty()) }
    val input = BudgetInput(typed)
    val problem = input.problem?.let(::budgetProblemText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.month_budget_title, budget.category.name, month.monthAndYear())) },
        text = {
            OutlinedTextField(
                value = typed,
                onValueChange = { text -> typed = text.filter { it.isDigit() || it == ',' || it == '.' } },
                label = { Text(stringResource(R.string.budget)) },
                singleLine = true,
                suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = problem != null,
                supportingText = {
                    // That it holds in this month alone, always, as the field starts out with the budget it has.
                    Text(
                        when {
                            problem != null -> stringResource(problem)
                            else -> budget.monthly?.let { stringResource(R.string.month_budget_hint, Money.format(it)) }
                                ?: stringResource(R.string.month_budget_hint_none)
                        },
                    )
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input.budget) }, enabled = problem == null) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                if (budget.ownInMonth) {
                    TextButton(onClick = { onConfirm(budget.monthly) }) { Text(stringResource(R.string.month_budget_reset)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** What keeps a budget typed from being set, in words. */
@StringRes
internal fun budgetProblemText(problem: BudgetProblem): Int = when (problem) {
    BudgetProblem.AmountInvalid -> R.string.booking_amount_invalid
    BudgetProblem.NotAboveZero -> R.string.booking_amount_not_above_zero
}
