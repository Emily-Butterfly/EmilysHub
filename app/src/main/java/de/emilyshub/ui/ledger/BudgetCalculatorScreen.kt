package de.emilyshub.ui.ledger

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.ledger.BudgetPlan
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.Money
import de.emilyshub.ledger.PlanCheck
import de.emilyshub.ledger.PlanLine
import de.emilyshub.ledger.PlanTyped
import de.emilyshub.ledger.TotalBudget
import de.emilyshub.ledger.TotalChoice
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.RadioOption
import de.emilyshub.ui.monthAndYear
import de.emilyshub.ui.rememberClosing
import java.time.YearMonth

/**
 * The budget calculator (SPEC.md §13): every expense category with a field for its budget and the average of its
 * expenses in the last months closed; the sum of them, the total budget and what is left of it or how far they are over
 * it. With [month] null, the monthly budgets against the standard total budget; else those of the open [month] against
 * its own. All is saved at once, over the total budget too; leaving without saving discards it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetCalculatorScreen(ledger: Ledger, month: YearMonth?, onClose: () -> Unit) {
    var plan by remember { mutableStateOf<BudgetPlan?>(null) }
    // What is typed, kept while the screen turns; the budgets and total budget saved, until the plan is read.
    var typed by rememberSaveable { mutableStateOf<PlanTyped?>(null) }
    LaunchedEffect(ledger, month) {
        // A month closed meanwhile has no calculator.
        val read = try {
            ledger.budgetPlan(month)
        } catch (_: IllegalArgumentException) {
            return@LaunchedEffect onClose()
        }
        plan = read
        if (typed == null) typed = PlanTyped.of(read)
    }
    val closeWith = rememberClosing(onClose)
    val shown = plan
    val now = typed
    val check = if (shown != null && now != null) PlanCheck(shown, now) else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.budget_calculator), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { if (check != null && check.canSave) closeWith { ledger.saveBudgetPlan(check) } },
                        enabled = check?.canSave == true,
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { contentPadding ->
        if (check == null) return@Scaffold
        LazyColumn(contentPadding = contentPadding) {
            item(key = "about") {
                Text(
                    if (month == null) {
                        stringResource(R.string.budget_calculator_monthly)
                    } else {
                        stringResource(R.string.budget_calculator_month, month.monthAndYear())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "sum") { PlanSum(check) }
            item(key = "total") { TotalFields(check, onChange = { typed = it }) }
            item(key = "budgets-title") { SectionTitle(stringResource(R.string.ledger_by_category)) }
            items(check.plan.lines, key = { it.category.id }) { line ->
                BudgetField(line, check, onChange = { text -> typed = check.typed.withBudget(line.category.id, text) })
            }
        }
    }
}

/** The sum of the budgets, and against the total budget, if there is one, what is left or how far they are over it. */
@Composable
private fun PlanSum(check: PlanCheck) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row {
            Text(stringResource(R.string.budget_calculator_sum), modifier = Modifier.weight(1f))
            Text(Money.format(check.sum), style = MaterialTheme.typography.titleMedium)
        }
        val total = check.totalAmount ?: return@Column
        val left = checkNotNull(check.left)
        Row {
            Text(stringResource(R.string.total_budget), modifier = Modifier.weight(1f))
            Text(Money.format(total), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            if (left < 0) {
                stringResource(R.string.total_budget_over, Money.format(-left))
            } else {
                stringResource(R.string.total_budget_free, Money.format(left))
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The total budget to choose: as usual (a month only, naming the standard), none, a fixed amount or from income with a
 * saving, saying what income it reckons with.
 */
@Composable
private fun TotalFields(check: PlanCheck, onChange: (PlanTyped) -> Unit) {
    val typed = check.typed
    val plan = check.plan
    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.total_budget))
        if (plan.month != null) {
            RadioOption(
                stringResource(R.string.total_choice_as_usual_is, totalKindText(plan.standard)),
                selected = typed.choice == TotalChoice.AsUsual,
            ) { onChange(typed.withTotal(TotalChoice.AsUsual)) }
        }
        RadioOption(stringResource(R.string.total_choice_none), selected = typed.choice == TotalChoice.None) {
            onChange(typed.withTotal(TotalChoice.None))
        }
        RadioOption(stringResource(R.string.total_choice_fixed), selected = typed.choice == TotalChoice.Fixed) {
            onChange(typed.withTotal(TotalChoice.Fixed))
        }
        if (typed.choice == TotalChoice.Fixed) {
            AmountField(
                typed.amount,
                stringResource(R.string.total_budget_amount),
                check.amountProblem?.let { stringResource(budgetProblemText(it)) },
            ) { onChange(typed.copy(amount = it)) }
        }
        RadioOption(stringResource(R.string.total_choice_from_income), selected = typed.choice == TotalChoice.FromIncome) {
            onChange(typed.withTotal(TotalChoice.FromIncome))
        }
        if (typed.choice == TotalChoice.FromIncome) {
            AmountField(
                typed.saving,
                stringResource(R.string.total_budget_saving),
                check.savingProblem?.let { stringResource(budgetProblemText(it)) },
            ) { onChange(typed.copy(saving = it)) }
        }
        // What income it reckons with, as usual too.
        if (check.effective is TotalBudget.FromIncome) {
            Text(
                stringResource(
                    if (plan.month == null) R.string.total_budget_income_standard else R.string.total_budget_income,
                    plan.incomeMonth.monthAndYear(), Money.format(plan.income),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** The budget of a [line]'s category as typed, "ohne Budget" while empty, with the average of its expenses under it. */
@Composable
private fun BudgetField(line: PlanLine, check: PlanCheck, onChange: (String) -> Unit) {
    val problem = check.problems[line.category.id]
    val average = line.average
    val months = check.plan.averageMonths
    AmountField(
        check.typed.budgets[line.category.id].orEmpty(),
        line.category.name,
        problem = problem?.let { stringResource(budgetProblemText(it)) },
        placeholder = stringResource(R.string.budget_calculator_without),
        hint = when {
            average == null -> stringResource(R.string.budget_calculator_no_average)
            months == 1 -> stringResource(R.string.budget_calculator_average_one, Money.format(average))
            else -> stringResource(R.string.budget_calculator_average, Money.format(average), months)
        },
        onChange = onChange,
    )
}

/** A field for euros with a comma, [problem] under it if it has one, else [hint]. */
@Composable
private fun AmountField(
    value: String,
    label: String,
    problem: String?,
    placeholder: String? = null,
    hint: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        suffix = { Text("€") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = problem != null,
        supportingText = (problem ?: hint)?.let { { Text(it) } },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
