package de.emilyshub.ui.today

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import de.emilyshub.routines.ChecklistEntry
import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Routines
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.routines.PriorityNote
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Today's routines in the order they are due, to tick off or skip (SPEC.md §1, Heute-Übersicht). */
@Composable
fun TodayScreen(routines: Routines, contentPadding: PaddingValues) {
    val today = rememberToday(routines)
    val due by remember(today) { routines.dueOn(today) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val list = due
    when {
        list == null -> Unit
        list.isEmpty() -> PlaceholderScreen(stringResource(R.string.today_empty), contentPadding)
        else -> LazyColumn(contentPadding = contentPadding) {
            items(list, key = { it.occurrenceId }) { routine ->
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
                    // Skipped or missed, its checklist stays as it is.
                    val tickable = routine.status == OccurrenceStatus.Open || routine.status == OccurrenceStatus.Done
                    routine.checklist.forEach { entry ->
                        ChecklistRow(entry, tickable) { ticked ->
                            scope.launchNow { routines.tick(routine.occurrenceId, entry.itemId, ticked) }
                        }
                    }
                }
            }
        }
    }
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
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            PriorityNote(routine.priority)
            routine.status.label?.let { label ->
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (routine.status) {
            OccurrenceStatus.Open -> TextButton(onClick = onSkip) { Text(stringResource(R.string.today_skip)) }
            OccurrenceStatus.Skipped -> TextButton(onClick = onReopen) { Text(stringResource(R.string.today_undo)) }
            OccurrenceStatus.Done, OccurrenceStatus.Missed -> Unit
        }
    }
}

/** An item of the routine's checklist, under it: ticking off the last one ticks the routine off. */
@Composable
private fun ChecklistRow(entry: ChecklistEntry, tickable: Boolean, onTickedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = entry.ticked, enabled = tickable, role = Role.Checkbox, onValueChange = onTickedChange)
            .padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = entry.ticked, onCheckedChange = null, enabled = tickable)
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (entry.ticked) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(start = 16.dp),
        )
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
