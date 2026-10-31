package de.emilyshub.ui.ledger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.ledger.BookingKind
import de.emilyshub.ledger.BookingWithCategory
import de.emilyshub.ledger.CategoryTotal
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.Money
import de.emilyshub.ledger.MonthSummary
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.monthAndYear
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.plusFabSpace
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * The household book (SPEC.md §13), a month at a time, the current one first: its income, expenses and balance; its
 * expenses by category as bars, the largest first, and as a pie chart with its legend; then all its bookings, the
 * latest first. "+" adds a booking, tapping one opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    ledger: Ledger,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onOpenCategories: () -> Unit,
    onBack: () -> Unit,
) {
    val summary by remember(ledger, month) { ledger.month(month) }.collectAsState(initial = null)

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
                    IconButton(onClick = onOpenCategories) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.ledger_categories))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.booking_create))
            }
        },
    ) { contentPadding ->
        LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
            item(key = "month") { MonthSwitch(month, onMonthChange) }
            val shown = summary ?: return@LazyColumn
            item(key = "sums") { Sums(shown) }
            if (shown.expensesByCategory.isNotEmpty()) {
                item(key = "by-category") { ExpensesByCategory(shown.expensesByCategory, shown.expenses) }
            }
            item(key = "bookings-title") { SectionTitle(stringResource(R.string.ledger_bookings)) }
            if (shown.bookings.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.ledger_month_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(shown.bookings, key = { it.booking.id }) { BookingRow(it, onClick = { onOpen(it.booking.id) }) }
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
private fun Sums(summary: MonthSummary) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Sum(stringResource(R.string.ledger_income), summary.income, Modifier.weight(1f))
        Sum(stringResource(R.string.ledger_expenses), summary.expenses, Modifier.weight(1f))
        Sum(stringResource(R.string.ledger_balance), summary.balance, Modifier.weight(1f))
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
 * The expenses by category: a bar each, as long as its share of the largest, and a pie chart of the shares, whose
 * legend names each category with its amount and share, so that the colours are not all there is to tell them apart.
 */
@Composable
private fun ExpensesByCategory(totals: List<CategoryTotal>, expenses: Long) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.ledger_by_category))
        val largest = totals.first().cents
        totals.forEachIndexed { index, total ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row {
                    Text(total.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(Money.format(total.cents), style = MaterialTheme.typography.bodyMedium)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(total.cents.toFloat() / largest)
                            .height(8.dp)
                            .background(sliceColor(index), RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
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
                            stringResource(R.string.ledger_share, total.category.name, percent(total.cents, expenses)),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
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
    val income = category.kind == BookingKind.Income
    ListItem(
        headlineContent = { Text(booking.description.ifEmpty { category.name }) },
        supportingContent = {
            Text(if (booking.description.isEmpty()) booking.date.numericDate() else "${booking.date.numericDate()} · ${category.name}")
        },
        trailingContent = {
            Text(
                text = Money.format(if (income) booking.amountCents else -booking.amountCents),
                color = if (income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** [part] of [whole] in whole percent. */
private fun percent(part: Long, whole: Long): Int = if (whole == 0L) 0 else (part * 100.0 / whole).roundToInt()

/**
 * The colour of the [index]th category: mid tones that stand out on the light background as on the dark one, after
 * the last of them the first again.
 */
private fun sliceColor(index: Int): Color = SLICE_COLORS[index % SLICE_COLORS.size]

private val SLICE_COLORS = listOf(
    Color(0xFF7E57C2), Color(0xFF26A69A), Color(0xFFEF6C00), Color(0xFF42A5F5),
    Color(0xFFEC407A), Color(0xFF9CCC65), Color(0xFF8D6E63), Color(0xFFFFB300),
)

private val PIE_SIZE = 120.dp
