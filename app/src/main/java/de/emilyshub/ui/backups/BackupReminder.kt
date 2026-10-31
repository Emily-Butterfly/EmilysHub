package de.emilyshub.ui.backups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R
import de.emilyshub.backup.BackupWarning
import de.emilyshub.backup.NightlyBackups

/**
 * The hint on Heute (SPEC.md §10) while the last backup is over 30 days old or failed, or there has been none for as
 * long, read again on every return to the app; [onOpen] opens the backups. Nothing while the backups are fine.
 */
@Composable
fun BackupReminder(nightly: NightlyBackups, onOpen: () -> Unit) {
    var warning by remember { mutableStateOf<BackupWarning?>(null) }
    var returns by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(nightly) {
        returns++
        onPauseOrDispose {}
    }
    LaunchedEffect(nightly, returns) { warning = nightly.warning() }
    val title = when (warning ?: return) {
        BackupWarning.Failed -> R.string.today_backup_failed
        BackupWarning.Old -> R.string.today_backup_old
        BackupWarning.None -> R.string.today_backup_none
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
            Text(stringResource(R.string.today_backup_text), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.today_backup_open))
            }
        }
    }
}
