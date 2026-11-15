package de.emilyshub.ui.tracking

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.DeviceKind
import de.emilyshub.tracking.GarminRead
import de.emilyshub.tracking.GarminSample
import de.emilyshub.tracking.HealthAvailability
import de.emilyshub.tracking.HealthSource
import de.emilyshub.tracking.SampleKind
import de.emilyshub.tracking.SampleSource
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingGarmin
import de.emilyshub.ui.dateAndTime
import de.emilyshub.ui.island.rememberIslandLoad
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Garmin in Tracking's menu (SPEC.md §17, "Garmin über Health Connect"): where Health Connect cannot be read, why, and
 * that Tracking works without; else "Einrichten" for its permissions, then "Jetzt lesen" and "Woher kommen meine
 * Werte?": by kind, each app and device, how many and the last value, with its tier.
 */
@Composable
internal fun GarminPage(tracking: Tracking, garmin: TrackingGarmin, contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    var checks by remember { mutableIntStateOf(0) }
    var setUp by remember { mutableStateOf<Boolean?>(null) }
    var asked by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<GarminRead?>(null) }
    LaunchedEffect(checks) { setUp = garmin.isSetUp() }
    val request = rememberLauncherForActivityResult(garmin.permissionRequest()) {
        asked = true
        scope.launch {
            setUp = garmin.isSetUp()
            if (setUp == true) outcome = garmin.read()
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.tracking_garmin_intro))
        when (garmin.availability()) {
            HealthAvailability.Unavailable -> Text(stringResource(R.string.tracking_garmin_unavailable), color = MaterialTheme.colorScheme.error)
            HealthAvailability.NeedsUpdate -> Text(stringResource(R.string.tracking_garmin_update), color = MaterialTheme.colorScheme.error)
            HealthAvailability.Available -> when (setUp) {
                null -> Unit
                false -> {
                    Text(stringResource(R.string.tracking_garmin_set_up_hint))
                    if (asked) Text(stringResource(R.string.tracking_garmin_missing), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { request.launch(HealthSource.PERMISSIONS) }) { Text(stringResource(R.string.tracking_garmin_set_up)) }
                }
                true -> Reading(tracking, garmin, outcome, onRead = { scope.launch { outcome = garmin.read().also { checks++ } } })
            }
        }
        Text(stringResource(R.string.tracking_garmin_tiers_hint), style = MaterialTheme.typography.bodySmall)
    }
}

/** "Jetzt lesen", when it was last read and how the last reading went, and where the values come from. */
@Composable
private fun Reading(tracking: Tracking, garmin: TrackingGarmin, outcome: GarminRead?, onRead: () -> Unit) {
    val lastRead = rememberIslandLoad(tracking) { garmin.lastRead() }
    OutlinedButton(onClick = onRead) { Text(stringResource(R.string.tracking_garmin_read_now)) }
    Text(
        lastRead?.let { stringResource(R.string.tracking_garmin_last_read, it.atZone(ZoneId.systemDefault()).dateAndTime()) }
            ?: stringResource(R.string.tracking_garmin_never_read),
        style = MaterialTheme.typography.bodyMedium,
    )
    when (outcome) {
        is GarminRead.Read -> Text(stringResource(R.string.tracking_garmin_read_count, outcome.count), style = MaterialTheme.typography.bodyMedium)
        GarminRead.Failed -> Text(stringResource(R.string.tracking_garmin_read_failed), color = MaterialTheme.colorScheme.error)
        GarminRead.Locked, GarminRead.NotSetUp, null -> Unit
    }
    EntryHeading(stringResource(R.string.tracking_garmin_sources_title))
    val sources = rememberIslandLoad(tracking) { garmin.sources() } ?: return
    if (sources.isEmpty()) Text(stringResource(R.string.tracking_garmin_no_values))
    sources.groupBy { it.first.kind }.forEach { (kind, ofKind) ->
        Text(stringResource(kind.label()), style = MaterialTheme.typography.titleSmall)
        ofKind.forEach { (source, last) -> SourceLine(tracking, garmin, source, last) }
    }
}

/** An app and device that wrote values of a kind: how many, and the last with its tier. */
@Composable
private fun SourceLine(tracking: Tracking, garmin: TrackingGarmin, source: SampleSource, last: GarminSample?) {
    val tier = rememberIslandLoad(tracking, last) { last?.let { garmin.tierOf(it) } }
    Column {
        Text(
            stringResource(
                R.string.tracking_garmin_source_line,
                source.sourceApp.ifEmpty { stringResource(R.string.tracking_garmin_unknown_app) },
                stringResource(source.device.label()),
                source.count,
            ),
        )
        if (last != null) {
            val details = listOfNotNull(
                stringResource(R.string.tracking_garmin_last_value, last.shown(), last.start.atZone(ZoneId.systemDefault()).dateAndTime()),
                tier?.let { stringResource(R.string.tracking_garmin_tier, it.name) },
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [this] value as it reads: a heart rate per minute, steps as counted, a night in hours and minutes. */
private fun GarminSample.shown(): String = when (kind) {
    SampleKind.HeartRate, SampleKind.RestingHeartRate -> "$value/min"
    SampleKind.Steps -> value.toString()
    SampleKind.Sleep -> "%d:%02d h".format(value / 60, value % 60)
    SampleKind.SleepStage -> value.toString()
}

private fun SampleKind.label(): Int = when (this) {
    SampleKind.HeartRate -> R.string.tracking_garmin_kind_heart_rate
    SampleKind.RestingHeartRate -> R.string.tracking_garmin_kind_resting
    SampleKind.Steps -> R.string.tracking_garmin_kind_steps
    SampleKind.Sleep -> R.string.tracking_garmin_kind_sleep
    SampleKind.SleepStage -> R.string.tracking_garmin_kind_stage
}

private fun DeviceKind.label(): Int = when (this) {
    DeviceKind.Watch -> R.string.tracking_garmin_device_watch
    DeviceKind.ChestStrap -> R.string.tracking_garmin_device_chest_strap
    DeviceKind.Unknown -> R.string.tracking_garmin_device_unknown
}
