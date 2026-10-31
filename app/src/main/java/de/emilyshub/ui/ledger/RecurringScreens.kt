package de.emilyshub.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.ledger.BookingDraft
import de.emilyshub.ledger.BookingInterval
import de.emilyshub.ledger.BookingKind
import de.emilyshub.ledger.BookingProblem
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.Money
import de.emilyshub.ledger.RecurringBooking
import de.emilyshub.ledger.RecurringDraft
import de.emilyshub.ledger.RecurringProblem
import de.emilyshub.ui.Hint
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.NumberField
import de.emilyshub.ui.fields.RadioOption
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.monthAndYear
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.plusFabSpace
import java.io.Serializable
import java.time.YearMonth

/** What the editor of fixed bookings opens: a new one, or one saved already. */
sealed interface RecurringEditorTarget : Serializable {
    data object New : RecurringEditorTarget

    data class Edit(val recurringId: Long) : RecurringEditorTarget
}

/**
 * The fixed bookings of the household book (SPEC.md §13), the next due first, each with how often it comes and when
 * next: "+" adds one, tapping one opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringListScreen(ledger: Ledger, onOpen: (RecurringEditorTarget) -> Unit, onBack: () -> Unit) {
    val templates by remember(ledger) { ledger.templates() }.collectAsState(initial = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(RecurringEditorTarget.New) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recurring_create))
            }
        },
    ) { contentPadding ->
        val list = templates
        when {
            list == null -> Unit
            list.isEmpty() -> PlaceholderScreen(stringResource(R.string.recurring_empty), contentPadding)
            else -> LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
                items(list, key = { it.id }) { template ->
                    val draft = template.draft
                    ListItem(
                        headlineContent = { Text(draft.cleanedDescription.ifEmpty { template.category.name }) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.recurring_schedule, intervalLabel(draft.interval), draft.day, template.next.numericDate(),
                                ),
                            )
                        },
                        trailingContent = { SignedAmount(draft.kind, template.amountCents) },
                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.recurring_edit)) {
                            onOpen(RecurringEditorTarget.Edit(template.id))
                        },
                    )
                }
            }
        }
    }
}

/**
 * A fixed booking, new or saved (SPEC.md §13): a booking without a date, which comes every so often on a day of the
 * month from its first month on. A change holds from the next one entered; deleting asks first, and keeps those entered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditorScreen(ledger: Ledger, target: RecurringEditorTarget, onClose: () -> Unit) {
    val recurringId = (target as? RecurringEditorTarget.Edit)?.recurringId
    var draft by rememberSaveable {
        mutableStateOf(RecurringDraft(BookingKind.Expense, amount = "", firstMonth = ledger.thisMonth(), day = ledger.today().dayOfMonth))
    }
    var loaded by rememberSaveable { mutableStateOf(recurringId == null) }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var dayText by rememberSaveable { mutableStateOf(draft.day.toString()) }
    // The first month of the one saved, which it may keep, if it lies further back.
    var loadedFirstMonth by rememberSaveable { mutableStateOf<YearMonth?>(null) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val categories by remember(ledger) { ledger.categories() }.collectAsState(initial = emptyList())

    if (!loaded && recurringId != null) {
        LaunchedEffect(recurringId) {
            val saved = ledger.templateDraftOf(recurringId)
            if (saved == null) {
                onClose()
            } else {
                draft = saved
                dayText = saved.day.toString()
                loadedFirstMonth = saved.firstMonth
                loaded = true
            }
        }
        return
    }

    val problems = draft.problems
    val bookingProblems = draft.asBooking.problems
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
            closeWith { if (recurringId == null) ledger.createTemplate(saved) else ledger.updateTemplate(recurringId, saved) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (recurringId == null) R.string.recurring_new else R.string.recurring_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (recurringId != null) {
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
            if (recurringId != null) Hint(stringResource(R.string.recurring_edit_hint))
            KindChips(draft.kind) { draft = draft.copy(kind = it, categoryId = null) }
            AmountField(draft.amount, draft.asBooking.cents, bookingProblems, showProblems) { draft = draft.copy(amount = it) }
            CategoryChoice(categories, draft.kind, draft.categoryId, showProblems && RecurringProblem.CategoryMissing in problems) {
                draft = draft.copy(categoryId = it)
            }
            Column(Modifier.selectableGroup()) {
                SectionTitle(stringResource(R.string.recurring_interval))
                BookingInterval.entries.forEach { interval ->
                    RadioOption(intervalLabel(interval), selected = draft.interval == interval) { draft = draft.copy(interval = interval) }
                }
            }
            Column {
                NumberField(dayText, draft.day, RecurringBooking.DAYS, stringResource(R.string.recurring_day)) { text, day ->
                    dayText = text
                    draft = draft.copy(day = day)
                }
                if (draft.day > SHORTEST_MONTH) Hint(stringResource(R.string.recurring_day_short_months))
            }
            Column {
                SectionTitle(stringResource(R.string.recurring_first_month))
                // A year back at most, as all months since are entered at once; one saved keeps its own.
                val earliest = minOf(ledger.thisMonth().minusMonths(MONTHS_BACK), loadedFirstMonth ?: draft.firstMonth)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { draft = draft.copy(firstMonth = draft.firstMonth.minusMonths(1)) },
                        enabled = draft.firstMonth.isAfter(earliest),
                    ) {
                        val previous = stringResource(R.string.recurring_first_month_previous)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = previous)
                    }
                    Text(draft.firstMonth.monthAndYear(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { draft = draft.copy(firstMonth = draft.firstMonth.plusMonths(1)) }) {
                        val next = stringResource(R.string.recurring_first_month_next)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = next)
                    }
                }
            }
            OutlinedTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it) },
                label = { Text(stringResource(R.string.booking_description)) },
                singleLine = true,
                isError = BookingProblem.DescriptionTooLong in bookingProblems,
                supportingText = { LengthHint(InputText.lengthOf(draft.cleanedDescription), BookingDraft.DESCRIPTION_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmingDelete && recurringId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.recurring_delete_title)) },
            text = { Text(stringResource(R.string.recurring_delete_text)) },
            confirmButton = {
                TextButton(onClick = { closeWith { ledger.deleteTemplate(recurringId) } }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun intervalLabel(interval: BookingInterval): String = stringResource(
    when (interval) {
        BookingInterval.Monthly -> R.string.recurring_monthly
        BookingInterval.Quarterly -> R.string.recurring_quarterly
        BookingInterval.HalfYearly -> R.string.recurring_half_yearly
        BookingInterval.Yearly -> R.string.recurring_yearly
    },
)

private const val SHORTEST_MONTH = 28
private const val MONTHS_BACK = 12L
