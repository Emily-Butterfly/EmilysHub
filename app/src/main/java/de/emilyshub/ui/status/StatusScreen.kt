package de.emilyshub.ui.status

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
import de.emilyshub.reminders.Reminders
import de.emilyshub.routines.Routines
import de.emilyshub.status.Need
import de.emilyshub.status.Readiness
import de.emilyshub.status.Standing
import de.emilyshub.status.Use
import de.emilyshub.todos.Todos
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
fun StatusScreen(routines: Routines, todos: Todos, onProbe: () -> Unit, onBack: () -> Unit) {
    val readiness = rememberChecked { readiness() }
    val use = rememberUse(routines, todos)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkup_title), modifier = Modifier.semantics { heading() }) },
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
            Hint(stringResource(R.string.checkup_intro))
            if (use != null) Need.entries.forEach { NeedRow(it, readiness.current, use) }
            if (readiness.current.putsAppsToSleep) SamsungHint()
            Probe(onProbe, shows = readiness.current.standing(Need.Notifications) != Standing.NotAllowed)
        }
    }
}

/** How Android stands on [need], what it is for, and the way to its page in Android's settings. */
@Composable
private fun NeedRow(need: Need, readiness: Readiness, use: Use) {
    val context = LocalContext.current
    val standing = readiness.standing(need)
    val needed = readiness.needs(use, need)
    val status = when {
        standing == Standing.Allowed -> R.string.checkup_allowed
        standing == Standing.Off -> R.string.checkup_off
        needed -> R.string.checkup_lacking
        need.recommended -> R.string.checkup_recommended
        else -> R.string.checkup_not_needed
    }
    val allowed = standing == Standing.Allowed
    val color = if (!allowed && needed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val texts = need.texts
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (allowed) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (allowed) MaterialTheme.colorScheme.primary else color,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(texts.title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(status), style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
        Hint(stringResource(texts.purpose))
        if (standing == Standing.Off) Hint(stringResource(texts.off))
        val button = when (standing) {
            Standing.Allowed -> R.string.checkup_open
            Standing.NotAllowed -> texts.allow
            Standing.Off -> texts.turnOn
        }
        TextButton(onClick = { context.openSettings(need.settings(context, standing)) }) { Text(stringResource(button)) }
    }
}

/** How to keep a Samsung from putting the app to sleep, as it does with apps not opened for a while. */
@Composable
private fun SamsungHint() {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.checkup_samsung_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.checkup_samsung_text), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.checkup_samsung_steps), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { context.openSettings(SystemSettings.battery()) }) {
                Text(stringResource(R.string.checkup_samsung_open))
            }
        }
    }
}

/** Sends a reminder to try out, and says when it comes, or that it will not while notifications are not allowed. */
@Composable
private fun Probe(onProbe: () -> Unit, shows: Boolean) {
    var sent by rememberSaveable { mutableStateOf(false) }
    val seconds = Reminders.PROBE_DELAY.seconds.toInt()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.checkup_probe_title))
        Button(
            onClick = {
                onProbe()
                sent = true
            },
        ) { Text(stringResource(R.string.checkup_probe, seconds)) }
        if (sent) {
            Hint(
                when {
                    shows -> stringResource(R.string.checkup_probe_sent, seconds)
                    else -> stringResource(R.string.checkup_probe_not_shown)
                },
            )
        }
    }
}
