package de.emilyshub.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.emilyshub.R
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.Money
import de.emilyshub.ledger.MonthSums
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.monthAndYear
import java.time.YearMonth

/**
 * The archive of the household book (SPEC.md §13): the months closed, the latest first, each with its income, expenses
 * and balance, its end balance if it has an opening one, and what it spent of the total budget it kept; tapping one
 * shows it, only to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerArchiveScreen(ledger: Ledger, onOpen: (YearMonth) -> Unit, onBack: () -> Unit) {
    val months by remember(ledger) { ledger.archive() }.collectAsState(initial = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ledger_archive), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        val list = months
        when {
            list == null -> Unit
            list.isEmpty() -> PlaceholderScreen(stringResource(R.string.ledger_archive_empty), contentPadding)
            else -> LazyColumn(contentPadding = contentPadding) {
                items(list, key = { it.month.toString() }) { archived ->
                    val month = archived.month
                    ListItem(
                        headlineContent = { Text(month.monthAndYear()) },
                        supportingContent = { MonthSumsText(archived) },
                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.ledger_archive_open)) { onOpen(month) },
                    )
                }
            }
        }
    }
}

/** A month's sums in words ([sumsText]), and what it spent of its total budget, if it has one, under them. */
@Composable
internal fun MonthSumsText(sums: MonthSums) {
    Column {
        Text(sumsText(sums))
        sums.totalUse?.let { TotalBudgetLine(it) }
    }
}

/** A month's income, expenses and balance in words, with its end balance if it has an opening balance. */
@Composable
internal fun sumsText(sums: MonthSums): String {
    val (income, expenses, balance) = Triple(Money.format(sums.income), Money.format(sums.expenses), Money.format(sums.balance))
    val closing = sums.closingBalance
    return if (closing == null) {
        stringResource(R.string.ledger_archive_sums, income, expenses, balance)
    } else {
        stringResource(R.string.ledger_archive_sums_closing, income, expenses, balance, Money.format(closing))
    }
}
