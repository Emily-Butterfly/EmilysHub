package de.emilyshub.ui.ledger

import androidx.compose.foundation.clickable
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
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.monthAndYear
import java.time.YearMonth

/**
 * The archive of the household book (SPEC.md §13): the months closed, the latest first, each with its income, expenses
 * and balance; tapping one shows it, only to read.
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
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.ledger_archive_sums,
                                    Money.format(archived.income),
                                    Money.format(archived.expenses),
                                    Money.format(archived.balance),
                                ),
                            )
                        },
                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.ledger_archive_open)) { onOpen(month) },
                    )
                }
            }
        }
    }
}
