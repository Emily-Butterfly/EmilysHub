package de.emilyshub.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.EntryKind
import de.emilyshub.tracking.EntryLimits
import de.emilyshub.tracking.MorningEntry
import de.emilyshub.tracking.MorningSheet
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.ui.island.KeepIslandDraft

/**
 * The morning entry of [page]'s day (SPEC.md §17, "Morgens"): the resting pulse lying down, the blood pressure, the
 * night's sleep, how rested, and whether the pulse was measured otherwise than asked. Saved as it stands, also
 * unfinished, as the page closes and before Tracking locks; what is left empty is "nicht erfasst".
 */
@Composable
internal fun MorningPage(tracking: Tracking, modules: TrackingModules, page: TrackingPage.Morning, contentPadding: PaddingValues, onDone: () -> Unit) {
    var loaded by remember { mutableStateOf<MorningSheet?>(null) }
    LaunchedEffect(page) {
        val sheet = modules.entries.morning(page.day)
        if (sheet == null) onDone() else loaded = sheet
    }
    val sheet = loaded ?: return
    val original = sheet.entry
    // Not saveable, as all that is typed in Tracking: nothing of it goes into the saved state.
    var day by remember { mutableStateOf(original.date) }
    var pulse by remember { mutableStateOf(TypedNumber.textOf(original.restingPulse)) }
    var systolic by remember { mutableStateOf(TypedNumber.textOf(original.systolic)) }
    var diastolic by remember { mutableStateOf(TypedNumber.textOf(original.diastolic)) }
    var sleep by remember { mutableStateOf(TypedNumber.textOf(original.sleepMinutes?.let { it / MINUTES_PER_TENTH }, tenths = true)) }
    var sleepQuality by remember { mutableStateOf(original.sleepQuality) }
    var rested by remember { mutableStateOf(original.rested) }
    var measuredDifferently by remember { mutableStateOf(original.measuredDifferently) }
    val pulseNumber = TypedNumber(pulse, EntryLimits.PULSE, tenths = false)
    val systolicNumber = TypedNumber(systolic, EntryLimits.SYSTOLIC, tenths = false)
    val diastolicNumber = TypedNumber(diastolic, EntryLimits.DIASTOLIC, tenths = false)
    val sleepNumber = TypedNumber(sleep, EntryLimits.SLEEP_MINUTES.first / MINUTES_PER_TENTH..EntryLimits.SLEEP_MINUTES.last / MINUTES_PER_TENTH, tenths = true)
    val entry = MorningEntry(
        date = day,
        restingPulse = pulseNumber.value,
        systolic = systolicNumber.value,
        diastolic = diastolicNumber.value,
        sleepMinutes = sleepNumber.value?.let { it * MINUTES_PER_TENTH },
        sleepQuality = sleepQuality,
        rested = rested,
        measuredDifferently = measuredDifferently,
        recordedOn = original.recordedOn,
    )
    KeepIslandDraft(tracking, page, modules.writes) {
        if (entry == original) null else ({ database -> modules.entries.writeMorning(database, entry, original.date.takeIf { sheet.stored }) })
    }
    EntryPage(
        title = stringResource(R.string.tracking_morning),
        contentPadding = contentPadding,
        // Wrong numbers stay to be put right; leaving otherwise saves all but them.
        doneEnabled = listOf(pulseNumber, systolicNumber, diastolicNumber, sleepNumber).none { it.wrong },
        onDone = onDone,
    ) {
        EntryDayField(modules, EntryKind.Morning, day, original.date.takeIf { sheet.stored }, onChange = { day = it })
        Text(stringResource(R.string.tracking_morning_hint), style = MaterialTheme.typography.bodyMedium)
        NumberField(stringResource(R.string.tracking_resting_pulse), pulse, { pulse = it }, pulseNumber.problem())
        NumberField(stringResource(R.string.tracking_systolic), systolic, { systolic = it }, systolicNumber.problem())
        NumberField(stringResource(R.string.tracking_diastolic), diastolic, { diastolic = it }, diastolicNumber.problem())
        CheckField(stringResource(R.string.tracking_measured_differently), measuredDifferently, { measuredDifferently = it })
        NumberField(stringResource(R.string.tracking_sleep_hours), sleep, { sleep = it }, sleepNumber.problem(), decimals = true)
        LevelField(stringResource(R.string.tracking_sleep_quality), sleepQuality, { sleepQuality = it }, words = SLEEP_WORDS)
        YesNoField(stringResource(R.string.tracking_rested), rested, { rested = it })
    }
}

/**
 * A page of an entry: [title] with "Fertig" ([onDone]) beside it, enabled only as [doneEnabled] says, and below them
 * [content], which scrolls.
 */
@Composable
internal fun EntryPage(
    title: String,
    contentPadding: PaddingValues,
    doneEnabled: Boolean,
    onDone: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            // Outside the scrolling, so that nothing scrolls below the bars.
            .padding(contentPadding),
    ) {
        // Above the scrolling, so that "Fertig" stays in reach.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = onDone, enabled = doneEnabled) { Text(stringResource(R.string.tracking_done)) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
            Text(stringResource(R.string.tracking_empty_hint), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The sleep is typed in hours with a decimal, kept in minutes: a tenth of an hour is six. */
private const val MINUTES_PER_TENTH = 6
