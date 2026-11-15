package de.emilyshub.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.Tracking
import de.emilyshub.ui.island.IslandInsel
import de.emilyshub.ui.island.IslandMenu
import de.emilyshub.ui.island.IslandMenuItem
import de.emilyshub.ui.island.PasswordField
import de.emilyshub.ui.island.touchesIsland
import kotlinx.coroutines.launch

/**
 * Tracking's menu in the top bar, only while it is open (SPEC.md §17): the symptoms, the medications and the base
 * data, which it opens ([onOpen]); "Passwort ändern", the fingerprint on or off, and "Alles löschen".
 */
@Composable
fun TrackingMenu(tracking: Tracking, onOpen: (TrackingPage) -> Unit) {
    var deleting by remember { mutableStateOf(false) }
    IslandMenu(
        tracking,
        TRACKING_TEXTS,
        before = MENU_PAGES.map { (label, page) -> IslandMenuItem(label) { onOpen(page) } },
        after = listOf(IslandMenuItem(R.string.tracking_menu_delete_all) { deleting = true }),
    )
    // The password field of its dialog too asks the keyboard to learn nothing.
    if (deleting) IslandInsel(tracking) { DeleteAll(tracking, onClose = { deleting = false }) }
}

/** "Alles löschen?": once confirmed with the Tracking password, all in Tracking is gone; password and fingerprint stay. */
@Composable
private fun DeleteAll(tracking: Tracking, onClose: () -> Unit) {
    // Not saveable: a password has no place in the saved state.
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(R.string.tracking_delete_all_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.tracking_delete_all_text))
                PasswordField(
                    value = password,
                    onChange = {
                        password = it
                        wrong = false
                    },
                    label = stringResource(R.string.tracking_delete_all_password),
                    problem = stringResource(R.string.doku_wrong_password).takeIf { wrong },
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    busy = true
                    scope.launch {
                        val deleted = tracking.deleteAll(password)
                        busy = false
                        if (deleted) onClose() else wrong = true
                    }
                },
                enabled = password.isNotEmpty() && !busy,
            ) {
                Text(stringResource(R.string.tracking_delete_all), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.touchesIsland(tracking),
    )
}

/** The pages the menu opens, by their label. */
private val MENU_PAGES = listOf(
    R.string.tracking_menu_symptoms to TrackingPage.Symptoms,
    R.string.tracking_menu_medications to TrackingPage.Medications,
    R.string.tracking_menu_base_data to TrackingPage.BaseData,
)
