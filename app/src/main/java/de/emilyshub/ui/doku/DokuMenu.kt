package de.emilyshub.ui.doku

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
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
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuState
import kotlinx.coroutines.launch

/**
 * The Doku's menu in the top bar, only while it is open (SPEC.md §14): its settings live here, not in the app's. For
 * now "Doku-Passwort ändern" and the fingerprint on or off; tests, the appointment template and the archive follow.
 */
@Composable
fun DokuMenu(doku: Doku) {
    val open = doku.state as? DokuState.Open ?: return
    var changingPassword by remember { mutableStateOf(false) }
    // The password fields of its dialog too ask the keyboard to learn nothing.
    DokuInsel(doku) { DokuMenuButton(doku, open, onChangePassword = { changingPassword = true }) }
    if (changingPassword) DokuInsel(doku) { ChangePassword(doku, onClose = { changingPassword = false }) }
}

/** The three dots, and what they open. */
@Composable
private fun DokuMenuButton(doku: Doku, open: DokuState.Open, onChangePassword: () -> Unit) {
    val activity = checkNotNull(LocalActivity.current)
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.doku_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.touchesDoku(doku)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.doku_menu_change_password)) },
                onClick = {
                    expanded = false
                    onChangePassword()
                },
            )
            // Off it can always be turned; on only with a fingerprint set up on the phone.
            if (open.fingerprint || doku.canUseFingerprint) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.doku_menu_fingerprint)) },
                    trailingIcon = { Switch(checked = open.fingerprint, onCheckedChange = null) },
                    onClick = {
                        expanded = false
                        scope.launch { if (open.fingerprint) doku.turnOffFingerprint() else doku.turnOnFingerprint(activity) }
                    },
                )
            }
        }
    }
}

/** The old Doku password and the new one twice; the database key stays, only how it is sealed changes. */
@Composable
private fun ChangePassword(doku: Doku, onClose: () -> Unit) {
    // Not saveable: a password has no place in the saved state.
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var triedToChange by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(stringResource(R.string.doku_menu_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(
                    value = old,
                    onChange = {
                        old = it
                        wrong = false
                    },
                    label = stringResource(R.string.doku_change_password_old),
                    problem = stringResource(R.string.doku_wrong_password).takeIf { wrong },
                )
                NewPasswordFields(
                    password = new,
                    onPasswordChange = { new = it },
                    repeated = repeated,
                    onRepeatedChange = { repeated = it },
                    problem = Doku.passwordProblem(new, repeated).takeIf { triedToChange },
                    label = stringResource(R.string.doku_change_password_new),
                    repeatLabel = stringResource(R.string.doku_change_password_repeat),
                )
                Text(stringResource(R.string.doku_change_password_backups))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    triedToChange = true
                    if (Doku.passwordProblem(new, repeated) == null) {
                        busy = true
                        scope.launch {
                            val changed = doku.changePassword(old, new)
                            busy = false
                            if (changed) onClose() else wrong = true
                        }
                    }
                },
                enabled = old.isNotEmpty() && !busy,
            ) {
                Text(stringResource(R.string.doku_change_password))
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.touchesDoku(doku),
    )
}
