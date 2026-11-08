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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withResumed
import de.emilyshub.R
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuState
import de.emilyshub.doku.FingerprintUnlock
import de.emilyshub.doku.Patients
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Doku in the tab of Sonstiges (SPEC.md §14): its password set on first opening, its lock screen, and only once
 * unlocked its [pages] ([onPagesChange]). It locks as [DokuLocker] says, and after five minutes without a touch. Each
 * unlock starts on its start page.
 */
@Composable
fun DokuArea(doku: Doku, patients: Patients, pages: DokuPages, onPagesChange: (DokuPages) -> Unit, contentPadding: PaddingValues) {
    val activity = checkNotNull(LocalActivity.current)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(doku, lifecycle) {
        val locker = DokuLocker(doku) { activity.isChangingConfigurations }
        lifecycle.addObserver(locker)
        onDispose {
            lifecycle.removeObserver(locker)
            locker.left()
        }
    }
    LaunchedEffect(doku) { doku.load() }
    // Here, so that what the prompts end in is done even once the screen that asked for them is gone, as the setup's.
    val scope = rememberCoroutineScope()

    DokuInsel(doku) {
        when (val state = doku.state) {
            null -> Unit
            DokuState.New -> SetUpScreen(
                canUseFingerprint = doku.canUseFingerprint,
                contentPadding = contentPadding,
                onSetUp = { password, withFingerprint ->
                    scope.launch {
                        doku.setUp(password)
                        if (withFingerprint) doku.turnOnFingerprint(activity)
                    }
                },
            )
            is DokuState.Locked -> {
                // What was open goes with the lock: the next unlock starts on the start page.
                LaunchedEffect(Unit) { if (pages != DokuPages()) onPagesChange(DokuPages()) }
                LockedScreen(doku, activity, state, scope, contentPadding)
            }
            is DokuState.Open -> {
                IdleLock(doku)
                OpenPage(doku, patients, pages, onPagesChange, contentPadding)
                if (state.offerFingerprint) FingerprintAgain(doku, activity, scope)
            }
        }
    }
}

/** The page of the open Doku on top of [pages]. */
@Composable
private fun OpenPage(doku: Doku, patients: Patients, pages: DokuPages, onPagesChange: (DokuPages) -> Unit, contentPadding: PaddingValues) {
    val open = { page: DokuPage -> onPagesChange(pages.open(page)) }
    when (val page = pages.top) {
        DokuPage.Home -> DokuHome(
            doku = doku,
            patients = patients,
            contentPadding = contentPadding,
            onOpen = { open(DokuPage.Patient(it)) },
            onOpenArchive = { open(DokuPage.Archive) },
        )
        DokuPage.Archive -> DokuArchivePage(doku, patients, contentPadding, onOpen = { open(DokuPage.Patient(it)) })
        is DokuPage.Patient -> PatientPage(
            doku = doku,
            patients = patients,
            page = page,
            contentPadding = contentPadding,
            onTab = { onPagesChange(pages.replaceTop(page.copy(tab = it))) },
            onEditText = { open(DokuPage.Text(page.id, it)) },
            onGone = { deleted -> onPagesChange(if (deleted) pages.without(page.id) else pages.back()) },
        )
        is DokuPage.Text -> PatientTextPage(doku, patients, page, contentPadding, onDone = { onPagesChange(pages.back()) })
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
private fun SetUpScreen(canUseFingerprint: Boolean, contentPadding: PaddingValues, onSetUp: (String, Boolean) -> Unit) {
    // Not saveable: a password has no place in the saved state.
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var withFingerprint by remember { mutableStateOf(false) }
    var triedToSet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
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
        NewPasswordFields(
            password = password,
            onPasswordChange = { password = it },
            repeated = repeated,
            onRepeatedChange = { repeated = it },
            problem = Doku.passwordProblem(password, repeated).takeIf { triedToSet },
            label = stringResource(R.string.doku_password_new),
            repeatLabel = stringResource(R.string.doku_password_repeat),
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
                    onSetUp(password, withFingerprint && canUseFingerprint)
                }
            },
            enabled = confirmed && !busy,
        ) {
            Text(stringResource(R.string.doku_set_up))
        }
    }
}

/**
 * The Doku locked: with the fingerprint on, its prompt at once, each time the Doku comes to the front, whose "Passwort"
 * leads to the password field; without, the password field right away. Nothing of the Doku shows.
 */
@Composable
private fun LockedScreen(doku: Doku, activity: Activity, state: DokuState.Locked, scope: CoroutineScope, contentPadding: PaddingValues) {
    var usePassword by remember { mutableStateOf(false) }
    val askFingerprint: suspend () -> Unit = {
        if (doku.unlockWithFingerprint(activity) == FingerprintUnlock.Password) usePassword = true
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    if (state.fingerprint && !usePassword) {
        LaunchedEffect(lifecycleOwner) {
            // The prompt goes as the app leaves the screen, and comes again on its return.
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                lifecycleOwner.withResumed {}
                askFingerprint()
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
        val fingerprintButton = @Composable {
            OutlinedButton(onClick = { scope.launch { askFingerprint() } }) { Text(stringResource(R.string.doku_unlock_with_fingerprint)) }
        }
        if (state.fingerprint && !usePassword) {
            fingerprintButton()
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
            if (state.fingerprint) fingerprintButton()
        }
    }
}

/** After a new fingerprint on the phone and the password, whether to turn the fingerprint on again; once, either way. */
@Composable
private fun FingerprintAgain(doku: Doku, activity: Activity, scope: CoroutineScope) {
    var asking by remember { mutableStateOf(false) }
    if (asking) return
    AlertDialog(
        onDismissRequest = { scope.launch { doku.declineFingerprint() } },
        title = { Text(stringResource(R.string.doku_fingerprint_again_title)) },
        text = { Text(stringResource(R.string.doku_fingerprint_again_text)) },
        confirmButton = {
            TextButton(
                onClick = {
                    asking = true
                    scope.launch { if (!doku.turnOnFingerprint(activity)) doku.declineFingerprint() }
                },
            ) {
                Text(stringResource(R.string.doku_fingerprint_again))
            }
        },
        dismissButton = {
            TextButton(onClick = { scope.launch { doku.declineFingerprint() } }) { Text(stringResource(R.string.doku_fingerprint_not_now)) }
        },
        modifier = Modifier.touchesDoku(doku),
    )
}
