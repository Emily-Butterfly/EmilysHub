package de.emilyshub.ui.ledger

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.emilyshub.R
import de.emilyshub.ledger.BookingKind
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.LedgerCategory
import de.emilyshub.ledger.categoryNameProblem
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.NameDialog
import de.emilyshub.ui.launchNow

/**
 * The categories of the household book (SPEC.md §13), those of expenses and those of income: each kind can have more,
 * tapping one renames it, deleting one with bookings asks first and moves them to "Sonstiges" of its kind, which stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerCategoriesScreen(ledger: Ledger, onBack: () -> Unit) {
    val all by remember(ledger) { ledger.categories() }.collectAsState(initial = null)
    var creating by rememberSaveable { mutableStateOf<BookingKind?>(null) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    // The category asked about, with how many bookings it has.
    var deleting by rememberSaveable { mutableStateOf<Pair<Long, Int>?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ledger_categories), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        val list = all ?: return@Scaffold
        LazyColumn(contentPadding = contentPadding) {
            BookingKind.entries.forEach { kind ->
                item(key = "title:$kind") {
                    SectionTitle(stringResource(if (kind == BookingKind.Expense) R.string.ledger_expenses else R.string.ledger_income))
                }
                items(list.filter { it.kind == kind }, key = { it.id }) { category ->
                    ListItem(
                        headlineContent = { Text(category.name) },
                        supportingContent = if (category.isOther) {
                            { Text(stringResource(R.string.category_other_stays)) }
                        } else {
                            null
                        },
                        trailingContent = if (category.isOther) {
                            null
                        } else {
                            {
                                IconButton(
                                    onClick = {
                                        scope.launchNow {
                                            val count = ledger.bookingsIn(category.id)
                                            if (count == 0) ledger.deleteCategory(category.id) else deleting = category.id to count
                                        }
                                    },
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.category_delete, category.name))
                                }
                            }
                        },
                        // "Sonstiges" keeps its name, as the bookings of a category deleted go there.
                        modifier = if (category.isOther) {
                            Modifier
                        } else {
                            Modifier.clickable(onClickLabel = stringResource(R.string.category_rename)) { renaming = category.id }
                        },
                    )
                }
                item(key = "create:$kind") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.category_create)) },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.clickable { creating = kind },
                    )
                }
            }
        }
    }

    val list = all.orEmpty()
    creating?.let { kind ->
        CategoryNameDialog(
            R.string.category_create, R.string.category_create_confirm, "", list.filter { it.kind == kind },
            onConfirm = { name ->
                creating = null
                scope.launchNow { ledger.createCategory(kind, name) }
            },
            onDismiss = { creating = null },
        )
    }
    list.find { it.id == renaming }?.let { category ->
        val others = list.filter { it.kind == category.kind && it.id != category.id }
        CategoryNameDialog(
            R.string.category_rename, R.string.category_rename_confirm, category.name, others,
            onConfirm = { name ->
                renaming = null
                scope.launchNow { ledger.renameCategory(category.id, name) }
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { (id, count) ->
        val category = list.find { it.id == id } ?: return@let
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(pluralStringResource(R.plurals.category_delete_text, count, count, category.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launchNow { ledger.deleteCategory(id) }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CategoryNameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    initial: String,
    others: List<LedgerCategory>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    NameDialog(
        title = title,
        confirm = confirm,
        label = R.string.category_name,
        taken = R.string.category_name_taken,
        max = LedgerCategory.NAME_MAX,
        initial = initial,
        problemOf = { categoryNameProblem(it, others) },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
