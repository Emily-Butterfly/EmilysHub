package de.emilyshub.ui.island

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
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
import de.emilyshub.island.Island
import de.emilyshub.island.IslandState
import kotlinx.coroutines.launch

/** An item of an island's menu: its [label], and what tapping it does once the menu has closed. */
class IslandMenuItem(@param:StringRes val label: Int, val onClick: () -> Unit)

/**
 * An island's menu in the top bar, only while it is open (SPEC.md §14, §17): its settings live here, not in the app's.
 * The items [before], then changing the password and the fingerprint on or off, then the items [after].
 */
@Composable
fun IslandMenu(island: Island<*>, texts: IslandTexts, before: List<IslandMenuItem> = emptyList(), after: List<IslandMenuItem> = emptyList()) {
    val open = island.state as? IslandState.Open ?: return
    var changingPassword by remember { mutableStateOf(false) }
    // The password fields of its dialog too ask the keyboard to learn nothing.
    IslandInsel(island) { IslandMenuButton(island, texts, open, before, after, onChangePassword = { changingPassword = true }) }
    if (changingPassword) IslandInsel(island) { ChangePassword(island, texts, onClose = { changingPassword = false }) }
}

/** The three dots, and what they open. */
@Composable
private fun IslandMenuButton(
    island: Island<*>,
    texts: IslandTexts,
    open: IslandState.Open,
    before: List<IslandMenuItem>,
    after: List<IslandMenuItem>,
    onChangePassword: () -> Unit,
) {
    val activity = checkNotNull(LocalActivity.current)
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(texts.menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.touchesIsland(island)) {
            val item = @Composable { it: IslandMenuItem ->
                DropdownMenuItem(
                    text = { Text(stringResource(it.label)) },
                    onClick = {
                        expanded = false
                        it.onClick()
                    },
                )
            }
            before.forEach { item(it) }
            DropdownMenuItem(
                text = { Text(stringResource(texts.changePassword)) },
                onClick = {
                    expanded = false
                    onChangePassword()
                },
            )
            // Off it can always be turned; on only with a fingerprint set up on the phone.
            if (open.fingerprint || island.canUseFingerprint) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.doku_menu_fingerprint)) },
                    trailingIcon = { Switch(checked = open.fingerprint, onCheckedChange = null) },
                    onClick = {
                        expanded = false
                        scope.launch { if (open.fingerprint) island.turnOffFingerprint() else island.turnOnFingerprint(activity) }
                    },
                )
            }
            after.forEach { item(it) }
        }
    }
}

/** The old password and the new one twice; the database key stays, only how it is sealed changes. */
@Composable
private fun ChangePassword(island: Island<*>, texts: IslandTexts, onClose: () -> Unit) {
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
        title = { Text(stringResource(texts.changePassword)) },
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
                    problem = Island.passwordProblem(new, repeated).takeIf { triedToChange },
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
                    if (Island.passwordProblem(new, repeated) == null) {
                        busy = true
                        scope.launch {
                            val changed = island.changePassword(old, new)
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
        modifier = Modifier.touchesIsland(island),
    )
}
