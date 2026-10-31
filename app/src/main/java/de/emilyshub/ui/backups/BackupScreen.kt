package de.emilyshub.ui.backups

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.backup.Backup
import de.emilyshub.backup.BackupException
import de.emilyshub.backup.BackupProblem
import de.emilyshub.backup.Backups
import de.emilyshub.backup.NightlyBackups
import de.emilyshub.backup.NightlyState
import de.emilyshub.backup.PasswordProblem
import de.emilyshub.ui.Hint
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.enumeration
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The backups (SPEC.md §10): the backup password, set once; a backup of all data saved to a file chosen in Android's
 * dialog; the folder the [nightly] backups go to; and one loaded from such a file with the password it was saved with,
 * which replaces all data only once confirmed. Below, the advice to copy the backups off the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(backups: Backups, nightly: NightlyBackups, onBack: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var hasPassword by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(backups) { hasPassword = backups.hasPassword() }
    // Read anew after each saving, choosing or loading.
    var nightlyState by remember { mutableStateOf<NightlyState?>(null) }
    var busy by remember { mutableStateOf(false) }
    // How the last saving or loading went.
    var outcome by rememberSaveable { mutableStateOf<Int?>(null) }
    // The file picked to load, while its password is asked for, and why it did not open; then the backup in it, while
    // the user is asked whether it is to replace all data.
    var picked by rememberSaveable { mutableStateOf<Uri?>(null) }
    var openProblem by remember { mutableStateOf<Int?>(null) }
    var opened by remember { mutableStateOf<Backup?>(null) }

    fun work(block: suspend () -> Unit) {
        busy = true
        outcome = null
        scope.launch {
            try {
                block()
            } finally {
                nightlyState = nightly.state()
                busy = false
            }
        }
    }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE)) { file ->
        if (file != null) {
            work {
                outcome = failingWith(R.string.backup_save_failed) {
                    backups.export { checkNotNull(resolver.openOutputStream(file, "wt")) }
                    R.string.backup_saved
                }
            }
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file -> picked = file }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folder ->
        if (folder != null) {
            work {
                outcome = failingWith(R.string.backup_nightly_choose_failed) {
                    if (nightly.choose(folder.toString())) R.string.backup_nightly_chosen else R.string.backup_nightly_choose_failed
                }
            }
        }
    }
    LaunchedEffect(nightly) { nightlyState = nightly.state() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backups_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val passwordSet = hasPassword ?: return@Column
            SectionTitle(stringResource(R.string.backup_password_title))
            if (passwordSet) {
                Text(stringResource(R.string.backup_password_is_set))
            } else {
                NewPassword(
                    busy = busy,
                    onSet = { password ->
                        work {
                            outcome = failingWith(R.string.backup_password_failed) {
                                backups.setPassword(password)
                                hasPassword = true
                                null
                            }
                        }
                    },
                )
            }

            SectionTitle(stringResource(R.string.backup_save_title))
            Text(stringResource(R.string.backup_save_summary))
            if (!passwordSet) Hint(stringResource(R.string.backup_save_needs_password))
            Button(onClick = { save.launch(backups.fileName()) }, enabled = passwordSet && !busy) {
                Text(stringResource(R.string.backup_save))
            }

            nightlyState?.let { state ->
                Nightly(
                    state = state,
                    enabled = passwordSet && !busy,
                    passwordSet = passwordSet,
                    onChoose = { chooseFolder.launch(null) },
                    onStop = {
                        work {
                            outcome = failingWith(R.string.backup_nightly_stop_failed) {
                                nightly.stop()
                                null
                            }
                        }
                    },
                )
            }

            SectionTitle(stringResource(R.string.backup_load_title))
            Text(stringResource(R.string.backup_load_summary))
            OutlinedButton(onClick = { load.launch(arrayOf("*/*")) }, enabled = !busy) { Text(stringResource(R.string.backup_load)) }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            outcome?.let { Text(stringResource(it), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            Hint(stringResource(R.string.backup_copy_hint))
        }
    }

    picked?.let { file ->
        PasswordOfBackup(
            busy = busy,
            problem = openProblem,
            onEdit = { openProblem = null },
            onOpen = { password ->
                work {
                    openProblem = failingWith(R.string.backup_unreadable) {
                        try {
                            opened = backups.open({ checkNotNull(resolver.openInputStream(file)) }, password)
                            picked = null
                            null
                        } catch (e: BackupException) {
                            e.problem.message
                        }
                    }
                }
            },
            onDismiss = {
                picked = null
                openProblem = null
            },
        )
    }

    opened?.let { backup ->
        ConfirmReplacing(
            backup = backup,
            takesPassword = hasPassword == false,
            onReplace = {
                opened = null
                work {
                    outcome = failingWith(R.string.backup_restore_failed) {
                        backups.restore(backup)
                        R.string.backup_restored
                    }
                    // Taken over from the backup if there was none.
                    hasPassword = backups.hasPassword()
                }
            },
            onDismiss = { opened = null },
        )
    }
}

/**
 * The nightly backups as they [state]: the folder they go to, to choose, change or give up, when the last backup was
 * made, and whether the last one at night failed.
 */
@Composable
private fun Nightly(state: NightlyState, enabled: Boolean, passwordSet: Boolean, onChoose: () -> Unit, onStop: () -> Unit) {
    SectionTitle(stringResource(R.string.backup_nightly_title))
    Text(stringResource(R.string.backup_nightly_summary))
    if (!passwordSet) Hint(stringResource(R.string.backup_nightly_needs_password))
    val folder = state.folderName
    Text(
        when (folder) {
            null -> stringResource(R.string.backup_nightly_no_folder)
            "" -> stringResource(R.string.backup_nightly_folder, stringResource(R.string.backup_nightly_folder_unnamed))
            else -> stringResource(R.string.backup_nightly_folder, folder)
        },
    )
    Text(
        when (val made = state.lastMade) {
            null -> stringResource(R.string.backup_nightly_none_yet)
            else -> stringResource(R.string.backup_nightly_last, "${made.toLocalDate().numericDate()}, ${made.clockTime()}")
        },
    )
    if (state.failed) Text(stringResource(R.string.backup_nightly_failed), color = MaterialTheme.colorScheme.error)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onChoose, enabled = enabled) {
            Text(stringResource(if (folder == null) R.string.backup_nightly_choose else R.string.backup_nightly_change))
        }
        if (folder != null) OutlinedButton(onClick = onStop, enabled = enabled) { Text(stringResource(R.string.backup_nightly_stop)) }
    }
}

/** The new backup password, typed twice; its problems shown once the user tries to set it. */
@Composable
private fun NewPassword(busy: Boolean, onSet: (String) -> Unit) {
    // Not saveable: a password has no place in the saved state.
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var triedToSet by remember { mutableStateOf(false) }
    val problem = Backups.passwordProblem(password, repeated).takeIf { triedToSet }
    Text(stringResource(R.string.backup_password_explanation))
    PasswordField(
        value = password,
        onChange = { password = it },
        label = stringResource(R.string.backup_password),
        problem = stringResource(R.string.backup_password_too_short, Backups.MIN_PASSWORD_LENGTH)
            .takeIf { problem == PasswordProblem.TooShort },
    )
    PasswordField(
        value = repeated,
        onChange = { repeated = it },
        label = stringResource(R.string.backup_password_repeat),
        problem = stringResource(R.string.backup_password_not_repeated).takeIf { problem == PasswordProblem.NotRepeated },
    )
    Button(
        onClick = {
            triedToSet = true
            if (Backups.passwordProblem(password, repeated) == null) onSet(password)
        },
        enabled = !busy,
    ) {
        Text(stringResource(R.string.backup_password_set))
    }
}

/** Asks for the password of the backup in the file picked; the [problem] opening it shows below the field. */
@Composable
private fun PasswordOfBackup(
    busy: Boolean,
    @StringRes problem: Int?,
    onEdit: () -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.backup_open_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_open_text))
                PasswordField(
                    value = password,
                    onChange = {
                        password = it
                        onEdit()
                    },
                    label = stringResource(R.string.backup_password),
                    problem = problem?.let { stringResource(it) },
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpen(password) }, enabled = password.isNotEmpty() && !busy) {
                Text(stringResource(R.string.backup_open))
            }
        },
        // Not while it opens: the backup would ask to be restored once open.
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Asks whether the [backup] is to replace all data, saying when it was made and what it holds, and whether its password
 * [takesPassword] over as the backup password here, there being none yet.
 */
@Composable
private fun ConfirmReplacing(backup: Backup, takesPassword: Boolean, onReplace: () -> Unit, onDismiss: () -> Unit) {
    val created = backup.created
    val summary = backup.summary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_replace_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_replace_text, "${created.toLocalDate().numericDate()}, ${created.clockTime()}"))
                val contents = enumeration(
                    listOf(
                        pluralStringResource(R.plurals.backup_routines, summary.routines, summary.routines),
                        pluralStringResource(R.plurals.backup_todos, summary.todos, summary.todos),
                        pluralStringResource(R.plurals.backup_wishes, summary.wishes, summary.wishes),
                        pluralStringResource(R.plurals.backup_notes, summary.notes, summary.notes),
                        pluralStringResource(R.plurals.backup_places, summary.places, summary.places),
                        pluralStringResource(R.plurals.backup_tags, summary.tags, summary.tags),
                    ),
                )
                Text(stringResource(R.string.backup_replace_contents, contents))
                if (takesPassword) Text(stringResource(R.string.backup_replace_takes_password))
            }
        },
        confirmButton = { TextButton(onClick = onReplace) { Text(stringResource(R.string.backup_replace)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, problem: String?) {
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

/**
 * What [block] says of how it went, if anything, or [failure] if it failed: the kind of failure is only logged, for there
 * is nothing the user could do about it. Not its message, which may quote the data.
 */
private suspend fun failingWith(@StringRes failure: Int, block: suspend () -> Int?): Int? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Log.e(TAG, "A backup failed: ${e.javaClass.name}")
    failure
}

private val BackupProblem.message: Int
    @StringRes get() = when (this) {
        BackupProblem.NotABackup -> R.string.backup_not_a_backup
        BackupProblem.Damaged -> R.string.backup_damaged
        BackupProblem.WrongPassword -> R.string.backup_wrong_password
        BackupProblem.TooLarge -> R.string.backup_too_large
        BackupProblem.NewerVersion -> R.string.backup_newer_version
        BackupProblem.OlderVersion -> R.string.backup_older_version
    }

/** Android knows no type for a backup of EmilysHub. */
private const val MIME_TYPE = "application/octet-stream"
private const val TAG = "EmilysHub"
