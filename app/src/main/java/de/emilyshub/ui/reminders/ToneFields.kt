package de.emilyshub.ui.reminders

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import de.emilyshub.R
import de.emilyshub.reminders.Tone
import de.emilyshub.reminders.ToneLibrary
import de.emilyshub.ui.fields.Problem

/**
 * The "Ton" of a routine or to-do (SPEC.md §3), for its full-screen alarm and its notification alike: "Standard", the
 * defaults in the settings, or one of the phone's tones of any kind, or none, out of Android's choice of tones, which
 * plays each as it is chosen. A tone gone from the phone says that the default stands in for it.
 */
@Composable
fun ToneField(tone: Tone, tones: ToneLibrary, onChange: (Tone) -> Unit) {
    val title = stringResource(R.string.tone)
    val pick = rememberTonePicker(phoneDefault = null, onPicked = onChange)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(labelOf(tone, tones, standard = stringResource(R.string.tone_standard))) },
        modifier = Modifier.clickable {
            // Not the phone's default, nor any to mark as chosen for "Standard": none is, as the settings decide.
            pick(pickerIntent(RingtoneManager.TYPE_ALL, title, (tone as? Tone.Of)?.uri?.toUri(), phoneDefault = null))
        },
    )
    if (tone is Tone.Of && !tones.exists(tone.uri)) Problem(stringResource(R.string.tone_missing))
    if (tone != Tone.Standard) {
        TextButton(onClick = { onChange(Tone.Standard) }) { Text(stringResource(R.string.tone_reset)) }
    }
}

/**
 * One of the default tones in the settings (SPEC.md §3), named [title]: the phone's own of its [kind], a
 * [RingtoneManager] type, as Android's choice of tones offers it first, or one of the phone's tones of that kind, or none.
 */
@Composable
fun DefaultToneSetting(title: String, kind: Int, tone: Tone, tones: ToneLibrary, onChange: (Tone) -> Unit) {
    val phoneDefault = when (kind) {
        RingtoneManager.TYPE_ALARM -> Settings.System.DEFAULT_ALARM_ALERT_URI
        else -> Settings.System.DEFAULT_NOTIFICATION_URI
    }
    val pick = rememberTonePicker(phoneDefault, onPicked = onChange)
    val existing = when (tone) {
        Tone.Standard -> phoneDefault
        Tone.Silent -> null
        is Tone.Of -> tone.uri.toUri()
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(labelOf(tone, tones, standard = stringResource(R.string.tone_phone))) },
        modifier = Modifier.clickable { pick(pickerIntent(kind, title, existing, phoneDefault)) },
    )
    if (tone is Tone.Of && !tones.exists(tone.uri)) Problem(stringResource(R.string.tone_missing_phone))
}

/** "Lautlos", the tone's name, or [standard] for the default. */
@Composable
private fun labelOf(tone: Tone, tones: ToneLibrary, standard: String): String = when (tone) {
    Tone.Standard -> standard
    Tone.Silent -> stringResource(R.string.tone_silent)
    is Tone.Of -> tones.title(tone.uri) ?: stringResource(R.string.tone_unnamed)
}

/**
 * Opens Android's choice of tones with what it is asked for, and tells [onPicked] what was chosen: none for "Lautlos",
 * the default for [phoneDefault]. Nothing if it was left without a choice.
 */
@Composable
private fun rememberTonePicker(phoneDefault: Uri?, onPicked: (Tone) -> Unit): (Intent) -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@rememberLauncherForActivityResult
        val picked = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        onPicked(
            when (picked) {
                null -> Tone.Silent
                phoneDefault -> Tone.Standard
                else -> Tone.of(picked.toString())
            },
        )
    }
    return { launcher.launch(it) }
}

/** Android's choice of tones of [kind], with "Lautlos", and the phone's own first if there is [phoneDefault]. */
private fun pickerIntent(kind: Int, title: String, existing: Uri?, phoneDefault: Uri?): Intent =
    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, kind)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, phoneDefault != null)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, phoneDefault)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
