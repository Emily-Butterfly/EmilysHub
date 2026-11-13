package de.emilyshub.ui.backups

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.backup.Backup
import de.emilyshub.backup.BackupException
import de.emilyshub.backup.BackupProblem
import de.emilyshub.backup.DataRescue
import de.emilyshub.data.HubData
import de.emilyshub.data.Unopenable
import de.emilyshub.ui.SectionTitle
import kotlinx.coroutines.launch

/**
 * When the app's data cannot be opened (#100): why, in one sentence of its [reason], that no reminders come until then,
 * and the ways out, none taken on its own: trying again, loading a backup into new data, or, once confirmed, starting
 * over. Data from a newer version first say to install that. Both of the last lay the data aside rather than deleting
 * them. Once the data open, [onOpened].
 */
@Composable
fun RescueScreen(reason: Unopenable, data: HubData, rescue: DataRescue, onOpened: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var outcome by rememberSaveable { mutableStateOf<Int?>(null) }
    var picked by rememberSaveable { mutableStateOf<Uri?>(null) }
    var openProblem by remember { mutableStateOf<Int?>(null) }
    var opened by remember { mutableStateOf<Backup?>(null) }
    var wrongDokuPassword by remember { mutableStateOf(false) }
    var confirmingStartOver by remember { mutableStateOf(false) }

    fun work(block: suspend () -> Unit) {
        busy = true
        outcome = null
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file -> picked = file }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.rescue_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(reason.sentence))
            if (reason == Unopenable.NewerVersion) {
                Text(stringResource(R.string.rescue_install_newer), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.rescue_no_reminders))

            Button(
                onClick = {
                    work {
                        if (data.retry() == null) onOpened() else outcome = R.string.rescue_still_unopenable
                    }
                },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.rescue_retry))
            }

            SectionTitle(stringResource(R.string.rescue_load_backup))
            Text(stringResource(R.string.rescue_load_backup_summary))
            OutlinedButton(onClick = { load.launch(arrayOf("*/*")) }, enabled = !busy) { Text(stringResource(R.string.rescue_load_backup)) }

            SectionTitle(stringResource(R.string.rescue_start_over))
            Text(stringResource(R.string.rescue_start_over_summary))
            OutlinedButton(onClick = { confirmingStartOver = true }, enabled = !busy) { Text(stringResource(R.string.rescue_start_over)) }

            Text(stringResource(R.string.rescue_set_aside_note), style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            outcome?.let { Text(stringResource(it), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
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
                            opened = rescue.open({ checkNotNull(resolver.openInputStream(file)) }, password)
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
            // The data begun anew have none.
            takesPassword = true,
            busy = busy,
            wrongDokuPassword = wrongDokuPassword,
            onEditDokuPassword = { wrongDokuPassword = false },
            onReplace = { dokuPassword ->
                work {
                    var restored = false
                    var askAgain = false
                    outcome = failingWith(R.string.rescue_failed) {
                        try {
                            rescue.restore(backup, dokuPassword)
                            restored = true
                            null
                        } catch (e: BackupException) {
                            if (e.problem != BackupProblem.WrongDokuPassword) throw e
                            // Nothing has changed: the Doku password is asked for again.
                            wrongDokuPassword = true
                            askAgain = true
                            null
                        }
                    }
                    if (!askAgain) opened = null
                    if (restored) onOpened()
                }
            },
            onDismiss = {
                opened = null
                wrongDokuPassword = false
                work { rescue.cancel() }
            },
        )
    }

    if (confirmingStartOver) {
        AlertDialog(
            onDismissRequest = { confirmingStartOver = false },
            title = { Text(stringResource(R.string.rescue_start_over_title)) },
            text = { Text(stringResource(R.string.rescue_start_over_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingStartOver = false
                        work {
                            var started = false
                            outcome = failingWith(R.string.rescue_failed) {
                                rescue.startOver()
                                started = true
                                null
                            }
                            if (started) onOpened()
                        }
                    },
                ) {
                    Text(stringResource(R.string.rescue_start_over))
                }
            },
            dismissButton = { TextButton(onClick = { confirmingStartOver = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The one sentence that says why, in one of three groups, without any details. */
private val Unopenable.sentence: Int
    get() = when (this) {
        Unopenable.KeyBroken -> R.string.rescue_key_broken
        Unopenable.FileBroken -> R.string.rescue_file_broken
        Unopenable.NewerVersion -> R.string.rescue_newer_version
    }
