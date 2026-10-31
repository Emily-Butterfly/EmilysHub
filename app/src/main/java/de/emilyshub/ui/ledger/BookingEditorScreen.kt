package de.emilyshub.ui.ledger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.ledger.BookingDraft
import de.emilyshub.ledger.BookingKind
import de.emilyshub.ledger.BookingProblem
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.Money
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.fields.RadioOption
import de.emilyshub.ui.launchNow
import java.io.Serializable

/** What the booking editor opens: a new booking, or one saved already. */
sealed interface BookingEditorTarget : Serializable {
    data object New : BookingEditorTarget

    data class Edit(val bookingId: Long) : BookingEditorTarget
}

/**
 * A booking of the household book (SPEC.md §13), new or saved: an expense or income, its amount in euros with a
 * comma, its date, today for a new one, its category of that kind and a description, if wanted. Deleting asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingEditorScreen(ledger: Ledger, target: BookingEditorTarget, onClose: () -> Unit) {
    val bookingId = (target as? BookingEditorTarget.Edit)?.bookingId
    var draft by rememberSaveable { mutableStateOf(ledger.newDraft(BookingKind.Expense)) }
    var loaded by rememberSaveable { mutableStateOf(bookingId == null) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var showProblems by rememberSaveable { mutableStateOf(false) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Saving and deleting close the editor at once; this keeps a second tap in the same frame from doing it twice.
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val categories by remember(ledger) { ledger.categories() }.collectAsState(initial = emptyList())

    if (!loaded && bookingId != null) {
        LaunchedEffect(bookingId) {
            val saved = ledger.draftOf(bookingId)
            if (saved == null) {
                onClose()
            } else {
                draft = saved
                loaded = true
            }
        }
        return
    }

    val problems = draft.problems
    val closeWith = { work: suspend () -> Unit ->
        if (!closing) {
            closing = true
            scope.launchNow { work() }
            onClose()
        }
    }
    val save = {
        if (problems.isNotEmpty()) {
            showProblems = true
        } else {
            val saved = draft
            closeWith { if (bookingId == null) ledger.create(saved) else ledger.update(bookingId, saved) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (bookingId == null) R.string.booking_new else R.string.booking_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (bookingId != null) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    TextButton(onClick = save) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookingKind.entries.reversed().forEach { kind ->
                    ChoiceChip(kindLabel(kind), chosen = draft.kind == kind) {
                        // A category of the other kind does not fit any more.
                        if (draft.kind != kind) draft = draft.copy(kind = kind, categoryId = null)
                    }
                }
            }
            val amountProblem = problems.firstOrNull {
                it in setOf(BookingProblem.AmountMissing, BookingProblem.AmountInvalid, BookingProblem.AmountNotAboveZero)
            }
            // A wrong amount is told at once; a missing one only after a try to save.
            val shownAmountProblem = amountProblem.takeIf { showProblems || it != BookingProblem.AmountMissing }
            OutlinedTextField(
                value = draft.amount,
                onValueChange = { typed -> draft = draft.copy(amount = typed.filter { it.isDigit() || it == ',' || it == '.' }) },
                label = { Text(stringResource(R.string.booking_amount)) },
                singleLine = true,
                suffix = { Text("€") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = shownAmountProblem != null,
                supportingText = {
                    Text(
                        when (shownAmountProblem) {
                            BookingProblem.AmountMissing -> stringResource(R.string.booking_amount_missing)
                            BookingProblem.AmountInvalid -> stringResource(R.string.booking_amount_invalid)
                            BookingProblem.AmountNotAboveZero -> stringResource(R.string.booking_amount_not_above_zero)
                            else -> draft.cents?.let(Money::format) ?: stringResource(R.string.booking_amount_hint)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            DateValue(stringResource(R.string.booking_date), draft.date, onClick = { choosingDate = true })
            Column(Modifier.selectableGroup()) {
                SectionTitle(stringResource(R.string.booking_category))
                categories.filter { it.kind == draft.kind }.forEach { category ->
                    RadioOption(category.name, selected = draft.categoryId == category.id) {
                        draft = draft.copy(categoryId = category.id)
                    }
                }
                if (showProblems && BookingProblem.CategoryMissing in problems) {
                    Problem(stringResource(R.string.booking_category_missing))
                }
            }
            OutlinedTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it) },
                label = { Text(stringResource(R.string.booking_description)) },
                singleLine = true,
                isError = BookingProblem.DescriptionTooLong in problems,
                supportingText = { LengthHint(InputText.lengthOf(draft.cleanedDescription), BookingDraft.DESCRIPTION_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (choosingDate) {
        DateDialog(
            initial = draft.date,
            onConfirm = {
                draft = draft.copy(date = it)
                choosingDate = false
            },
            onDismiss = { choosingDate = false },
        )
    }
    if (confirmingDelete && bookingId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.booking_delete_title)) },
            text = { Text(stringResource(R.string.booking_delete_text, draft.cents?.let(Money::format).orEmpty())) },
            confirmButton = {
                TextButton(onClick = { closeWith { ledger.delete(bookingId) } }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun kindLabel(kind: BookingKind): String = stringResource(
    when (kind) {
        BookingKind.Income -> R.string.booking_kind_income
        BookingKind.Expense -> R.string.booking_kind_expense
    },
)
