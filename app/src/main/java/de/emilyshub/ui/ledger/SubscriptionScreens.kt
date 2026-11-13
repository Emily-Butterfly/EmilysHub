package de.emilyshub.ui.ledger

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.data.Saving
import de.emilyshub.ledger.BookingInterval
import de.emilyshub.ledger.Deadline
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.Money
import de.emilyshub.ledger.NoticeUnit
import de.emilyshub.ledger.SubscriptionCost
import de.emilyshub.ledger.SubscriptionDraft
import de.emilyshub.ledger.SubscriptionEditing
import de.emilyshub.ledger.SubscriptionItem
import de.emilyshub.ledger.SubscriptionProblem
import de.emilyshub.ledger.SubscriptionStatus
import de.emilyshub.ledger.Subscriptions
import de.emilyshub.ui.Hint
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.differsAsSaved
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.fields.RadioOption
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.plusFabSpace
import de.emilyshub.ui.rememberClosing
import de.emilyshub.ui.rememberLeaving
import de.emilyshub.ui.rememberToday
import java.io.Serializable
import java.time.LocalDate

/** What the editor of Abos opens: a new one, linked to the fixed booking [recurringId] if given, or one saved already. */
sealed interface SubscriptionEditorTarget : Serializable {
    /** A new Abo, linked to the fixed booking [recurringId] if given. */
    data class New(val recurringId: Long? = null) : SubscriptionEditorTarget

    /** The Abo saved under [subscriptionId]; asking at once until when it was cancelled if [cancelling], as from its reminder (#80). */
    data class Edit(val subscriptionId: Long, val cancelling: Boolean = false) : SubscriptionEditorTarget
}

/**
 * The Abos of the household book (SPEC.md §13): on top what the running ones cost a month; then those, the next last day
 * to cancel first, each with that day and the days to it; then those cancelled, with the day they end. "+" adds one,
 * tapping one opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionListScreen(subscriptions: Subscriptions, day: Day, onOpen: (SubscriptionEditorTarget) -> Unit, onBack: () -> Unit) {
    // Looked at anew when the day changes, so that the days left are those of today.
    val today = rememberToday(day)
    val overview by remember(subscriptions, today) { subscriptions.overview() }.collectAsState(initial = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscriptions_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpen(SubscriptionEditorTarget.New()) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.subscription_create))
            }
        },
    ) { contentPadding ->
        val shown = overview ?: return@Scaffold
        if (shown.running.isEmpty() && shown.cancelled.isEmpty()) {
            PlaceholderScreen(stringResource(R.string.subscriptions_empty), contentPadding)
            return@Scaffold
        }
        LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
            item(key = "per-month") {
                Text(
                    stringResource(R.string.subscriptions_per_month, Money.format(shown.perMonth)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(shown.running, key = { it.subscription.id }) { SubscriptionRow(it, onOpen) }
            if (shown.cancelled.isNotEmpty()) {
                item(key = "cancelled") { SectionTitle(stringResource(R.string.subscriptions_cancelled)) }
                items(shown.cancelled, key = { it.subscription.id }) { SubscriptionRow(it, onOpen) }
            }
        }
    }
}

/** An Abo in its list: its name, how it stands, and what it costs, if known; tapping it opens it. */
@Composable
private fun SubscriptionRow(item: SubscriptionItem, onOpen: (SubscriptionEditorTarget) -> Unit) {
    val lapsed = item.status is SubscriptionStatus.Lapsed
    ListItem(
        headlineContent = { Text(item.subscription.name) },
        supportingContent = {
            Text(statusText(item.status), color = if (lapsed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = item.cost?.let { cost -> { Text(costText(cost)) } },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.subscription_edit)) {
            onOpen(SubscriptionEditorTarget.Edit(item.subscription.id))
        },
    )
}

/** How an Abo stands, in words. */
@Composable
internal fun statusText(status: SubscriptionStatus): String = when (status) {
    is SubscriptionStatus.Due -> if (status.daysLeft == 0L) {
        stringResource(R.string.subscription_due_today, status.lastDay.numericDate())
    } else {
        pluralStringResource(R.plurals.subscription_due, status.daysLeft.toInt(), status.lastDay.numericDate(), status.daysLeft.toInt())
    }
    is SubscriptionStatus.Lapsed -> stringResource(R.string.subscription_lapsed, status.cancelBy.numericDate())
    is SubscriptionStatus.RunsOn -> stringResource(R.string.subscription_runs_on)
    is SubscriptionStatus.Cancelled -> stringResource(R.string.subscription_ends, status.endsOn.numericDate())
}

/** "12,99 € Monatlich". */
@Composable
private fun costText(cost: SubscriptionCost): String = stringResource(R.string.subscription_cost, Money.format(cost.cents), intervalLabel(cost.interval))

/** A choice of date in the Abo's editor, and on which date its dialog is open. */
private enum class DateChoice { CancelBy, TermEnd, Cancelled }

/**
 * An Abo, new or saved (SPEC.md §13): its name, its amount and interval or the fixed booking it is linked to, a note,
 * its deadline as a single date or reckoned from its term with the last day that follows, and the days of lead. One
 * saved can be cancelled, let run on, its cancellation taken back, or deleted after asking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionEditorScreen(subscriptions: Subscriptions, ledger: Ledger, target: SubscriptionEditorTarget, onClose: () -> Unit) {
    val subscriptionId = (target as? SubscriptionEditorTarget.Edit)?.subscriptionId
    var editing by rememberSaveable { mutableStateOf<SubscriptionEditing?>(null) }
    var draft by rememberSaveable { mutableStateOf(subscriptions.newDraft()) }
    // The form as a new one starts out, from a fixed booking or empty, to tell whether anything was typed.
    var fresh by rememberSaveable { mutableStateOf(draft) }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    var refused by rememberSaveable { mutableStateOf(false) }
    var choosingDate by rememberSaveable { mutableStateOf<DateChoice?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var cancelling by rememberSaveable { mutableStateOf((target as? SubscriptionEditorTarget.Edit)?.cancelling == true) }
    // The day it was cancelled to end on, while asking whether its fixed booking ends then too.
    var endingRecurring by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val overview by remember(subscriptions) { subscriptions.overview() }.collectAsState(initial = null)
    val choices by remember(subscriptions, subscriptionId) { subscriptions.linkable(except = subscriptionId) }.collectAsState(initial = emptyList())

    val opened = editing
    if (opened == null) {
        LaunchedEffect(target) {
            when (target) {
                is SubscriptionEditorTarget.New -> {
                    target.recurringId?.let { id ->
                        subscriptions.draftFor(id)?.let {
                            draft = it
                            fresh = it
                        }
                    }
                    editing = SubscriptionEditing.New
                }
                is SubscriptionEditorTarget.Edit -> {
                    val saved = subscriptions.editing(target.subscriptionId)
                    if (saved == null) {
                        onClose()
                    } else {
                        draft = checkNotNull(saved.original)
                        editing = saved
                    }
                }
            }
        }
        return
    }

    val problems = draft.problems
    val item = overview?.let { it.running + it.cancelled }?.find { it.subscription.id == subscriptionId }
    val closeWith = rememberClosing(onClose)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    // Cancelled to end on a day: linked to a fixed booking, it asks next whether that ends then too, else it closes.
    val cancelOn = { saved: SubscriptionItem, endsOn: LocalDate ->
        if (saved.linked == null) {
            closeWith { subscriptions.cancel(saved.subscription.id, endsOn) }
        } else {
            scope.launchNow { subscriptions.cancel(saved.subscription.id, endsOn) }
            endingRecurring = endsOn
        }
    }
    val save = {
        if (problems.isNotEmpty()) {
            showProblems = true
        } else {
            val saved = draft
            closeWith.leaveThrough {
                when (subscriptions.save(opened, saved)) {
                    is Saving.Saved -> onClose()
                    // Its fixed booking took another Abo meanwhile.
                    is Saving.Refused -> refused = true
                }
            }
        }
    }
    val leave = rememberLeaving(
        closing = closeWith,
        changed = { differsAsSaved(draft, opened.original ?: fresh, SubscriptionDraft::cleaned) },
        onSave = save,
        onDiscard = onClose,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (subscriptionId == null) R.string.subscription_new else R.string.subscription_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (subscriptionId != null) {
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
            item?.let { saved -> StatusActions(saved, subscriptions, onCancel = { cancelling = true }, closeWith = { closeWith(it) }) }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.subscription_name)) },
                singleLine = true,
                isError = (showProblems && SubscriptionProblem.NameMissing in problems) || SubscriptionProblem.NameTooLong in problems,
                supportingText = {
                    if (showProblems && SubscriptionProblem.NameMissing in problems) {
                        Text(stringResource(R.string.subscription_name_missing))
                    } else {
                        LengthHint(InputText.lengthOf(draft.cleanedName), SubscriptionDraft.NAME_MAX)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            RecurringChoice(draft, choices.map { it.id to it.name }, refused) { draft = draft.copy(recurringId = it) }
            val linked = choices.find { it.id == draft.recurringId }
            if (linked != null) {
                Hint(stringResource(R.string.subscription_recurring_cost, costText(linked.cost)))
            } else {
                OwnCost(draft, problems) { draft = it }
            }
            OutlinedTextField(
                value = draft.note,
                onValueChange = { draft = draft.copy(note = it) },
                label = { Text(stringResource(R.string.subscription_note)) },
                isError = SubscriptionProblem.NoteTooLong in problems,
                supportingText = { LengthHint(InputText.lengthOf(draft.cleanedNote), SubscriptionDraft.NOTE_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
            DeadlineFields(draft, problems, showProblems, onChoose = { choosingDate = it }) { draft = it }
            OutlinedTextField(
                value = draft.lead,
                onValueChange = { typed -> draft = draft.copy(lead = typed.filter(Char::isDigit).take(DIGITS)) },
                label = { Text(stringResource(R.string.subscription_lead)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = SubscriptionProblem.LeadOutOfRange in problems,
                supportingText = {
                    Text(
                        if (SubscriptionProblem.LeadOutOfRange in problems) {
                            stringResource(R.string.subscription_range, SubscriptionDraft.LEAD.first, SubscriptionDraft.LEAD.last)
                        } else {
                            stringResource(R.string.subscription_lead_hint)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (val choice = choosingDate) {
        null -> Unit
        DateChoice.CancelBy, DateChoice.TermEnd -> DateDialog(
            initial = (if (choice == DateChoice.CancelBy) draft.cancelBy else draft.termEnd) ?: subscriptions.today(),
            onConfirm = { date ->
                draft = if (choice == DateChoice.CancelBy) draft.copy(cancelBy = date) else draft.copy(termEnd = date, termDay = null)
                choosingDate = null
            },
            onDismiss = { choosingDate = null },
        )
        DateChoice.Cancelled -> item?.let { saved ->
            DateDialog(
                initial = saved.endIfCancelled,
                onConfirm = { date ->
                    choosingDate = null
                    cancelling = false
                    cancelOn(saved, date)
                },
                onDismiss = { choosingDate = null },
            )
        }
    }
    if (cancelling && choosingDate == null) {
        // Not for one cancelled meanwhile, as its reminder may still have been up.
        item?.takeIf { it.status !is SubscriptionStatus.Cancelled }?.let { saved ->
            CancelDialog(
                saved,
                onCancel = { date ->
                    cancelling = false
                    cancelOn(saved, date)
                },
                onChooseDate = { choosingDate = DateChoice.Cancelled },
                onDismiss = { cancelling = false },
            )
        }
    }
    val linkedToEnd = item?.linked
    val endsOn = endingRecurring
    if (endsOn != null && linkedToEnd != null) {
        AlertDialog(
            onDismissRequest = { closeWith { } },
            title = { Text(stringResource(R.string.subscription_end_recurring_title)) },
            text = { Text(stringResource(R.string.subscription_end_recurring_text, linkedToEnd.name, endsOn.numericDate())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        closeWith {
                            // Not before its first booking: it says so, and the booking runs on.
                            if (!ledger.endTemplateOn(linkedToEnd.id, endsOn)) {
                                Toast.makeText(context, context.getString(R.string.subscription_end_recurring_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.subscription_end_recurring_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { closeWith { } }) { Text(stringResource(R.string.subscription_end_recurring_no)) }
            },
        )
    }
    if (confirmingDelete && subscriptionId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.subscription_delete_title)) },
            text = { Text(stringResource(R.string.subscription_delete_text)) },
            confirmButton = {
                TextButton(onClick = { closeWith { subscriptions.delete(subscriptionId) } }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * What can be done with the saved [item] as it stands: running, cancel it or let it run on; cancelled, take that back.
 * Its status above, in words.
 */
@Composable
private fun StatusActions(
    item: SubscriptionItem,
    subscriptions: Subscriptions,
    onCancel: () -> Unit,
    closeWith: (suspend () -> Unit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val lapsed = item.status is SubscriptionStatus.Lapsed
        Text(
            statusText(item.status),
            style = MaterialTheme.typography.titleMedium,
            color = if (lapsed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (item.status is SubscriptionStatus.Cancelled) {
            OutlinedButton(onClick = { closeWith { subscriptions.uncancel(item.subscription.id) } }) {
                Text(stringResource(R.string.subscription_uncancel))
            }
        } else {
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.subscription_cancel)) }
            if (item.status is SubscriptionStatus.Due) {
                OutlinedButton(onClick = { closeWith { subscriptions.runOn(item.subscription.id) } }) {
                    Text(stringResource(R.string.subscription_run_on))
                }
                Hint(stringResource(R.string.subscription_run_on_hint))
            }
        }
    }
}

/**
 * Cancelling [item]: a reckoned one ends with its term, a single date's on a day chosen, by default its date.
 */
@Composable
private fun CancelDialog(item: SubscriptionItem, onCancel: (LocalDate) -> Unit, onChooseDate: () -> Unit, onDismiss: () -> Unit) {
    val reckoned = item.subscription.deadline is Deadline.Term
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subscription_cancel_title, item.subscription.name)) },
        text = {
            Text(
                if (reckoned) {
                    stringResource(R.string.subscription_cancel_term, item.endIfCancelled.numericDate())
                } else {
                    stringResource(R.string.subscription_cancel_date, item.endIfCancelled.numericDate())
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onCancel(item.endIfCancelled) }) { Text(stringResource(R.string.subscription_cancel)) }
        },
        dismissButton = {
            Row {
                if (!reckoned) TextButton(onClick = onChooseDate) { Text(stringResource(R.string.field_date_change)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** The fixed booking an Abo is linked to, if any: none, or one of those free, the one it has among them. */
@Composable
private fun RecurringChoice(draft: SubscriptionDraft, choices: List<Pair<Long, String>>, refused: Boolean, onChange: (Long?) -> Unit) {
    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.subscription_recurring))
        RadioOption(stringResource(R.string.subscription_recurring_none), selected = draft.recurringId == null) { onChange(null) }
        choices.forEach { (id, name) -> RadioOption(name, selected = draft.recurringId == id) { onChange(id) } }
        if (refused) Problem(stringResource(R.string.subscription_recurring_taken))
    }
}

/** An amount of its own, if any, with its interval. */
@Composable
private fun OwnCost(draft: SubscriptionDraft, problems: Set<SubscriptionProblem>, onChange: (SubscriptionDraft) -> Unit) {
    val problem = when {
        SubscriptionProblem.AmountInvalid in problems -> R.string.booking_amount_invalid
        SubscriptionProblem.AmountNotAboveZero in problems -> R.string.booking_amount_not_above_zero
        else -> null
    }
    OutlinedTextField(
        value = draft.amount,
        onValueChange = { text -> onChange(draft.copy(amount = text.filter { it.isDigit() || it == ',' || it == '.' })) },
        label = { Text(stringResource(R.string.subscription_amount)) },
        singleLine = true,
        suffix = { Text("€") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = problem != null,
        supportingText = problem?.let { { Text(stringResource(it)) } } ?: draft.cents?.let { { Text(Money.format(it)) } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (draft.amount.isNotBlank()) {
        Column(Modifier.selectableGroup()) {
            SectionTitle(stringResource(R.string.recurring_interval))
            BookingInterval.entries.forEach { interval ->
                RadioOption(intervalLabel(interval), selected = draft.interval == interval) { onChange(draft.copy(interval = interval)) }
            }
        }
    }
}

/**
 * The deadline: a single date to cancel by, or reckoned from the end of the term with the notice in days, weeks or
 * months and the months it renews by, and the last day to cancel that follows.
 */
@Composable
private fun DeadlineFields(
    draft: SubscriptionDraft,
    problems: Set<SubscriptionProblem>,
    showProblems: Boolean,
    onChoose: (DateChoice) -> Unit,
    onChange: (SubscriptionDraft) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.subscription_deadline))
        RadioOption(stringResource(R.string.subscription_deadline_date), selected = !draft.reckoned) { onChange(draft.copy(reckoned = false)) }
        RadioOption(stringResource(R.string.subscription_deadline_term), selected = draft.reckoned) { onChange(draft.copy(reckoned = true)) }
    }
    if (!draft.reckoned) {
        DateChoiceValue(stringResource(R.string.subscription_cancel_by), draft.cancelBy, showProblems && SubscriptionProblem.CancelByMissing in problems) {
            onChoose(DateChoice.CancelBy)
        }
        return
    }
    DateChoiceValue(stringResource(R.string.subscription_term_end), draft.termEnd, showProblems && SubscriptionProblem.TermEndMissing in problems) {
        onChoose(DateChoice.TermEnd)
    }
    OutlinedTextField(
        value = draft.notice,
        onValueChange = { typed -> onChange(draft.copy(notice = typed.filter(Char::isDigit).take(DIGITS))) },
        label = { Text(stringResource(R.string.subscription_notice)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = SubscriptionProblem.NoticeOutOfRange in problems,
        supportingText = {
            Text(stringResource(R.string.subscription_range, draft.noticeUnit.range.first, draft.noticeUnit.range.last))
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Column(Modifier.selectableGroup()) {
        NoticeUnit.entries.forEach { unit ->
            RadioOption(stringResource(unitLabel(unit)), selected = draft.noticeUnit == unit) { onChange(draft.copy(noticeUnit = unit)) }
        }
    }
    OutlinedTextField(
        value = draft.renewal,
        onValueChange = { typed -> onChange(draft.copy(renewal = typed.filter(Char::isDigit).take(DIGITS))) },
        label = { Text(stringResource(R.string.subscription_renewal)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = SubscriptionProblem.RenewalOutOfRange in problems,
        supportingText = {
            Text(stringResource(R.string.subscription_range, SubscriptionDraft.RENEWAL.first, SubscriptionDraft.RENEWAL.last))
        },
        modifier = Modifier.fillMaxWidth(),
    )
    (draft.deadline as? Deadline.Term)?.let { term ->
        Text(stringResource(R.string.subscription_last_day, term.lastDay.numericDate()), style = MaterialTheme.typography.titleMedium)
    }
}

/** A date to choose, "nicht gewählt" while none is, with its problem if [missing]. */
@Composable
private fun DateChoiceValue(label: String, date: LocalDate?, missing: Boolean, onClick: () -> Unit) {
    Column {
        LabelledValue(
            label = label,
            value = date?.numericDate() ?: stringResource(R.string.subscription_date_none),
            modifier = Modifier.clickable(onClickLabel = stringResource(R.string.field_date_change), onClick = onClick),
        )
        if (missing) Problem(stringResource(R.string.subscription_date_missing))
    }
}

/** "Tage", "Wochen" or "Monate". */
private fun unitLabel(unit: NoticeUnit): Int = when (unit) {
    NoticeUnit.Days -> R.string.subscription_notice_days
    NoticeUnit.Weeks -> R.string.subscription_notice_weeks
    NoticeUnit.Months -> R.string.subscription_notice_months
}

private const val DIGITS = 3
