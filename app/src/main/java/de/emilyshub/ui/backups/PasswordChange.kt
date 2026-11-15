package de.emilyshub.ui.backups

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.backup.Backups
import de.emilyshub.backup.PasswordProblem
import de.emilyshub.ui.Hint

/**
 * The step changing the backup password is at (SPEC.md §10; #111): asked for the old one and the new; told what
 * forgetting it means, before the app lock; and, past the app lock, asked for the new one only.
 */
internal enum class PasswordChangeStep {
    Changing,
    Forgetting,
    Forgotten,
    ;

    companion object {
        /**
         * Kept over a rotation or the app's process ending, but never past the app lock: [Forgotten] comes back as
         * [Forgetting], so that the app lock is asked for again.
         */
        val Saver: Saver<PasswordChangeStep?, String> = Saver(
            save = { step -> (if (step == Forgotten) Forgetting else step)?.name ?: "" },
            restore = { name -> entries.firstOrNull { it.name == name } },
        )
    }
}

/**
 * Asks for the old backup password and the new one twice, saying that backups saved before stay with the old one; the
 * old one is shown [wrongOld] once it was not the one set. "Passwort vergessen?" leads to [onForgot], but only if the
 * phone [canForget], with a screen lock for the app lock to ask for; else the dialog says to set one up first.
 */
@Composable
internal fun ChangePassword(
    busy: Boolean,
    wrongOld: Boolean,
    onEditOld: () -> Unit,
    onChange: (old: String, new: String) -> Unit,
    canForget: Boolean,
    onForgot: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Not saveable: a password has no place in the saved state.
    var old by remember { mutableStateOf("") }
    val new = remember { NewPasswordState() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.backup_password_change_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_password_change_text))
                PasswordField(
                    value = old,
                    onChange = {
                        old = it
                        onEditOld()
                    },
                    label = stringResource(R.string.backup_password_old),
                    problem = stringResource(R.string.backup_password_old_wrong).takeIf { wrongOld },
                )
                NewPasswordFields(new)
                if (canForget) {
                    TextButton(onClick = onForgot, enabled = !busy) { Text(stringResource(R.string.backup_password_forgot)) }
                } else {
                    Hint(stringResource(R.string.backup_password_forgot_needs_screen_lock))
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (new.tryToSet()) onChange(old, new.password) },
                enabled = old.isNotEmpty() && !busy,
            ) {
                Text(stringResource(R.string.backup_password_change_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Says what a new backup password in place of one forgotten means, before the app lock is asked for ([onContinue]). */
@Composable
internal fun ForgetPassword(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password_forgot)) },
        text = { Text(stringResource(R.string.backup_password_forgot_text)) },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.backup_password_forgot_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Asks, past the app lock, for a new backup password in place of one forgotten, twice. */
@Composable
internal fun PasswordInsteadOfForgotten(busy: Boolean, onSet: (String) -> Unit, onDismiss: () -> Unit) {
    val new = remember { NewPasswordState() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.backup_password_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NewPasswordFields(new)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { if (new.tryToSet()) onSet(new.password) }, enabled = !busy) {
                Text(stringResource(R.string.backup_password_set))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A new backup password typed twice, its problems shown once it was tried to be set. */
internal class NewPasswordState {
    var password by mutableStateOf("")
    var repeated by mutableStateOf("")
    var tried by mutableStateOf(false)

    val problem: PasswordProblem? get() = Backups.passwordProblem(password, repeated).takeIf { tried }

    /** Whether it can be set, showing its problems from now on. */
    fun tryToSet(): Boolean {
        tried = true
        return Backups.passwordProblem(password, repeated) == null
    }
}

/** The fields of a new backup password, by default named as the new one in place of another. */
@Composable
internal fun NewPasswordFields(
    state: NewPasswordState,
    @StringRes label: Int = R.string.backup_password_new,
    @StringRes repeatLabel: Int = R.string.backup_password_new_repeat,
) {
    val problem = state.problem
    PasswordField(
        value = state.password,
        onChange = { state.password = it },
        label = stringResource(label),
        problem = stringResource(R.string.backup_password_too_short, Backups.MIN_PASSWORD_LENGTH)
            .takeIf { problem == PasswordProblem.TooShort },
    )
    PasswordField(
        value = state.repeated,
        onChange = { state.repeated = it },
        label = stringResource(repeatLabel),
        problem = stringResource(R.string.backup_password_not_repeated).takeIf { problem == PasswordProblem.NotRepeated },
    )
}
