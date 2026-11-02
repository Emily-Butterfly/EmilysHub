package de.emilyshub.ui.reminders

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.emilyshub.reminders.RemindingStyle
import de.emilyshub.reminders.ToneCollection
import de.emilyshub.ui.routines.FullScreenField
import de.emilyshub.ui.routines.NameOnLockScreenField

/**
 * How a routine or to-do reminds (SPEC.md §3), the same section in both editors: as a full-screen alarm or not, with
 * [fullScreenNote] under it while it is one, if there is any to say; its name on the lock screen or not; and its tone,
 * from [tones]. Each a field of the parent's, so that they are spaced as the others.
 */
@Composable
fun RemindingStyleFields(
    style: RemindingStyle,
    tones: ToneCollection,
    onChange: (RemindingStyle) -> Unit,
    fullScreenNote: String? = null,
) {
    FullScreenField(style.fullScreen, onChange = { onChange(style.copy(fullScreen = it)) })
    if (style.fullScreen && fullScreenNote != null) {
        Text(fullScreenNote, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    NameOnLockScreenField(style.nameOnLockScreen, onChange = { onChange(style.copy(nameOnLockScreen = it)) })
    ToneField(style.tone, tones, onChange = { onChange(style.copy(tone = it)) })
}
