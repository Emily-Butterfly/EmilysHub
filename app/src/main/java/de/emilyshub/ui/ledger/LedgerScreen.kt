package de.emilyshub.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.ledger.BookingKind
import de.emilyshub.ledger.BookingWithCategory
import de.emilyshub.ledger.BudgetLevel
import de.emilyshub.ledger.CategoryTotal
import de.emilyshub.ledger.Carry
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.MonthClosing
import de.emilyshub.ledger.Money
import de.emilyshub.ui.Hint
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.monthAndYear
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.plusFabSpace
import de.emilyshub.ui.rememberToday
import java.time.YearMonth

/**
 * The household book (SPEC.md §13), a month at a time, the current one first: its opening balance, or the ways to get
 * one while it is open; its total budget as a bar, if it has one; its income, expenses and balance, and the end balance
 * that follows from an opening one; its expenses by category as bars, the largest first, and as a pie chart with its
 * legend; then all its bookings, the latest first. "+" adds a booking, tapping one opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    ledger: Ledger,
    day: Day,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenYear: () -> Unit,
    onOpenCalculator: (YearMonth) -> Unit,
    onBack: () -> Unit,
) {
    var confirmingClose by rememberSaveable { mutableStateOf(false) }
    var editingOpening by rememberSaveable { mutableStateOf(false) }
    var confirmingCarry by rememberSaveable { mutableStateOf(false) }
    var pickingBudget by rememberSaveable { mutableStateOf(false) }
    // The expense category whose budget in this month alone is being set.
    var budgeting by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    // Looked at anew when the day changes, so that the fixed bookings of the new day are entered while it shows, and
    // the month may be closed on its last day.
    val today = rememberToday(day)
    // All of one read, null until known, so that a closed month does not show what an open one has for a moment.
    val shown by remember(ledger, month, today) { ledger.month(month) }.collectAsState(initial = null)
    val offers = shown?.offers

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ledger_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenArchive) {
                        Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.ledger_archive))
                    }
                    IconButton(onClick = onOpenRecurring) {
                        Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.recurring_title))
                    }
                    IconButton(onClick = onOpenSubscriptions) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.subscriptions_title))
                    }
                    IconButton(onClick = onOpenCategories) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.ledger_categories))
                    }
                },
            )
        },
        floatingActionButton = {
            // Nothing is booked in a month closed.
            if (offers?.book == true) {
                FloatingActionButton(onClick = onCreate) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.booking_create))
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
            item(key = "month") { MonthSwitch(month, onMonthChange) }
            item(key = "year") {
                TextButton(onClick = onOpenYear, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.ledger_year_open, month.year))
                }
            }
            // Earlier months not closed yet, to find them easily.
            val others = shown?.stillOpen.orEmpty()
            if (others.isNotEmpty()) item(key = "still-open") { StillOpen(others, onMonthChange) }
            item(key = "closing") {
                when (offers?.closing) {
                    null, MonthClosing.None -> Unit
                    MonthClosing.Reopen -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.ledger_closed),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { confirmingClose = true }) { Text(stringResource(R.string.ledger_reopen)) }
                    }
                    MonthClosing.Close -> TextButton(
                        onClick = { confirmingClose = true },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text(stringResource(R.string.ledger_close)) }
                }
            }
            // Opened again after it was closed: what was not entered meanwhile, if wanted.
            val skipped = shown?.skipped ?: 0
            if (offers?.enterSkipped == true) {
                item(key = "skipped") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Hint(stringResource(R.string.ledger_skipped_hint))
                        TextButton(onClick = { scope.launchNow { ledger.enterSkipped(month) } }) {
                            Text(pluralStringResource(R.plurals.ledger_enter_skipped, skipped, skipped))
                        }
                    }
                }
            }
            val here = shown ?: return@LazyColumn
            val summary = here.summary
            val opening = here.opening
            val carry = here.offers.carry
            when {
                opening != null -> item(key = "opening") {
                    OpeningRow(opening, onEdit = if (here.offers.editOpening) ({ editingOpening = true }) else null)
                }
                carry != null -> item(key = "opening-offers") {
                    OpeningOffers(
                        onCarry = {
                            when (carry) {
                                Carry.AtOnce -> scope.launchNow { ledger.carryOver(here.month) }
                                Carry.AskFirst -> confirmingCarry = true
                            }
                        },
                        onSet = { editingOpening = true },
                    )
                }
            }
            here.totalUse?.let { use ->
                item(key = "total-budget") { TotalBar(use, onOpen = if (here.offers.budgets) ({ onOpenCalculator(month) }) else null) }
            }
            item(key = "sums") { Sums(summary.income, summary.expenses, summary.balance) }
            here.closingBalance?.let { closing -> item(key = "closing-balance") { ClosingRow(closing) } }
            here.foreseenEnd?.let { foreseen -> item(key = "foreseen") { ForeseenRow(foreseen) } }
            if (summary.expensesByCategory.isNotEmpty()) {
                item(key = "by-category") {
                    ExpensesByCategory(
                        summary.expensesByCategory,
                        summary.expenses,
                        onBudget = if (here.offers.budgets) ({ budgeting = it }) else null,
                    )
                }
            }
            if (here.offers.budgets) {
                item(key = "month-budgets") {
                    FlowRow(modifier = Modifier.padding(horizontal = 8.dp)) {
                        TextButton(onClick = { pickingBudget = true }) {
                            Text(stringResource(R.string.month_budgets, month.monthAndYear()))
                        }
                        TextButton(onClick = { onOpenCalculator(month) }) { Text(stringResource(R.string.budget_calculator)) }
                    }
                }
            }
            item(key = "bookings-title") { Column(Modifier.padding(horizontal = 16.dp)) { SectionTitle(stringResource(R.string.ledger_bookings)) } }
            if (summary.bookings.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.ledger_month_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(summary.bookings, key = { it.booking.id }) { BookingRow(it, onClick = { onOpen(it.booking.id) }) }
        }
    }

    val known = shown ?: return
    val can = known.offers
    when {
        // Reopening what is closed, closing what is open.
        confirmingClose && can.closing == MonthClosing.Reopen -> AlertDialog(
            onDismissRequest = { confirmingClose = false },
            title = { Text(stringResource(R.string.ledger_reopen_title, month.monthAndYear())) },
            text = { Text(stringResource(R.string.ledger_reopen_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClose = false
                        scope.launchNow { ledger.reopen(month) }
                    },
                ) { Text(stringResource(R.string.ledger_reopen)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClose = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
        confirmingClose && can.closing == MonthClosing.Close -> CloseDialog(
            known,
            onClose = { carryOver ->
                confirmingClose = false
                scope.launchNow { ledger.close(month, carryOver) }
            },
            onDismiss = { confirmingClose = false },
        )
        // Not in a month closed meanwhile.
        editingOpening && can.editOpening -> OpeningDialog(
            current = known.opening?.cents,
            onSave = { cents ->
                editingOpening = false
                scope.launchNow { ledger.setOpening(month, cents) }
            },
            onRemove = {
                editingOpening = false
                scope.launchNow { ledger.removeOpening(month) }
            },
            onDismiss = { editingOpening = false },
        )
        pickingBudget && can.budgets -> MonthBudgetsDialog(
            month,
            known.budgets,
            onPick = { id ->
                pickingBudget = false
                budgeting = id
            },
            onDismiss = { pickingBudget = false },
        )
        budgeting != null && can.budgets -> known.budgets.find { it.category.id == budgeting }?.let { budget ->
            MonthBudgetDialog(
                month,
                budget,
                onConfirm = { cents ->
                    budgeting = null
                    scope.launchNow { ledger.setBudgetIn(month, budget.category.id, cents) }
                },
                onDismiss = { budgeting = null },
            )
        }
        confirmingCarry && can.carry == Carry.AskFirst -> CarryDialog(
            known,
            onCarry = {
                confirmingCarry = false
                scope.launchNow { ledger.carryOver(month) }
            },
            onDismiss = { confirmingCarry = false },
        )
    }
}

/** The earlier [months] still open, a chip each, which shows it. */
@Composable
private fun StillOpen(months: List<YearMonth>, onMonthChange: (YearMonth) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.ledger_still_open), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Chips that lead there, not choices.
            months.forEach { month -> AssistChip(onClick = { onMonthChange(month) }, label = { Text(month.monthAndYear()) }) }
        }
    }
}

@Composable
private fun MonthSwitch(month: YearMonth, onMonthChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.ledger_month_previous))
        }
        Text(
            text = month.monthAndYear(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.ledger_month_next))
        }
    }
}

/** Income, expenses and the balance, each a figure with its name under it. */
@Composable
internal fun Sums(income: Long, expenses: Long, balance: Long) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Sum(stringResource(R.string.ledger_income), income, Modifier.weight(1f))
        Sum(stringResource(R.string.ledger_expenses), expenses, Modifier.weight(1f))
        Sum(stringResource(R.string.ledger_balance), balance, Modifier.weight(1f))
    }
}

@Composable
private fun Sum(label: String, cents: Long, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(Money.format(cents), style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The expenses by category: a bar each, and a pie chart of the shares, whose legend names each category with its
 * amount and share, so that the colours are not all there is to tell them apart. The bar of a category with a budget
 * shows how much of it is used and what is left, orange from 80 %, red above it, with how much it is over (SPEC.md
 * §13); that of one without is as long as its share of the largest. While the month is open ([onBudget]), tapping one
 * changes its budget in this month alone.
 */
@Composable
private fun ExpensesByCategory(byCategory: List<CategoryTotal>, expenses: Long, onBudget: ((Long) -> Unit)?) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.ledger_by_category))
        val largest = byCategory.first().cents
        byCategory.forEachIndexed { index, total ->
            val use = total.use
            val change = stringResource(R.string.month_budget_change)
            Column(
                Modifier
                    .then(if (onBudget == null) Modifier else Modifier.clickable(onClickLabel = change) { onBudget(total.category.id) })
                    .semantics(mergeDescendants = true) {},
            ) {
                Row {
                    Text(total.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    val amount = Money.format(total.cents)
                    Text(
                        if (use == null) amount else stringResource(R.string.budget_of, amount, Money.format(use.budget)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Bar(
                    share = use?.share ?: if (largest == 0L) 0f else total.cents.toFloat() / largest,
                    color = use?.let { budgetColor(it.level) } ?: sliceColor(index),
                )
                if (use != null) {
                    val over = use.level == BudgetLevel.Over
                    // In words too, so that the colour is not all that tells it.
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
        }
        // Those with a budget but nothing spent are last, and have no slice.
        val totals = byCategory.filter { it.cents > 0 }
        if (totals.isEmpty()) return@Column
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val description = stringResource(R.string.ledger_pie_description)
            PieChart(totals.map { it.cents }, Modifier.size(PIE_SIZE).semantics { contentDescription = description })
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                totals.forEachIndexed { index, total ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(sliceColor(index), CircleShape),
                        )
                        Text(
                            stringResource(R.string.ledger_share, total.category.name, Money.percent(total.cents, expenses)),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** A bar filled to [share] in [color]. */
@Composable
internal fun Bar(share: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(share)
                .height(8.dp)
                .background(color, RoundedCornerShape(4.dp)),
        )
    }
}

/** Orange from 80 % of a budget, red above it; within it, null: the colour of the category. */
@Composable
internal fun budgetColor(level: BudgetLevel): Color? = when (level) {
    BudgetLevel.Within -> null
    BudgetLevel.Near -> NEAR_BUDGET
    BudgetLevel.Over -> MaterialTheme.colorScheme.error
}

/** A pie of [values], drawn here, each slice in its colour, set apart from the next by a line in the background's. */
@Composable
private fun PieChart(values: List<Long>, modifier: Modifier) {
    val gap = MaterialTheme.colorScheme.surface
    val total = values.sum().toFloat()
    Canvas(modifier) {
        val diameter = size.minDimension
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = 360f * value / total
            drawArc(sliceColor(index), start, sweep, useCenter = true, topLeft = topLeft, size = Size(diameter, diameter))
            if (values.size > 1) {
                val line = Stroke(2.dp.toPx())
                drawArc(gap, start, sweep, useCenter = true, topLeft = topLeft, size = Size(diameter, diameter), style = line)
            }
            start += sweep
        }
    }
}

@Composable
private fun BookingRow(entry: BookingWithCategory, onClick: () -> Unit) {
    val (booking, category) = entry
    ListItem(
        headlineContent = { Text(booking.description.ifEmpty { category.name }) },
        supportingContent = {
            val parts = listOfNotNull(
                booking.date.numericDate(),
                category.name.takeIf { booking.description.isNotEmpty() },
                // Entered from a fixed booking (SPEC.md §13).
                stringResource(R.string.recurring_entered).takeIf { booking.recurringId != null },
            )
            Text(parts.joinToString(" · "))
        },
        trailingContent = {
            SignedAmount(category.kind, booking.amountCents)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** An amount of [kind], signed, so that the colour is not all that tells income from expenses. */
@Composable
internal fun SignedAmount(kind: BookingKind, cents: Long) {
    val income = kind == BookingKind.Income
    Text(
        text = if (income) "+${Money.format(cents)}" else Money.format(-cents),
        color = if (income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
    )
}

/**
 * The colour of the [index]th category: mid tones that stand out on the light background as on the dark one, after
 * the last of them the first again.
 */
internal fun sliceColor(index: Int): Color = SLICE_COLORS[index % SLICE_COLORS.size]

private val SLICE_COLORS = listOf(
    // Violet late, as the light and dark designs are lilac.
    Color(0xFF26A69A), Color(0xFFEF6C00), Color(0xFF42A5F5), Color(0xFFEC407A),
    Color(0xFF9CCC65), Color(0xFFFFB300), Color(0xFF8D6E63), Color(0xFF7E57C2),
)

private val PIE_SIZE = 120.dp

/** Orange, which stands out on the light background as on the dark one. */
private val NEAR_BUDGET = Color(0xFFF57C00)
