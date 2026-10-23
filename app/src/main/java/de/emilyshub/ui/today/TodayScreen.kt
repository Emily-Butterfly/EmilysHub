package de.emilyshub.ui.today

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R
import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Routines
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.clockTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Today's routines in the order they are due, to tick off or skip (SPEC.md §1, Heute-Übersicht). */
@Composable
fun TodayScreen(routines: Routines, contentPadding: PaddingValues) {
    // A new day starts at midnight while the app stays open, or shows on return to the app.
    var today by remember { mutableStateOf(routines.today()) }
    LifecycleResumeEffect(routines) {
        today = routines.today()
        onPauseOrDispose {}
    }
    LaunchedEffect(routines) {
        while (true) {
            delay(routines.timeUntilTomorrow().toMillis() + MIDNIGHT_MARGIN_MILLIS)
            today = routines.today()
        }
    }

    val due by remember(today) { routines.dueOn(today) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val list = due
    when {
        list == null -> Unit
        list.isEmpty() -> PlaceholderScreen(stringResource(R.string.today_empty), contentPadding)
        else -> LazyColumn(contentPadding = contentPadding) {
            items(list, key = { it.occurrenceId }) { routine ->
                DueRoutineRow(
                    routine = routine,
                    onDoneChange = { done ->
                        scope.launch {
                            if (done) routines.markDone(routine.occurrenceId) else routines.reopen(routine.occurrenceId)
                        }
                    },
                    onSkip = { scope.launch { routines.skip(routine.occurrenceId) } },
                    onReopen = { scope.launch { routines.reopen(routine.occurrenceId) } },
                )
            }
        }
    }
}

/** Ticking the box marks it done, unticking undoes that; a skipped one offers to undo instead. */
@Composable
private fun DueRoutineRow(
    routine: DueRoutine,
    onDoneChange: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onReopen: () -> Unit,
) {
    val done = routine.status == OccurrenceStatus.Done
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = done, role = Role.Checkbox, onValueChange = onDoneChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = null)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                text = routine.dueAt.clockTime(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = routine.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
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

/** Shown under the name; "done" is the ticked box itself. */
private val OccurrenceStatus.label: Int?
    @StringRes get() = when (this) {
        OccurrenceStatus.Skipped -> R.string.status_skipped
        OccurrenceStatus.Missed -> R.string.status_missed
        OccurrenceStatus.Open, OccurrenceStatus.Done -> null
    }

/** The day may only just have changed when the delay ends early, so it waits a little longer. */
private const val MIDNIGHT_MARGIN_MILLIS = 1_000L
