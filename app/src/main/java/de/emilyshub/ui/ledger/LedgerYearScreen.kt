package de.emilyshub.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.Money
import de.emilyshub.ledger.YearSummary
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.monthName
import de.emilyshub.ui.rememberToday
import java.time.Year
import java.time.YearMonth

/**
 * The year overview of the household book (SPEC.md §13), a year at a time: its income, expenses and balance; each
 * month with its own, tapping one shows it; and the year's expenses by category, each with its sum and share, the
 * largest first. Months open and closed count the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerYearScreen(
    ledger: Ledger,
    day: Day,
    year: Year,
    onYearChange: (Year) -> Unit,
    onOpenMonth: (YearMonth) -> Unit,
    onBack: () -> Unit,
) {
    // Looked at anew when the day changes, so that the fixed bookings of the new day are entered while it shows.
    val today = rememberToday(day)
    val summary by remember(ledger, year, today) { ledger.year(year) }.collectAsState(initial = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ledger_year), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(contentPadding = contentPadding) {
            item(key = "year") { YearSwitch(year, onYearChange) }
            // Of the year shown, not of the one before while it loads.
            val shown = summary?.takeIf { it.year == year } ?: return@LazyColumn
            item(key = "sums") { Sums(shown.income, shown.expenses, shown.balance) }
            item(key = "months-title") { SectionTitle(stringResource(R.string.ledger_year_months)) }
            items(shown.months, key = { it.month.toString() }) { sums ->
                ListItem(
                    headlineContent = { Text(sums.month.monthName()) },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.ledger_archive_sums,
                                Money.format(sums.income),
                                Money.format(sums.expenses),
                                Money.format(sums.balance),
                            ),
                        )
                    },
                    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.ledger_archive_open)) {
                        onOpenMonth(sums.month)
                    },
                )
            }
            item(key = "by-category") { ExpensesOfYear(shown) }
        }
    }
}

@Composable
private fun YearSwitch(year: Year, onYearChange: (Year) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        IconButton(onClick = { onYearChange(year.minusYears(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.ledger_year_previous))
        }
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        IconButton(onClick = { onYearChange(year.plusYears(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.ledger_year_next))
        }
    }
}

/**
 * The year's expenses by category: a bar each, as long as its share of the largest, with its sum and its share of
 * the year's expenses in words, so that the colours are not all there is to it.
 */
@Composable
private fun ExpensesOfYear(summary: YearSummary) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.ledger_year_by_category))
        val byCategory = summary.expensesByCategory
        if (byCategory.isEmpty()) {
            Text(
                stringResource(R.string.ledger_year_no_expenses),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@Column
        }
        val largest = byCategory.first().cents
        byCategory.forEachIndexed { index, total ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Row {
                    Text(total.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.ledger_year_share, Money.format(total.cents), summary.shareOf(total)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Bar(share = total.cents.toFloat() / largest, color = sliceColor(index))
            }
        }
    }
}
