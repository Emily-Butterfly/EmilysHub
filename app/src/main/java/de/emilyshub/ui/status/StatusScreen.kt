package de.emilyshub.ui.status

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.Routines
import de.emilyshub.status.Need
import de.emilyshub.status.Readiness
import de.emilyshub.status.Use
import de.emilyshub.ui.Hint
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.SystemSettings
import de.emilyshub.ui.openSettings
import de.emilyshub.ui.rememberChecked

/**
 * "Funktioniert alles?" (SPEC.md §11): each of what the reminders need from Android, whether Android allows it, what
 * it is for, and the way to its page in Android's settings, read again on every return from there; on a Samsung, how
 * to keep it from putting the app to sleep; and a reminder to try out, which [onProbe] sends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(routines: Routines, onProbe: () -> Unit, onBack: () -> Unit) {
    val readiness = rememberChecked { readiness() }
    val use = rememberUse(routines)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.status_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
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
            Hint(stringResource(R.string.status_intro))
            if (use != null) Need.entries.forEach { NeedRow(it, readiness.current, use) }
            if (Build.MANUFACTURER.equals(SAMSUNG, ignoreCase = true)) SamsungHint()
            Probe(onProbe)
        }
    }
}

/** Whether Android allows [need], what it is for, and, if it does not, the way to allow it. */
@Composable
private fun NeedRow(need: Need, readiness: Readiness, use: Use) {
    val context = LocalContext.current
    val has = readiness.has(need)
    val calledFor = need in use.calledFor()
    val status = when {
        has -> R.string.status_allowed
        need == Need.NoBatteryLimits -> R.string.status_recommended
        calledFor -> R.string.status_lacking
        else -> R.string.status_not_needed
    }
    val color = if (!has && calledFor) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (has) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (has) MaterialTheme.colorScheme.primary else color,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(need.title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(status), style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
        Hint(stringResource(need.purpose))
        // Allowed always, but turned off: the location is turned on elsewhere.
        val locationOff = need == Need.LocationAlways && need in readiness.allowed && !readiness.locationOn
        when {
            has -> Unit
            locationOff -> {
                Hint(stringResource(R.string.need_location_off))
                TextButton(onClick = { context.openSettings(SystemSettings.location()) }) {
                    Text(stringResource(R.string.location_off_open))
                }
            }
            else -> TextButton(onClick = { context.openSettings(need.settings(context)) }) { Text(stringResource(need.allow)) }
        }
    }
}

/** How to keep a Samsung from putting the app to sleep, as it does with apps not opened for a while. */
@Composable
private fun SamsungHint() {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.status_samsung_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.status_samsung_text), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.status_samsung_steps), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { context.openSettings(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }) {
                Text(stringResource(R.string.status_samsung_open))
            }
        }
    }
}

/** Sends a reminder to try out, and says when it comes. */
@Composable
private fun Probe(onProbe: () -> Unit) {
    var sent by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.status_probe_title))
        Button(
            onClick = {
                onProbe()
                sent = true
            },
        ) { Text(stringResource(R.string.status_probe)) }
        if (sent) Hint(stringResource(R.string.status_probe_sent))
    }
}

private const val SAMSUNG = "samsung"
