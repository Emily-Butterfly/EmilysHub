package de.emilyshub.ui.status

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.Routines
import de.emilyshub.status.Standing
import de.emilyshub.ui.enumeration
import de.emilyshub.ui.rememberChecked

/**
 * The warning on Heute (SPEC.md §11) while something the active [routines] need from Android is lacking, read again on
 * every return to the app; [onCheck] opens "Funktioniert alles?". Nothing while all is there.
 */
@Composable
fun SetupWarning(routines: Routines, onCheck: () -> Unit) {
    val readiness = rememberChecked { readiness() }
    val use = rememberUse(routines) ?: return
    val lacking = readiness.current.lacking(use)
    if (lacking.isEmpty()) return
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
                Text(
                    stringResource(R.string.setup_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            val names = lacking.map { need ->
                val texts = need.texts
                stringResource(if (readiness.current.standing(need) == Standing.Off) texts.offTitle else texts.title)
            }
            Text(stringResource(R.string.setup_warning_lacking, enumeration(names)), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCheck, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.setup_warning_check))
            }
        }
    }
}
