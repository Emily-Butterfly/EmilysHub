package de.emilyshub.ui.tracking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.island.IslandSaver
import de.emilyshub.tracking.AbortCause
import de.emilyshub.tracking.EntryLimits
import de.emilyshub.tracking.StandPoint
import de.emilyshub.tracking.StandRun
import de.emilyshub.tracking.StandTestRules
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.tracking.TrackingStandTests
import de.emilyshub.ui.island.WhileMeasuring
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.island.touchesIsland
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Stehtest [id] (SPEC.md §17): while it runs, the assistant ([RunningStandTest]); once it ended, by its last point or an
 * abort, its result ([StandTestResultPage]).
 */
@Composable
internal fun StandTestPage(tracking: Tracking, modules: TrackingModules, id: Long, contentPadding: PaddingValues) {
    // Read anew on each change, so that each step saved shows the next.
    val current = rememberIslandLoad(tracking, id) { modules.standTests.run(id) } ?: return
    if (current.test.running) {
        RunningStandTest(tracking, modules.standTests, current, contentPadding)
    } else {
        StandTestResultPage(tracking, modules.standTests, id, contentPadding)
    }
}

/**
 * The assistant of a Stehtest that runs: the screen stays on and the five minutes rest ([WhileMeasuring]); "Abbrechen –
 * hinlegen" at the top at all times, also by back; lying with its timer, then each point at its minute with a tone and
 * a vibration. Should Tracking lock meanwhile, what was typed for the point is kept and the test counts as aborted.
 */
@Composable
private fun RunningStandTest(tracking: Tracking, standTests: TrackingStandTests, run: StandRun, contentPadding: PaddingValues) {
    WhileMeasuring(tracking)
    val signal = rememberMeasureSignal()
    val scope = rememberCoroutineScope()
    val now = rememberNow(standTests)
    var aborting by remember { mutableStateOf(false) }
    // What was typed for the point and not saved yet; no state, as nothing is shown of it here. Not [KeepIslandDraft]:
    // a point half typed is not saved as the page goes away, as on a rotation, only should the test end with it.
    val draft = remember { Draft() }
    val test = run.test
    DisposableEffect(test.id) {
        // Locked meanwhile, by the background or the screen going off: kept as it stands, and aborted.
        val saver = tracking.addSaver(
            IslandSaver {
                { database -> standTests.writeAbort(database, test.id, AbortCause.Locked, "", draft.point) }
            },
        )
        onDispose { saver.close() }
    }
    BackHandler { aborting = true }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = { aborting = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
        ) { Text(stringResource(R.string.tracking_stand_abort), style = MaterialTheme.typography.titleLarge) }
        val standingAt = test.standingAt
        if (standingAt == null) {
            Lying(standTests, run, now, signal, onDraft = { draft.point = it })
        } else {
            Standing(standTests, run, standingAt, now, signal, onDraft = { draft.point = it })
        }
    }
    if (aborting) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { aborting = false },
            title = { Text(stringResource(R.string.tracking_stand_abort_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tracking_stand_abort_text))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { if (it.length <= TrackingStandTests.REASON_MAX) reason = it },
                        label = { Text(stringResource(R.string.tracking_stand_abort_reason)) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    aborting = false
                    scope.launch { standTests.abort(test.id, AbortCause.Pressed, reason, draft.point) }
                }) { Text(stringResource(R.string.tracking_stand_abort_confirm)) }
            },
            dismissButton = { TextButton(onClick = { aborting = false }) { Text(stringResource(R.string.tracking_stand_abort_go_on)) } },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/** Lying down: the timer, a tone once it may stand up and once more at the most, then the lying values. */
@Composable
private fun Lying(
    standTests: TrackingStandTests,
    run: StandRun,
    now: Instant,
    signal: MeasureSignal,
    onDraft: (StandPoint?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val lying = Duration.between(run.test.startedAt, now)
    val mayStand = lying >= Duration.ofMinutes(StandTestRules.LYING_MINUTES.first.toLong())
    val lyingLongest = lying >= Duration.ofMinutes(StandTestRules.LYING_MINUTES.last.toLong())
    LaunchedEffect(mayStand) { if (mayStand) signal.play() }
    LaunchedEffect(lyingLongest) { if (lyingLongest) signal.play() }
    var pulse by remember { mutableStateOf("") }
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    val pulseNumber = TypedNumber(pulse, EntryLimits.PULSE, tenths = false)
    val systolicNumber = TypedNumber(systolic, EntryLimits.SYSTOLIC, tenths = false)
    val diastolicNumber = TypedNumber(diastolic, EntryLimits.DIASTOLIC, tenths = false)
    // Lying, nothing typed is kept should it lock: a test aborted lying down has no points.
    SideEffect { onDraft(null) }
    Text(stringResource(R.string.tracking_stand_lying), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.tracking_stand_lying_text), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.tracking_stand_lying_since, clock(lying)), style = MaterialTheme.typography.displaySmall)
    Text(
        stringResource(if (lyingLongest) R.string.tracking_stand_lying_long else if (!mayStand) R.string.tracking_stand_lying_wait else R.string.tracking_stand_point_now),
        style = MaterialTheme.typography.bodyMedium,
    )
    NumberField(stringResource(R.string.tracking_stand_pulse), pulse, { pulse = it }, pulseNumber.problem())
    NumberField(stringResource(R.string.tracking_systolic), systolic, { systolic = it }, systolicNumber.problem())
    NumberField(stringResource(R.string.tracking_diastolic), diastolic, { diastolic = it }, diastolicNumber.problem())
    Button(
        onClick = {
            scope.launch {
                standTests.standUp(run.test.id, pulseNumber.value, systolicNumber.value, diastolicNumber.value)
            }
        },
        enabled = mayStand && listOf(pulseNumber, systolicNumber, diastolicNumber).none { it.wrong },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.tracking_stand_stand_up)) }
}

/** Standing: the time since standing up, and the point measured next, with a tone and a vibration as it is due. */
@Composable
private fun Standing(
    standTests: TrackingStandTests,
    run: StandRun,
    standingAt: Instant,
    now: Instant,
    signal: MeasureSignal,
    onDraft: (StandPoint?) -> Unit,
) {
    val minute = run.nextMinute ?: return
    val standing = Duration.between(standingAt, now)
    val dueIn = Duration.ofMinutes(minute.toLong()).minus(standing)
    val due = !dueIn.isPositive
    LaunchedEffect(minute, due) { if (due) signal.play() }
    Text(stringResource(R.string.tracking_stand_standing_since, clock(standing)), style = MaterialTheme.typography.displaySmall)
    // A new point each minute: what was typed for the one before is gone with it.
    key(minute) {
        PointForm(standTests, run.test.id, minute, due, dueIn, onDraft)
    }
}

/** The values of the point at [minute], to save or to skip; [due] says it is time. */
@Composable
private fun PointForm(
    standTests: TrackingStandTests,
    testId: Long,
    minute: Int,
    due: Boolean,
    dueIn: Duration,
    onDraft: (StandPoint?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var pulse by remember { mutableStateOf("") }
    var systolic by remember { mutableStateOf("") }
    var diastolic by remember { mutableStateOf("") }
    var dizziness by remember { mutableStateOf<Int?>(null) }
    var palpitations by remember { mutableStateOf<Int?>(null) }
    var breathlessness by remember { mutableStateOf<Int?>(null) }
    var legs by remember { mutableStateOf<Boolean?>(null) }
    val pulseNumber = TypedNumber(pulse, EntryLimits.PULSE, tenths = false)
    val systolicNumber = TypedNumber(systolic, EntryLimits.SYSTOLIC, tenths = false)
    val diastolicNumber = TypedNumber(diastolic, EntryLimits.DIASTOLIC, tenths = false)
    val point = StandPoint(
        testId = testId,
        minute = minute,
        pulse = pulseNumber.value,
        systolic = systolicNumber.value,
        diastolic = diastolicNumber.value,
        dizziness = dizziness,
        palpitations = palpitations,
        breathlessness = breathlessness,
        legsDiscoloured = legs,
    )
    val typed = point != StandPoint(testId, minute)
    // After each composition, at once: should Tracking lock right after a value was typed, it is kept.
    SideEffect { onDraft(point.takeIf { typed }) }
    Text(stringResource(R.string.tracking_stand_point, minute), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text(
        if (due) stringResource(R.string.tracking_stand_point_now) else stringResource(R.string.tracking_stand_point_in, clock(dueIn)),
        style = MaterialTheme.typography.titleMedium,
        color = if (due) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NumberField(stringResource(R.string.tracking_stand_pulse), pulse, { pulse = it }, pulseNumber.problem())
    NumberField(stringResource(R.string.tracking_systolic), systolic, { systolic = it }, systolicNumber.problem())
    NumberField(stringResource(R.string.tracking_diastolic), diastolic, { diastolic = it }, diastolicNumber.problem())
    LevelField(stringResource(R.string.tracking_stand_dizziness), dizziness, { dizziness = it })
    LevelField(stringResource(R.string.tracking_stand_palpitations), palpitations, { palpitations = it })
    LevelField(stringResource(R.string.tracking_stand_breathlessness), breathlessness, { breathlessness = it })
    YesNoField(stringResource(R.string.tracking_stand_legs), legs, { legs = it })
    val save = { saved: StandPoint ->
        scope.launch {
            onDraft(null)
            standTests.record(saved)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { save(point) },
            // Only at its minute, so that the value is that minute's; before, it is shown when it is due.
            enabled = due && listOf(pulseNumber, systolicNumber, diastolicNumber).none { it.wrong },
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.tracking_stand_save_point)) }
        OutlinedButton(onClick = { save(StandPoint(testId, minute)) }, enabled = due) { Text(stringResource(R.string.tracking_stand_skip_point)) }
    }
}

/** What was typed for the point measured next, not saved yet. */
private class Draft {
    var point: StandPoint? = null
}

/** The moment it is, anew each quarter of a second. */
@Composable
private fun rememberNow(standTests: TrackingStandTests): Instant {
    var now by remember { mutableStateOf(standTests.now()) }
    LaunchedEffect(standTests) {
        while (true) {
            now = standTests.now()
            delay(TICK_MILLIS)
        }
    }
    return now
}

/** [duration] as minutes and seconds, "4:05". */
internal fun clock(duration: Duration): String {
    val seconds = duration.seconds.coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private const val TICK_MILLIS = 250L
