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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.routines.Run
import de.emilyshub.routines.RunEnd
import de.emilyshub.routines.RunView
import de.emilyshub.routines.RoutineRuns
import de.emilyshub.ui.launchNow
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * The Ablauf-Modus (SPEC.md §2): the [run] through a routine's checklist, one step at a time, as [runs] has it. "Fertig"
 * ticks the step off and "Schritt zurück" takes back the last one ticked off here, each saying how the run goes on
 * ([onRunChange]); a stopwatch counts from its start, by [day]. With the last step, "Geschafft", how long it took and the
 * routine's streak, and the way back to Heute ([onClose]). Should the occurrence end otherwise meanwhile, the run closes,
 * saying why. The screen stays on while it shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunScreen(runs: RoutineRuns, day: Day, run: Run, onRunChange: (Run) -> Unit, onClose: () -> Unit) {
    KeepScreenOn()
    val view = remember(runs, run.occurrenceId) { runs.view(run) }.collectAsStateWithLifecycle(initialValue = null).value
    val scope = rememberCoroutineScope()
    val latestRun by rememberUpdatedState(run)
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
                took = rememberTook(day, run),
                canGoBack = run.canGoBack(view),
                onDone = { scope.launchNow { onRunChange(runs.done(latestRun, view)) } },
                onBack = { scope.launchNow { onRunChange(runs.back(latestRun, view)) } },
                modifier = modifier,
            )
            // Done otherwise, from its reminder say, it took until it was seen done.
            is RunView.Done -> DoneView(view, took = remember(run) { run.took(day.now()) }, onClose = onClose, modifier = modifier)
            is RunView.Ended -> Ended(view.why, onClose)
        }
    }
}

/**
 * A step: "Schritt 2 von 4" and the stopwatch, the item large, wrapping and scrolling if long, and under it "Fertig" and
 * "Schritt zurück", always in view.
 */
@Composable
private fun StepView(
    step: RunView.Step,
    took: Duration,
    canGoBack: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        Text(
            stringResource(R.string.run_step, step.number, step.count) + " · " + took.stopwatch(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
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
        TextButton(onClick = onBack, enabled = canGoBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.run_back))
        }
    }
}

/** "Geschafft", how long it [took] and the routine's streak, and the way back to Heute ([onClose]). */
@Composable
private fun DoneView(done: RunView.Done, took: Duration, onClose: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.run_finished), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.padding(8.dp))
        Text(stringResource(R.string.run_took, took.stopwatch()), style = MaterialTheme.typography.titleMedium)
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
}

/** How long the [run] has run so far, by [day], anew each second. */
@Composable
private fun rememberTook(day: Day, run: Run): Duration {
    var took by remember(run.startedAt) { mutableStateOf(run.took(day.now())) }
    LaunchedEffect(run.startedAt) {
        while (true) {
            took = run.took(day.now())
            delay(SECOND)
        }
    }
    return took
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

private const val SECOND = 1_000L
