package de.emilyshub.ui.backups

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R
import de.emilyshub.backup.BackupWarning
import de.emilyshub.backup.NightlyBackups
import de.emilyshub.ui.WarningCard

/**
 * The warning on Heute (SPEC.md §10) while the last backup is over 30 days old or failed, or there has been none for as
 * long, read again on every return to the app; [onOpen] opens the backups. Nothing while the backups are fine.
 */
@Composable
fun BackupWarningCard(nightly: NightlyBackups, onOpen: () -> Unit) {
    var warning by remember { mutableStateOf<BackupWarning?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(nightly) {
        resumes++
        onPauseOrDispose {}
    }
    LaunchedEffect(nightly, resumes) { warning = nightly.warning() }
    WarningCard(
        title = when (warning ?: return) {
            BackupWarning.Failed -> stringResource(R.string.today_backup_failed)
            BackupWarning.Old -> stringResource(R.string.today_backup_old, NightlyBackups.WARN_AFTER_DAYS.toInt())
            BackupWarning.None -> stringResource(R.string.today_backup_none)
        },
        text = stringResource(R.string.today_backup_text),
        action = stringResource(R.string.today_backup_open),
        onAction = onOpen,
    )
}
