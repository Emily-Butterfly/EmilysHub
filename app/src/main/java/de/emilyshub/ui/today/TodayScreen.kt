package de.emilyshub.ui.today

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.StringRes
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R
import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Routines
import de.emilyshub.today.TodayEntry
import de.emilyshub.today.todayList
import de.emilyshub.todos.Due
import de.emilyshub.todos.DueTodo
import de.emilyshub.todos.Todos
import de.emilyshub.ui.ChecklistRow
import de.emilyshub.ui.Hint
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.PriorityNote
import de.emilyshub.ui.fields.priorityNameStyle
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.rememberNow
import de.emilyshub.ui.status.SetupWarning
import de.emilyshub.ui.todos.dueText
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * Today's routines and to-dos in the order they are due, the overdue to-dos at the top ([todayList]), to tick off, or
 * skip a routine (SPEC.md §1, Heute-Übersicht); above them, a warning while something the routines need from Android
 * is lacking, which leads to "Funktioniert alles?" ([onOpenStatus]).
 */
@Composable
fun TodayScreen(routines: Routines, todos: Todos, contentPadding: PaddingValues, onOpenStatus: () -> Unit) {
    val today = rememberToday(routines)
    // Looked at anew each minute, so that a to-do turning overdue moves to the top.
    val now = rememberNow(todos::now)
    val dueRoutines by remember(today) { routines.dueOn(today) }.collectAsState(initial = null)
    val dueTodos by remember(today, now) { todos.dueOn(today, now) }.collectAsState(initial = null)
    val list = dueRoutines?.let { routinesDue -> dueTodos?.let { todosDue -> todayList(routinesDue, todosDue, today) } }
    when {
        list == null -> Unit
        list.isEmpty() -> Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            SetupWarning(routines, todos, onCheck = onOpenStatus)
            Box(Modifier.weight(1f)) { PlaceholderScreen(stringResource(R.string.today_empty), PaddingValues()) }
        }
        else -> LazyColumn(contentPadding = contentPadding) {
            item(key = "setup-warning") { SetupWarning(routines, todos, onCheck = onOpenStatus) }
            items(list, key = { it.key }) { entry ->
                when (entry) {
                    is TodayEntry.OfRoutine -> RoutineEntry(entry.routine, today, routines)
                    is TodayEntry.OfTodo -> TodoEntry(entry.todo, today, todos)
                }
            }
        }
    }
}

/** A routine's occurrence on Heute, with its checklist under it. */
@Composable
private fun RoutineEntry(routine: DueRoutine, today: LocalDate, routines: Routines) {
    val scope = rememberCoroutineScope()
    Column {
        DueRoutineRow(
            routine = routine,
            fromDayBefore = routine.dueAt.toLocalDate().isBefore(today),
            onDoneChange = { done ->
                scope.launchNow {
                    if (done) routines.markDone(routine.occurrenceId) else routines.reopen(routine.occurrenceId)
                }
            },
            onSkip = { scope.launchNow { routines.skip(routine.occurrenceId) } },
            onReopen = { scope.launchNow { routines.reopen(routine.occurrenceId) } },
        )
        routine.checklist.forEach { entry ->
            ChecklistRow(entry, tickable = routine.status.checklistTickable) { ticked ->
                scope.launchNow { routines.tick(routine.occurrenceId, entry.itemId, ticked) }
            }
        }
    }
}

/** A to-do on Heute, with its checklist under it: ticked off, it is done and stays here, ticked, for the day. */
@Composable
private fun TodoEntry(todo: DueTodo, today: LocalDate, todos: Todos) {
    val scope = rememberCoroutineScope()
    Column {
        DueTodoRow(
            todo = todo,
            today = today,
            onDoneChange = { done -> scope.launchNow { if (done) todos.markDone(todo.todoId) else todos.reopen(todo.todoId) } },
        )
        todo.checklist.forEach { entry ->
            ChecklistRow(entry, tickable = true) { ticked -> scope.launchNow { todos.tick(todo.todoId, entry.itemId, ticked) } }
        }
    }
}

/**
 * A to-do's row on Heute: when it is due, in red while it is overdue, its title, crossed out once done, and the places it
 * waits for.
 */
@Composable
private fun DueTodoRow(todo: DueTodo, today: LocalDate, onDoneChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = todo.done, role = Role.Checkbox, onValueChange = onDoneChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
            PriorityNote(todo.priority)
            // It pops up on arrival at one of its places (SPEC.md §4).
            if (todo.waitingFor.isNotEmpty()) {
                Hint(stringResource(R.string.today_waiting_for, todo.waitingFor.joinToString(stringResource(R.string.names_or))))
            }
        }
    }
}

/** Tells the entries of Heute apart, routines' occurrences from to-dos. */
private val TodayEntry.key: String
    get() = when (this) {
        is TodayEntry.OfRoutine -> "routine:${routine.occurrenceId}"
        is TodayEntry.OfTodo -> "todo:${todo.todoId}"
    }

/**
 * Today's date, kept current while the screen shows: at midnight, on return to the app, and when the
 * phone's clock or time zone changes, after a flight for example.
 */
@Composable
private fun rememberToday(routines: Routines): LocalDate {
    var today by remember { mutableStateOf(routines.today()) }
    var clockChanges by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(routines) {
        today = routines.today()
        onPauseOrDispose {}
    }
    OnClockChange {
        today = routines.today()
        clockChanges++
    }
    // Restarted after a clock change, since the wait until midnight was measured on the old clock.
    LaunchedEffect(routines, clockChanges) {
        while (true) {
            delay(routines.timeUntilTomorrow().toMillis() + MIDNIGHT_MARGIN_MILLIS)
            today = routines.today()
        }
    }
    return today
}

/** Calls [onChange] when the time, the time zone or the date is changed by hand or by the network. */
@Composable
private fun OnClockChange(onChange: () -> Unit) {
    val context = LocalContext.current
    val currentOnChange by rememberUpdatedState(onChange)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = currentOnChange()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        // Not exported: the system still delivers its own broadcasts, other apps cannot send any (SPEC.md §9).
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
}

/** Ticking the box marks it done, unticking undoes that; a skipped one offers to undo instead. */
@Composable
private fun DueRoutineRow(
    routine: DueRoutine,
    fromDayBefore: Boolean,
    onDoneChange: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onReopen: () -> Unit,
) {
    val done = routine.status == OccurrenceStatus.Done
    // Missed is how it ended: it can no longer be ticked off.
    val tickable = routine.status != OccurrenceStatus.Missed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = done, enabled = tickable, role = Role.Checkbox, onValueChange = onDoneChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            PriorityNote(routine.priority)
            // Due elsewhere: it pops up on arrival at one of its places (SPEC.md §4).
            if (routine.waitingFor.isNotEmpty()) {
                Hint(stringResource(R.string.today_waiting_for, routine.waitingFor.joinToString(stringResource(R.string.names_or))))
            }
            routine.status.label?.let { Hint(stringResource(it)) }
        }
        when (routine.status) {
            OccurrenceStatus.Open -> TextButton(onClick = onSkip) { Text(stringResource(R.string.today_skip)) }
            OccurrenceStatus.Skipped -> TextButton(onClick = onReopen) { Text(stringResource(R.string.today_undo)) }
            OccurrenceStatus.Done, OccurrenceStatus.Missed -> Unit
        }
    }
}

/** Shown under the name; "done" is the ticked box itself. */
private val OccurrenceStatus.label: Int?
    @StringRes get() = when (this) {
        OccurrenceStatus.Skipped -> R.string.status_skipped
        OccurrenceStatus.Missed -> R.string.status_missed
        OccurrenceStatus.Open, OccurrenceStatus.Done -> null
    }

/** The day may only just have changed when the delay ends early, so it waits a little longer. */
private const val MIDNIGHT_MARGIN_MILLIS = 1_000L
