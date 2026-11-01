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
import de.emilyshub.reminders.putOffLabel
import java.time.Duration

/**
 * What the full-screen alarm shows of what it rings for, a routine or a to-do: its [title] and [text], and whether it can
 * be skipped, as a routine can and a to-do cannot. [key] tells them apart. While the phone is locked, what it allows
 * there, [onLockScreen]; with nothing, only that one is due.
 */
data class AlarmShown(
    val key: String,
    val title: String,
    val text: String?,
    val skippable: Boolean,
    val onLockScreen: LockScreenShown? = null,
    /** For a joint reminder of coupled routines (SPEC.md §3), each of them, with a "done" of its own. */
    val each: List<AlarmChoice> = emptyList(),
)

/** One of the routines a joint alarm rings for: its occurrence, by its [id], and its routine's [name]. */
data class AlarmChoice(val id: Long, val name: String)

/** What the locked alarm shows of one that allows its name there (SPEC.md §3): the name as [title], and its time, [text]. */
data class LockScreenShown(val title: String, val text: String?)

/**
 * The full-screen alarm (SPEC.md §3, §5, §9). While [locked], it only says "Erinnerung fällig", or the name and time
 * of one that allows them there, and offers to unlock or close; once unlocked, it shows what it rings for, [shown],
 * with "Erledigt · Später", and "Überspringen · Heute nicht" for a routine; for coupled routines due together, a
 * "done" for each of them ([onDoneOf]) instead of "Erledigt", the rest for all of them; after "Später" the choice
 * when to come back, out of [putOffChoices]; without any, there is no "Später".
 */
@Composable
fun AlarmScreen(
    shown: AlarmShown?,
    putOffChoices: List<Duration>,
    locked: Boolean,
    onUnlock: () -> Unit,
    onDone: () -> Unit,
    onDoneOf: (Long) -> Unit,
    onPutOff: (Duration) -> Unit,
    onSkip: () -> Unit,
    onDrop: () -> Unit,
    onClose: () -> Unit,
) {
    // For the reminder shown: another one coming first starts with its actions again.
    var choosingPutOff by rememberSaveable(shown?.key) { mutableStateOf(false) }
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
                    val onLockScreen = shown?.onLockScreen
                    if (onLockScreen != null) {
                        Title(onLockScreen.title)
                        onLockScreen.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
                    } else {
                        Title(stringResource(R.string.reminder_public))
                    }
                    WideButton(stringResource(R.string.alarm_unlock), onUnlock, primary = true)
                    TextButton(onClick = onClose) { Text(stringResource(R.string.alarm_close)) }
                }
                // Only for a moment, until the reminder is read.
                shown == null -> Unit
                choosingPutOff -> {
                    Title(shown.title)
                    Text(stringResource(R.string.reminder_put_off_when), style = MaterialTheme.typography.bodyLarge)
                    val context = LocalContext.current
                    putOffChoices.forEach { choice ->
                        WideButton(context.putOffLabel(choice), onClick = { onPutOff(choice) })
                    }
                    TextButton(onClick = { choosingPutOff = false }) { Text(stringResource(R.string.cancel)) }
                }
                else -> {
                    Title(shown.title)
                    shown.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
                    if (shown.each.isEmpty()) {
                        WideButton(stringResource(R.string.reminder_done), onDone, primary = true)
                    } else {
                        shown.each.forEach { choice ->
                            WideButton(stringResource(R.string.alarm_done_of, choice.name), { onDoneOf(choice.id) }, primary = true)
                        }
                    }
                    if (putOffChoices.isNotEmpty()) {
                        WideButton(stringResource(R.string.reminder_put_off), onClick = { choosingPutOff = true })
                    }
                    if (shown.skippable) {
                        TextButton(onClick = onSkip) { Text(stringResource(R.string.reminder_skip)) }
                        TextButton(onClick = onDrop) { Text(stringResource(R.string.reminder_drop)) }
                    }
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
