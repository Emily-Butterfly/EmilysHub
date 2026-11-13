package de.emilyshub.ui.today

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emilyshub.R
import de.emilyshub.ledger.SubscriptionReminder
import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.today.Heute
import de.emilyshub.today.HeuteDay
import de.emilyshub.today.PastSummary
import de.emilyshub.today.RoutineOffer
import de.emilyshub.today.TodayEntry
import de.emilyshub.today.catchUpTickable
import de.emilyshub.today.covered
import de.emilyshub.today.offer
import de.emilyshub.today.tickable
import de.emilyshub.todos.Due
import de.emilyshub.todos.DueTodo
import de.emilyshub.todos.RecurrenceEnd
import de.emilyshub.ui.ChecklistRow
import de.emilyshub.ui.Hint
import de.emilyshub.ui.MoreMenu
import de.emilyshub.ui.MoreMenuItem
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.dateLine
import de.emilyshub.ui.fields.PriorityNote
import de.emilyshub.ui.fields.endText
import de.emilyshub.ui.fields.priorityNameStyle
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.notes.PinnedNotes
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.offerUndo
import de.emilyshub.ui.shortWeekdayDayAndMonth
import de.emilyshub.ui.todos.TodoMenu
import de.emilyshub.ui.todos.dueText
import de.emilyshub.ui.todos.recurrenceText
import de.emilyshub.ui.todos.upcomingText
import java.time.LocalDate

/**
 * Which of Heute's blocks are open (SPEC.md §1, Drei Blöcke): [overdue] and [past]. Folded, whenever Heute shows anew.
 */
data class HeuteFolds(val overdue: Boolean = false, val past: Boolean = false)

/**
 * Heute as [day] has it (SPEC.md §1, Heute-Übersicht): the date and how far the day is at the top; the day's routines and
 * to-dos in their blocks ([de.emilyshub.today.TodayBlocks]), to tick off, or skip a routine, through [heute]: the overdue
 * to-dos and what is over each folded into one line, open as [folds] has it ([onFoldsChange]), and between them what is
 * still to do. Ticked off, an entry goes to what is over, with a bar to undo that. Above them the [warnings], while
 * something the reminders need from Android is lacking or the backups are amiss; the notes pinned here, which open with
 * a tap ([onOpenNote]); the to-dos shown before their day under "Demnächst", and there too the Abos to cancel soon,
 * which open with a tap ([onOpenSubscription], #80); and under it all, what was done each day of the last six months
 * ([TodayHeatmap]). Nothing until all of it is there, so that Heute does not say there is nothing before it comes.
 * Under the date, the line a thought is captured in and the way to the Eingang ([capture], SPEC.md §16), and the way
 * to yesterday, to catch up on it ([onOpenYesterday], [YesterdayScreen]).
 */
@Composable
fun TodayScreen(
    day: HeuteDay?,
    heute: Heute,
    contentPadding: PaddingValues,
    folds: HeuteFolds,
    onFoldsChange: (HeuteFolds) -> Unit,
    warnings: @Composable () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenYesterday: () -> Unit,
    onOpenSubscription: (Long) -> Unit = {},
    onOpenTodo: (Long) -> Unit = {},
    onOpenRoutine: (Long) -> Unit = {},
    capture: @Composable () -> Unit = {},
) {
    day ?: return
    val scope = rememberCoroutineScope()
    val act: Act = remember(heute, scope) { { action -> scope.launchNow { heute.action() } } }
    val snackbar = remember { SnackbarHostState() }
    val tickedMessage = stringResource(R.string.today_ticked)
    val movedMessage = stringResource(R.string.move_done)
    val undoLabel = stringResource(R.string.undo)
    val today = day.date
    val onHeute = remember(heute, scope, snackbar, tickedMessage, movedMessage, undoLabel, today, onOpenTodo, onOpenRoutine) {
        OnHeute(
            today = today,
            // Ticked off, it goes to what is over; the bar brings it back.
            tickOff = { entry ->
                scope.launchNow {
                    heute.onToday.setDone(entry, done = true)
                    snackbar.offerUndo(tickedMessage.format(entry.name), undoLabel) { heute.onToday.setDone(entry, done = false) }
                }
            },
            move = { todo, date ->
                scope.launchNow {
                    val move = heute.move(todo, date) ?: return@launchNow
                    snackbar.offerUndo(movedMessage.format(date.dateLine()), undoLabel) { heute.undoMove(move) }
                }
            },
            openTodo = onOpenTodo,
            openRoutine = onOpenRoutine,
        )
    }
    val blocks = day.blocks
    val upcoming = day.upcoming
    val pinned = day.pinned
    val subscriptions = day.subscriptions
    Box(Modifier.fillMaxSize()) {
        if (blocks.isEmpty && pinned.isEmpty() && upcoming.isEmpty() && subscriptions.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                DateLine(day)
                capture()
                ToYesterday(onOpenYesterday)
                warnings()
                Box(Modifier.weight(1f)) { PlaceholderScreen(stringResource(R.string.today_empty), PaddingValues()) }
                // What was done stays in view on a day with nothing due (SPEC.md §1).
                TodayHeatmap(day.heatmap)
            }
        } else {
            LazyColumn(contentPadding = contentPadding) {
                item(key = "date") { DateLine(day) }
                item(key = "capture") { Column { capture() } }
                item(key = "yesterday") { ToYesterday(onOpenYesterday) }
                item(key = "warnings") { Column { warnings() } }
                if (pinned.isNotEmpty()) {
                    item(key = "pinned-notes") { PinnedNotes(pinned, onOpen = onOpenNote, onUnpin = { act { unpin(it) } }) }
                }
                if (blocks.overdue.isNotEmpty()) {
                    item(key = "overdue") {
                        FoldLine(
                            title = stringResource(R.string.today_overdue, blocks.overdue.size),
                            open = folds.overdue,
                            onToggle = { onFoldsChange(folds.copy(overdue = !folds.overdue)) },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (folds.overdue) {
                        items(blocks.overdue, key = { it.key }) { entry -> TodoEntry(entry, today, act, onHeute) }
                    }
                }
                if (!blocks.isEmpty) {
                    item(key = "up-next") { BlockTitle(stringResource(R.string.today_up_next)) }
                }
                if (blocks.upNext.isEmpty()) {
                    item(key = "nothing-due") {
                        Text(
                            text = stringResource(if (blocks.isEmpty) R.string.today_empty else R.string.today_all_done),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(blocks.upNext, key = { it.key }) { entry -> Entry(entry, today, act, onHeute) }
                if (blocks.past.isNotEmpty()) {
                    item(key = "past") {
                        FoldLine(
                            title = stringResource(R.string.today_past),
                            summary = blocks.summary.text(),
                            open = folds.past,
                            onToggle = { onFoldsChange(folds.copy(past = !folds.past)) },
                        )
                    }
                    if (folds.past) {
                        items(blocks.past, key = { it.key }) { entry -> Entry(entry, today, act, onHeute) }
                    }
                }
                // Under the day's own entries, those due some days on that are to show before (SPEC.md §1).
                if (upcoming.isNotEmpty() || subscriptions.isNotEmpty()) {
                    item(key = "upcoming") { BlockTitle(stringResource(R.string.today_upcoming)) }
                    items(upcoming, key = { "upcoming:${it.todo.todoId}" }) { entry ->
                        TodoEntry(entry, today, act, onHeute, upcoming = true)
                    }
                    // The Abos to cancel soon (#80), from the first day of their lead to their last day.
                    items(subscriptions, key = { "subscription:${it.subscriptionId}" }) { reminder ->
                        SubscriptionRow(reminder, onOpen = { onOpenSubscription(reminder.subscriptionId) })
                    }
                }
                // Under all of the day's entries and those to come, what was done in the last six months (SPEC.md §1).
                item(key = "heatmap") { TodayHeatmap(day.heatmap) }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding),
        )
    }
}

/** The title of a part of Heute, as far in as the rows under it (Befund U11). */
@Composable
private fun BlockTitle(text: String) {
    Column(Modifier.padding(horizontal = 16.dp)) { SectionTitle(text) }
}

/** The date at the top of Heute, and how far the day is, while anything on it counts (SPEC.md §1, Kopfzeile). */
@Composable
private fun DateLine(day: HeuteDay) {
    val date = day.date.dateLine()
    val stand = day.blocks.stand
    Text(
        text = if (stand.total == 0) date else stringResource(R.string.today_stand, date, stand.done, stand.total),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
    )
}

/**
 * The line a block of Heute is folded into: its [title] and [summary], a heading that says whether it is [open]; tapped,
 * it opens or folds ([onToggle]).
 */
@Composable
private fun FoldLine(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    summary: String? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val state = stringResource(if (open) R.string.today_expanded else R.string.today_collapsed)
    val action = stringResource(if (open) R.string.today_collapse else R.string.today_expand)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = action, onClick = onToggle)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = state
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            summary?.let { Hint(it) }
        }
        Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
}

/** What is over, by its status, naming only what there is: 3 erledigt · 1 übersprungen. */
@Composable
private fun PastSummary.text(): String = listOfNotNull(
    done.takeIf { it > 0 }?.let { stringResource(R.string.today_past_done, it) },
    skipped.takeIf { it > 0 }?.let { stringResource(R.string.today_past_skipped, it) },
    missed.takeIf { it > 0 }?.let { stringResource(R.string.today_past_missed, it) },
    dropped.takeIf { it > 0 }?.let { stringResource(R.string.today_past_dropped, it) },
).joinToString(" · ")

/** An entry of Heute, a routine's occurrence or a to-do. */
@Composable
private fun Entry(entry: TodayEntry, today: LocalDate, act: Act, onHeute: OnHeute) = when (entry) {
    is TodayEntry.OfRoutine -> RoutineEntry(entry, today, act, onHeute)
    is TodayEntry.OfTodo -> TodoEntry(entry, today, act, onHeute)
}

/**
 * Yesterday, to catch up on (SPEC.md §1, Gestern nachtragen), as [heute] has it: plainly marked as such at the top,
 * with the way back to Heute ([onToday]); its routines and to-dos only, each to tick off late or untick, its checklist
 * too, but neither skipped nor dropped. Nothing until it is there; anew past midnight.
 */
@Composable
fun YesterdayScreen(heute: Heute, contentPadding: PaddingValues, onToday: () -> Unit) {
    val yesterday = remember(heute) { heute.yesterday() }.collectAsStateWithLifecycle(initialValue = null).value ?: return
    val scope = rememberCoroutineScope()
    val act: Act = remember(heute, scope) { { action -> scope.launchNow { heute.action() } } }
    LazyColumn(contentPadding = contentPadding) {
        item(key = "header") { YesterdayHeader(yesterday.date, onToday) }
        if (yesterday.entries.isEmpty()) {
            item(key = "nothing-due") {
                Text(
                    text = stringResource(R.string.yesterday_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(yesterday.entries, key = { it.key }) { entry ->
            when (entry) {
                is TodayEntry.OfRoutine -> RoutineEntry(entry, yesterday.date, act, onHeute = null, catchingUp = true)
                is TodayEntry.OfTodo -> TodoEntry(entry, yesterday.date, act, onHeute = null, catchingUp = true)
            }
        }
    }
}

/** The narrow line above Heute that leads to yesterday ([onClick]). */
@Composable
private fun ToYesterday(onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 4.dp)) {
        TextButton(onClick = onClick) { Text(stringResource(R.string.today_to_yesterday)) }
    }
}

/** What marks yesterday as such, the day it is, with the way back to Heute ([onToday]). */
@Composable
private fun YesterdayHeader(date: LocalDate, onToday: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.yesterday_title, date.shortWeekdayDayAndMonth()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
                    .semantics { heading() },
            )
            TextButton(onClick = onToday) { Text(stringResource(R.string.yesterday_to_today)) }
        }
    }
}

/** Heute as [heute] has it while it shows, collected only then, and read anew on return to the app; null until it is all there. */
@Composable
fun rememberHeute(heute: Heute): HeuteDay? =
    remember(heute) { heute.day() }.collectAsStateWithLifecycle(initialValue = null).value

/** Runs what is done with Heute, to the end even if Heute goes. */
private typealias Act = (suspend Heute.() -> Unit) -> Unit

/**
 * What an entry on Heute offers besides its box (SPEC.md §1): ticking it off, with a bar to undo that ([tickOff]), and
 * its menu, to [move] a to-do to another day from [today], with a bar to undo that too, and to open a to-do or routine in
 * its editor ([openTodo], [openRoutine]). Yesterday's entries, caught up on, offer none of it.
 */
private class OnHeute(
    val today: LocalDate,
    val tickOff: (TodayEntry) -> Unit,
    val move: (DueTodo, LocalDate) -> Unit,
    val openTodo: (Long) -> Unit,
    val openRoutine: (Long) -> Unit,
)

/** Ticks [entry] off through [onHeute] where there is one, or unticks it; otherwise as [catchingUp] has it. */
private fun setDone(entry: TodayEntry, done: Boolean, act: Act, onHeute: OnHeute?, catchingUp: Boolean) {
    if (done && onHeute != null) onHeute.tickOff(entry) else act { ticking(catchingUp).setDone(entry, done) }
}

/**
 * A routine's occurrence on Heute, with its checklist under it, but for one missed; ticked off through [onHeute] where
 * there is one, with its menu: to skip it or drop it while it is open, and to open it, from "⋮" or a long press. On the
 * day before when [catchingUp] on it, only to tick off late or untick ([DueRoutine.catchUpTickable]), its checklist too.
 */
@Composable
private fun RoutineEntry(entry: TodayEntry.OfRoutine, today: LocalDate, act: Act, onHeute: OnHeute?, catchingUp: Boolean = false) {
    val routine = entry.routine
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        DueRoutineRow(
            routine = routine,
            fromDayBefore = routine.dueAt.toLocalDate().isBefore(today),
            tickable = if (catchingUp) routine.catchUpTickable else routine.tickable,
            offer = if (catchingUp) RoutineOffer.None else routine.offer,
            onDoneChange = { done -> setDone(entry, done, act, onHeute, catchingUp) },
            onUndo = { act { undo(routine) } },
            onLongClick = onHeute?.let { { menuOpen = true } },
        ) {
            onHeute ?: return@DueRoutineRow
            MoreMenu(routine.name, menuOpen, onExpandedChange = { menuOpen = it }) { close ->
                if (routine.offer == RoutineOffer.SkipOrDrop) {
                    MoreMenuItem(stringResource(R.string.today_skip), close) { act { skip(routine) } }
                    MoreMenuItem(stringResource(R.string.today_drop), close) { act { drop(routine) } }
                }
                MoreMenuItem(stringResource(R.string.entry_open), close) { onHeute.openRoutine(routine.routineId) }
            }
        }
        // Missed is how it ended: its checklist is of no use any more.
        if (!catchingUp && routine.status == OccurrenceStatus.Missed) return@Column
        val checklistTickable = if (catchingUp) routine.catchUpTickable else routine.status.checklistTickable
        routine.checklist.forEach { item ->
            ChecklistRow(item, tickable = checklistTickable) { ticked ->
                act { ticking(catchingUp).tick(entry, item.itemId, ticked) }
            }
        }
    }
}

/**
 * A to-do on Heute, with its checklist under it, [upcoming] under "Demnächst" if so: ticked off through [onHeute] where
 * there is one, it is done and goes to what is over for the day; its menu, from "⋮" or a long press, moves it to another
 * day or opens it ([TodoMenu]). On the day before when [catchingUp] on it, ticked off as done then.
 */
@Composable
private fun TodoEntry(
    entry: TodayEntry.OfTodo,
    today: LocalDate,
    act: Act,
    onHeute: OnHeute?,
    upcoming: Boolean = false,
    catchingUp: Boolean = false,
) {
    val todo = entry.todo
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        DueTodoRow(
            todo = todo,
            today = today,
            upcoming = upcoming,
            onDoneChange = { done -> setDone(entry, done, act, onHeute, catchingUp) },
            onLongClick = onHeute?.let { { menuOpen = true } },
        ) {
            onHeute ?: return@DueTodoRow
            TodoMenu(
                title = todo.title,
                done = todo.done,
                due = todo.due.date,
                today = onHeute.today,
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                onMove = { date -> onHeute.move(todo, date) },
                onOpen = { onHeute.openTodo(todo.todoId) },
            )
        }
        todo.checklist.forEach { item ->
            ChecklistRow(item, tickable = true) { ticked ->
                act { ticking(catchingUp).tick(entry, item.itemId, ticked) }
            }
        }
    }
}

/** An Abo under "Demnächst" (#80): until when to cancel it, and its name; tapped, it opens. */
@Composable
private fun SubscriptionRow(reminder: SubscriptionReminder, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.today_subscription_cancel_by, reminder.lastDay.numericDate()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(reminder.name, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * A to-do's row on Heute: when it is due, in red while it is overdue, or in how many days if [upcoming]; its title,
 * crossed out once done, how it comes again, and the places it waits for. Tapped, it is ticked off or unticked; a long
 * press opens its [menu], if it has one ([onLongClick]).
 */
@Composable
private fun DueTodoRow(
    todo: DueTodo,
    today: LocalDate,
    upcoming: Boolean,
    onDoneChange: (Boolean) -> Unit,
    onLongClick: (() -> Unit)?,
    menu: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tickable(todo.done, enabled = true, onDoneChange, onLongClick, stringResource(R.string.entry_menu))
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = if (onLongClick == null) 16.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = todo.done, onCheckedChange = null)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            val due = todo.due
            val day = dueText(due, today).orEmpty()
            Text(
                text = when {
                    upcoming -> stringResource(R.string.today_todo_due, upcomingText(due, today).orEmpty())
                    todo.overdue -> stringResource(R.string.today_todo_overdue, day)
                    due is Due.At && due.date == today -> stringResource(R.string.today_todo_at, due.time.clockTime())
                    due is Due.On && due.date == today -> stringResource(R.string.today_todo)
                    else -> stringResource(R.string.today_todo_due, day)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (todo.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = todo.title,
                style = priorityNameStyle(todo.priority),
                textDecoration = if (todo.done) TextDecoration.LineThrough else null,
            )
            todo.recurrence?.let { Hint(recurrenceText(it, todo.end, todo.seriesNumber)) }
            PriorityNote(todo.priority)
            // It pops up on arrival at one of its places (SPEC.md §4).
            if (todo.waitingFor.isNotEmpty()) {
                Hint(stringResource(R.string.today_waiting_for, todo.waitingFor.joinToString(stringResource(R.string.names_or))))
            }
        }
        menu()
    }
}

/** Tells the entries of Heute apart, routines' occurrences from to-dos. */
private val TodayEntry.key: String
    get() = when (this) {
        is TodayEntry.OfRoutine -> "routine:${routine.occurrenceId}"
        is TodayEntry.OfTodo -> "todo:${todo.todoId}"
    }

/**
 * Ticking the box marks it done, unticking undoes that, while [tickable]; "Rückgängig" undoes a skip or drop, where it
 * [offer]s that; a long press opens its [menu], if it has one ([onLongClick]).
 */
@Composable
private fun DueRoutineRow(
    routine: DueRoutine,
    fromDayBefore: Boolean,
    tickable: Boolean,
    offer: RoutineOffer,
    onDoneChange: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onLongClick: (() -> Unit)?,
    menu: @Composable () -> Unit,
) {
    val done = routine.status == OccurrenceStatus.Done
    val covered = routine.covered
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tickable(done, enabled = tickable, onDoneChange, onLongClick, stringResource(R.string.entry_menu))
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = if (onLongClick == null) 16.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = null, enabled = tickable)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = when {
                    fromDayBefore -> stringResource(R.string.today_yesterday_at, routine.dueAt.clockTime())
                    else -> routine.dueAt.clockTime()
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = routine.name,
                style = priorityNameStyle(routine.priority),
                textDecoration = if (done || covered) TextDecoration.LineThrough else null,
            )
            routine.endsOn?.let { endText(RecurrenceEnd.OnDate(it))?.let { until -> Hint(until) } }
            PriorityNote(routine.priority)
            // Due elsewhere: it pops up on arrival at one of its places (SPEC.md §4).
            if (routine.waitingFor.isNotEmpty()) {
                Hint(stringResource(R.string.today_waiting_for, routine.waitingFor.joinToString(stringResource(R.string.names_or))))
            }
            if (covered) {
                Hint(stringResource(R.string.status_covered, routine.coveredBy.orEmpty()))
            } else {
                routine.status.label?.let { label ->
                    // Dropped is how the day ended, as missed would: in the color of the calendar's mark for it.
                    if (routine.status == OccurrenceStatus.Dropped) {
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    } else {
                        Hint(stringResource(label))
                    }
                }
            }
        }
        // Skipping and dropping are in the menu; undoing them stays at hand.
        if (offer == RoutineOffer.Undo) TextButton(onClick = onUndo) { Text(stringResource(R.string.today_undo)) }
        menu()
    }
}

/**
 * Tapped, ticks the box off or unticks it ([onDoneChange]) while [enabled]; a long press, also when not, does
 * [onLongClick], if there is one, as for the menu of an entry on Heute, which [menuLabel] names.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.tickable(
    done: Boolean,
    enabled: Boolean,
    onDoneChange: (Boolean) -> Unit,
    onLongClick: (() -> Unit)?,
    menuLabel: String,
): Modifier {
    if (onLongClick == null) return toggleable(value = done, enabled = enabled, role = Role.Checkbox, onValueChange = onDoneChange)
    return combinedClickable(
        role = Role.Checkbox,
        onLongClickLabel = menuLabel,
        onLongClick = onLongClick,
        onClick = { if (enabled) onDoneChange(!done) },
    ).semantics {
        toggleableState = ToggleableState(done)
        if (!enabled) disabled()
    }
}

/** Shown under the name; "done" is the ticked box itself. */
private val OccurrenceStatus.label: Int?
    @StringRes get() = when (this) {
        OccurrenceStatus.Skipped -> R.string.status_skipped
        OccurrenceStatus.Missed -> R.string.status_missed
        OccurrenceStatus.Dropped -> R.string.status_dropped
        OccurrenceStatus.Open, OccurrenceStatus.Done -> null
    }
