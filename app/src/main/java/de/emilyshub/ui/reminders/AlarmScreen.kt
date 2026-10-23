package de.emilyshub.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.reminders.Reminders
import de.emilyshub.reminders.putOffLabel
import de.emilyshub.routines.Reminder
import de.emilyshub.ui.clockTime
import java.time.Duration

/**
 * The full-screen alarm (SPEC.md §3, §9). While [locked], it only says "Erinnerung fällig" and offers to
 * unlock or close; once unlocked, it shows the [reminder] with "Erledigt · Später · Überspringen", and after
 * "Später" the choice when to come back.
 */
@Composable
fun AlarmScreen(
    reminder: Reminder?,
    locked: Boolean,
    onUnlock: () -> Unit,
    onDone: () -> Unit,
    onPutOff: (Duration) -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
) {
    var choosingPutOff by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                locked -> {
                    Title(stringResource(R.string.reminder_public))
                    WideButton(stringResource(R.string.alarm_unlock), onUnlock, primary = true)
                    TextButton(onClick = onClose) { Text(stringResource(R.string.alarm_close)) }
                }
                // Only for a moment, until the reminder is read.
                reminder == null -> Unit
                choosingPutOff -> {
                    Title(reminder.name)
                    Text(stringResource(R.string.reminder_put_off_when), style = MaterialTheme.typography.bodyLarge)
                    val context = LocalContext.current
                    Reminders.PUT_OFF_CHOICES.forEach { choice ->
                        WideButton(context.putOffLabel(choice), onClick = { onPutOff(choice) })
                    }
                    TextButton(onClick = { choosingPutOff = false }) { Text(stringResource(R.string.cancel)) }
                }
                else -> {
                    Title(reminder.name)
                    Text(
                        stringResource(R.string.reminder_due_at, reminder.dueAt.clockTime()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    WideButton(stringResource(R.string.reminder_done), onDone, primary = true)
                    WideButton(stringResource(R.string.reminder_put_off), onClick = { choosingPutOff = true })
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.reminder_skip)) }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun WideButton(label: String, onClick: () -> Unit, primary: Boolean = false) {
    val modifier = Modifier.fillMaxWidth()
    if (primary) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}
