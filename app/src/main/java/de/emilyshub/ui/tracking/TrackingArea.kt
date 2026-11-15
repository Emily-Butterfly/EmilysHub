package de.emilyshub.ui.tracking

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.ACUTE_WARNING_SIGNS
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.Urgency
import de.emilyshub.tracking.WarningSigns
import de.emilyshub.ui.island.IslandGate
import de.emilyshub.ui.island.IslandTexts

/**
 * Tracking in the tab of Sonstiges (SPEC.md §17): behind its [IslandGate], as the Doku is, and only once unlocked its
 * start page. For now that holds "Akute Beschwerden?"; the slices after #123 add the rest.
 */
@Composable
internal fun TrackingArea(tracking: Tracking, contentPadding: PaddingValues) {
    IslandGate(island = tracking, texts = TRACKING_TEXTS, contentPadding = contentPadding, onLocked = {}) {
        TrackingHome(contentPadding)
    }
}

/** What Tracking's screens call it and its password. */
internal val TRACKING_TEXTS = IslandTexts(
    setUpTitle = R.string.tracking_set_up_title,
    passwordNew = R.string.tracking_password_new,
    passwordRepeat = R.string.tracking_password_repeat,
    lockedTitle = R.string.tracking_locked_title,
    fingerprintLost = R.string.tracking_fingerprint_lost,
    fingerprintAgainText = R.string.tracking_fingerprint_again_text,
    menu = R.string.tracking_menu,
    changePassword = R.string.tracking_menu_change_password,
)

/** Tracking's start page: first of all "Akute Beschwerden?". */
@Composable
private fun TrackingHome(contentPadding: PaddingValues) {
    LazyColumn(contentPadding = contentPadding) {
        item { AcuteComplaints() }
    }
}

/**
 * "Akute Beschwerden?" (SPEC.md §17): first a button that opens the phone app with 112, then the warning signs that
 * call for 112 at once and those that call for a doctor soon, each with their sources; then that medication is changed
 * only with those treating. The same for everyone, whatever is recorded.
 */
@Composable
private fun AcuteComplaints() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.tracking_acute_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            // First, so that it is at hand without reading on.
            CallEmergency()
            ACUTE_WARNING_SIGNS.forEach { WarningSignList(it) }
            Text(stringResource(R.string.tracking_medication_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The [signs] under what they call for, and their sources. */
@Composable
private fun WarningSignList(signs: WarningSigns) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val heading = when (signs.urgency) {
            Urgency.Emergency -> R.string.tracking_acute_emergency
            Urgency.Soon -> R.string.tracking_acute_soon
        }
        Text(stringResource(heading), style = MaterialTheme.typography.titleSmall)
        signs.signs.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Text(
            stringResource(R.string.tracking_acute_sources, signs.sources.joinToString { it.code }),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "112 anrufen": the phone app opens with the number, to call from there; no permission is needed for that. */
@Composable
private fun CallEmergency() {
    val context = LocalContext.current
    Button(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$EMERGENCY_NUMBER")))
            } catch (e: ActivityNotFoundException) {
                // A phone without a phone app, a tablet say: the number stands on the button.
                Log.w(TAG, "No app to dial with: ${e.javaClass.simpleName}")
            }
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.tracking_call_emergency))
    }
}

/** The emergency number across the EU. */
private const val EMERGENCY_NUMBER = "112"
private const val TAG = "EmilysHub"
