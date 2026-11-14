package de.emilyshub.ui.routines

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.Outlook
import de.emilyshub.routines.RoutineRuns
import de.emilyshub.routines.Routines
import de.emilyshub.routines.RunEnd
import de.emilyshub.routines.RunView
import de.emilyshub.routines.StepTime
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.launchNow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * The Ablauf-Modus (SPEC.md §2): the run through the checklist of the occurrence [occurrenceId] of the routine
 * [routineId], one step at a time, as [runs] keeps it. "Fertig" ticks the step off and "Schritt zurück" takes back the
 * last one ticked off here; a stopwatch counts from the run's first start, by [day], and a step with minutes shows a ring
 * with its time left, then its overtime. With the last step, "Geschafft", how long it took and the routine's streak, and
 * the way back to Heute ([onClose]). Should the occurrence end otherwise meanwhile, or the run be ended, the run closes,
 * saying why. Leaving it, the run goes on. The screen stays on while it shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(runs: RoutineRuns, day: Day, occurrenceId: Long, routineId: Long, onClose: () -> Unit) {
    KeepScreenOn()
    val view = remember(runs, occurrenceId, routineId) { runs.view(occurrenceId, routineId) }
        .collectAsStateWithLifecycle(initialValue = null).value
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((view as? RunView.Step)?.name ?: (view as? RunView.Done)?.name.orEmpty(), Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
        when (view) {
            null -> Unit
            is RunView.Step -> StepView(
                step = view,
                now = rememberSecond(day),
                zone = day.zone(),
                onDone = { scope.launchNow { runs.done(occurrenceId, view.item.itemId) } },
                onBack = { scope.launchNow { runs.back(occurrenceId) } },
                modifier = modifier,
            )
            is RunView.Done -> DoneView(view, onClose = onClose, modifier = modifier)
            is RunView.Ended -> Ended(view.why, onClose)
        }
    }
}

/**
 * A step at [now]: "Schritt 2 von 4" and the run's stopwatch, how many steps are left and, with minutes for all, when
 * they are done by; the step's ring with its time left or over, if it has minutes; the item large, wrapping and scrolling
 * if long, and under it "Fertig" and "Schritt zurück", always in view.
 */
@Composable
private fun StepView(step: RunView.Step, now: Instant, zone: ZoneId, onDone: () -> Unit, onBack: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(
            stringResource(R.string.run_step, step.number, step.count) + " · " + Duration.between(step.runStartedAt, now).stopwatch(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(step.outlook(now).text(zone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val time = step.time(now)) {
                is StepTime.Countdown -> TimeRing(progress = time.left.toMillis().toFloat() / time.of.toMillis(), time.left.countdown(), over = false)
                is StepTime.Overtime -> TimeRing(progress = 1f, "+" + time.over.stopwatch(), over = true)
                // The stopwatch above counts.
                is StepTime.Stopwatch -> Unit
            }
            Text(
                step.item.text,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
        ) {
            Text(stringResource(R.string.run_done), style = MaterialTheme.typography.titleLarge)
        }
        TextButton(onClick = onBack, enabled = step.canGoBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.run_back))
        }
    }
}

/**
 * The ring of a step's time: as full as [progress] of it is left, with the [time] in it; once [over], full, in a quiet
 * color rather than red, as it is to show the time, not to press (#102).
 */
@Composable
private fun TimeRing(progress: Float, time: String, over: Boolean) {
    val label = if (over) stringResource(R.string.run_overtime) + " " + time else time
    Box(Modifier.size(RING).semantics(mergeDescendants = true) { contentDescription = label }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(RING),
            color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            strokeWidth = 8.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(time, style = MaterialTheme.typography.displaySmall, color = if (over) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
    }
}

/** "noch 3 Schritte, fertig gegen 07:42", or without minutes for all of them only "noch 3 Schritte". */
@Composable
private fun Outlook.text(zone: ZoneId): String {
    val by = doneBy
    return if (by == null) {
        pluralStringResource(R.plurals.run_left, steps, steps)
    } else {
        pluralStringResource(R.plurals.run_left_done_by, steps, steps, by.atZone(zone).clockTime())
    }
}

/** "Geschafft", how long it took and the routine's streak, and the way back to Heute ([onClose]). */
@Composable
private fun DoneView(done: RunView.Done, onClose: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.run_finished), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.padding(8.dp))
        done.took?.let { Text(stringResource(R.string.run_took, it.stopwatch()), style = MaterialTheme.typography.titleMedium) }
        Text(pluralStringResource(R.plurals.run_streak, done.streak, done.streak), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(16.dp))
        Button(onClick = onClose) { Text(stringResource(R.string.run_to_today)) }
    }
}

/** The run closes, saying [why] in a sentence. */
@Composable
private fun Ended(why: RunEnd, onClose: () -> Unit) {
    val context = LocalContext.current
    val sentence = why.sentence()
    LaunchedEffect(why) {
        Toast.makeText(context, sentence, Toast.LENGTH_LONG).show()
        onClose()
    }
}

/** Why the run ended, in a sentence. */
@Composable
private fun RunEnd.sentence(): String = when (this) {
    RunEnd.Missed -> stringResource(R.string.run_ended_missed)
    RunEnd.Skipped -> stringResource(R.string.run_ended_skipped)
    is RunEnd.Covered -> stringResource(R.string.run_ended_covered, by)
    RunEnd.Dropped -> stringResource(R.string.run_ended_dropped)
    RunEnd.Paused -> stringResource(R.string.run_ended_paused)
    RunEnd.Archived -> stringResource(R.string.run_ended_archived)
    RunEnd.Gone -> stringResource(R.string.run_ended_gone)
    RunEnd.Stopped -> stringResource(R.string.run_ended_stopped)
}

/**
 * "Starten" at an open routine on Heute (SPEC.md §2): starts the run through its occurrence, or takes it up, and opens it
 * ([onOpen]). Another running, it asks first whether to end that one, naming its routine; one run runs at a time.
 */
@Composable
fun rememberStartRun(runs: RoutineRuns, routines: Routines, onOpen: (occurrenceId: Long, routineId: Long) -> Unit): (DueRoutine) -> Unit {
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf<Pair<DueRoutine, String>?>(null) }
    val start = { routine: DueRoutine ->
        scope.launchNow {
            runs.start(routine.occurrenceId, routine.routineId)
            onOpen(routine.occurrenceId, routine.routineId)
        }
        Unit
    }
    asking?.let { (routine, running) ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.run_other_title)) },
            text = { Text(stringResource(R.string.run_other_text, running, routine.name)) },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    start(routine)
                }) { Text(stringResource(R.string.run_other_confirm)) }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    return { routine ->
        scope.launchNow {
            val other = runs.other(routine.occurrenceId)
            if (other == null) start(routine) else asking = routine to routines.get(other.routineId)?.name.orEmpty()
        }
    }
}

/** The time now by [day], anew each second. */
@Composable
private fun rememberSecond(day: Day): Instant {
    var now by remember { mutableStateOf(day.now()) }
    LaunchedEffect(day) {
        while (true) {
            now = day.now()
            delay(SECOND)
        }
    }
    return now
}

/** Keeps the screen on while it shows (SPEC.md §2, Ablauf-Modus). */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** "4:05", or "1:04:05" from an hour on. */
private fun Duration.stopwatch(): String {
    val seconds = seconds.coerceAtLeast(0)
    val (hours, minutes, rest) = Triple(seconds / 3600, seconds / 60 % 60, seconds % 60)
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

/** As [stopwatch], a second begun counting as a whole one, so that "0:00" shows only once the time is over. */
private fun Duration.countdown(): String = plusMillis(SECOND - 1).withNanos(0).stopwatch()

private const val SECOND = 1_000L
private val RING = 180.dp
