package de.emilyshub.ui.doku

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import de.emilyshub.R
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuPasswordProblem
import de.emilyshub.doku.DokuState
import de.emilyshub.doku.Fingerprint
import de.emilyshub.ui.PlaceholderScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Doku in the tab of Sonstiges (SPEC.md §14): its password set on first opening, its lock screen, and only once
 * unlocked its start page. It locks as soon as it is left, for another tab, the list of Sonstiges or a screen over the
 * tabs, as soon as the app leaves the screen or the screen goes off, and after five minutes without a touch; but not
 * for a rotation. Each unlock starts on its start page.
 */
@Composable
fun DokuArea(doku: Doku, fingerprint: Fingerprint, contentPadding: PaddingValues) {
    val activity = checkNotNull(LocalActivity.current)
    val locksNow by rememberUpdatedState { if (!activity.isChangingConfigurations) doku.lock() }
    DisposableEffect(doku) { onDispose { locksNow() } }
    // Also when the screen goes off: the activity stops then too.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { locksNow() }
    LaunchedEffect(doku) { doku.load() }
    // Here, so that what the prompts end in is done even once the screen that asked for them is gone.
    val scope = rememberCoroutineScope()
    val enableFingerprint = {
        scope.launch {
            val cipher = doku.sealingCipher()
            fingerprint.authenticate(activity, cipher, toUnlock = false, onSuccess = { scope.launch { doku.enableFingerprint(it) } }, onOther = {})
        }
        Unit
    }

    DokuInsel(doku) {
        when (val state = doku.state) {
            null -> Unit
            DokuState.New -> SetUpScreen(
                canUseFingerprint = fingerprint.isEnrolled(),
                contentPadding = contentPadding,
                onSetUp = { password, withFingerprint ->
                    doku.setUp(password)
                    if (withFingerprint) enableFingerprint()
                },
            )
            is DokuState.Locked -> LockedScreen(doku, fingerprint, activity, state, scope, contentPadding)
            is DokuState.Open -> {
                IdleLock(doku)
                PlaceholderScreen(stringResource(R.string.doku_no_patients), contentPadding)
                if (state.offerFingerprint) {
                    FingerprintAgain(
                        onEnable = {
                            doku.declineFingerprint()
                            enableFingerprint()
                        },
                        onDecline = doku::declineFingerprint,
                    )
                }
            }
        }
    }
}

/** Locks [doku] once it has been idle for five minutes. */
@Composable
private fun IdleLock(doku: Doku) {
    LaunchedEffect(doku) {
        while (true) {
            delay(doku.millisUntilIdle().coerceAtLeast(1))
            doku.lockIfIdle()
        }
    }
}

/** The Doku password, typed twice, with the warning that it cannot be recovered to confirm, and the fingerprint if [canUseFingerprint]. */
@Composable
private fun SetUpScreen(canUseFingerprint: Boolean, contentPadding: PaddingValues, onSetUp: suspend (String, Boolean) -> Unit) {
    // Not saveable: a password has no place in the saved state.
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var withFingerprint by remember { mutableStateOf(canUseFingerprint) }
    var triedToSet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val problem = Doku.passwordProblem(password, repeated).takeIf { triedToSet }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Outside the scrolling, so that nothing scrolls below the bars.
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.doku_set_up_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.doku_set_up_explanation))
        PasswordField(
            value = password,
            onChange = { password = it },
            label = stringResource(R.string.doku_password_new),
            problem = stringResource(R.string.doku_password_too_short, Doku.MIN_PASSWORD_LENGTH)
                .takeIf { problem == DokuPasswordProblem.TooShort },
        )
        PasswordField(
            value = repeated,
            onChange = { repeated = it },
            label = stringResource(R.string.doku_password_repeat),
            problem = stringResource(R.string.doku_password_not_repeated).takeIf { problem == DokuPasswordProblem.NotRepeated },
        )
        Text(stringResource(R.string.doku_no_recovery), color = MaterialTheme.colorScheme.error)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.toggleable(value = confirmed, role = Role.Checkbox, onValueChange = { confirmed = it }),
        ) {
            Checkbox(checked = confirmed, onCheckedChange = null)
            Text(stringResource(R.string.doku_no_recovery_confirm), modifier = Modifier.padding(start = 8.dp))
        }
        if (canUseFingerprint) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = withFingerprint, role = Role.Switch, onValueChange = { withFingerprint = it }),
            ) {
                Text(stringResource(R.string.doku_use_fingerprint), modifier = Modifier.weight(1f))
                Switch(checked = withFingerprint, onCheckedChange = null)
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(
            onClick = {
                triedToSet = true
                if (Doku.passwordProblem(password, repeated) == null) {
                    busy = true
                    scope.launch { onSetUp(password, withFingerprint && canUseFingerprint) }
                }
            },
            enabled = confirmed && !busy,
        ) {
            Text(stringResource(R.string.doku_set_up))
        }
    }
}

/**
 * The Doku locked: with the fingerprint on, its prompt at once, whose "Passwort" leads to the password field; without,
 * the password field right away. Nothing of the Doku shows.
 */
@Composable
private fun LockedScreen(
    doku: Doku,
    fingerprint: Fingerprint,
    activity: Activity,
    state: DokuState.Locked,
    scope: CoroutineScope,
    contentPadding: PaddingValues,
) {
    var usePassword by remember { mutableStateOf(false) }
    val promptFingerprint = {
        scope.launch {
            val cipher = doku.unlockingCipher()
            if (cipher == null) {
                usePassword = true
            } else {
                fingerprint.authenticate(
                    activity, cipher, toUnlock = true,
                    onSuccess = { scope.launch { if (!doku.unlockWithFingerprint(it)) usePassword = true } },
                    onOther = { usePassword = true },
                )
            }
        }
        Unit
    }
    // Asks right away, each time the locked Doku comes to the front, as long as the password was not chosen instead.
    val lifecycleOwner = LocalLifecycleOwner.current
    val prompt by rememberUpdatedState(promptFingerprint)
    if (state.fingerprint && !usePassword) {
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                lifecycleOwner.withResumed { prompt() }
            }
        }
    }

    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val unlock = {
        busy = true
        scope.launch {
            wrong = !doku.unlockWithPassword(password)
            busy = false
        }
        Unit
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Outside the scrolling, so that nothing scrolls below the bars.
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(R.string.doku_locked_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (state.fingerprintLost) Text(stringResource(R.string.doku_fingerprint_lost))
        if (state.fingerprint && !usePassword) {
            Button(onClick = promptFingerprint) { Text(stringResource(R.string.doku_unlock_with_fingerprint)) }
            OutlinedButton(onClick = { usePassword = true }) { Text(stringResource(R.string.doku_enter_password)) }
        } else {
            PasswordField(
                value = password,
                onChange = {
                    password = it
                    wrong = false
                },
                label = stringResource(R.string.doku_password),
                problem = stringResource(R.string.doku_wrong_password).takeIf { wrong },
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(onClick = unlock, enabled = password.isNotEmpty() && !busy) { Text(stringResource(R.string.doku_unlock)) }
            if (state.fingerprint) {
                OutlinedButton(onClick = promptFingerprint) { Text(stringResource(R.string.doku_unlock_with_fingerprint)) }
            }
        }
    }
}

/** After a new fingerprint on the phone and the password, whether to turn the fingerprint on again. */
@Composable
private fun FingerprintAgain(onEnable: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.doku_fingerprint_again_title)) },
        text = { Text(stringResource(R.string.doku_fingerprint_again_text)) },
        confirmButton = { TextButton(onClick = onEnable) { Text(stringResource(R.string.doku_fingerprint_again)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.doku_fingerprint_not_now)) } },
    )
}

@Composable
internal fun PasswordField(value: String, onChange: (String) -> Unit, label: String, problem: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        isError = problem != null,
        supportingText = problem?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}
