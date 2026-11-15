package de.emilyshub.ui.tracking

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.Symptom
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingSymptoms
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.island.touchesIsland
import kotlinx.coroutines.launch

/**
 * The symptoms asked in the evening (SPEC.md §17, "Symptome"): each to rename, move up or down and ask or not; hidden
 * ones keep their values. "Symptom hinzufügen" adds one of one's own at the end.
 */
@Composable
internal fun SymptomsPage(tracking: Tracking, symptoms: TrackingSymptoms, contentPadding: PaddingValues) {
    val all = rememberIslandLoad(tracking) { symptoms.all() } ?: return
    val scope = rememberCoroutineScope()
    var naming by remember { mutableStateOf<Symptom?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            // Outside the scrolling, so that nothing scrolls below the bars.
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.tracking_symptoms_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        all.forEachIndexed { index, symptom ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
            ) {
                Text(
                    symptom.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (symptom.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { naming = symptom }) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.tracking_symptom_rename, symptom.name))
                }
                IconButton(onClick = { scope.launch { symptoms.move(symptom.id, up = true) } }, enabled = index > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.tracking_symptom_up, symptom.name))
                }
                IconButton(onClick = { scope.launch { symptoms.move(symptom.id, up = false) } }, enabled = index < all.lastIndex) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.tracking_symptom_down, symptom.name))
                }
                val asked = stringResource(R.string.tracking_symptom_asked, symptom.name)
                Switch(
                    checked = !symptom.hidden,
                    onCheckedChange = { shown -> scope.launch { symptoms.setHidden(symptom.id, !shown) } },
                    modifier = Modifier.semantics { contentDescription = asked },
                )
            }
        }
        OutlinedButton(onClick = { adding = true }, modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.tracking_symptom_add))
        }
    }
    naming?.let { symptom ->
        SymptomNameDialog(tracking, title = stringResource(R.string.tracking_symptom_rename, symptom.name), initial = symptom.name, onDismiss = { naming = null }) {
            symptoms.rename(symptom.id, it)
        }
    }
    if (adding) {
        SymptomNameDialog(tracking, title = stringResource(R.string.tracking_symptom_add), initial = "", onDismiss = { adding = false }) {
            symptoms.add(it)
        }
    }
}

/** Asks for a symptom's name under [title]; [confirm] is false for one empty, too long or taken, which then says so. */
@Composable
private fun SymptomNameDialog(tracking: Tracking, title: String, initial: String, onDismiss: () -> Unit, confirm: suspend (String) -> Boolean) {
    // Not saveable: a symptom's name is Tracking's, and nothing of it goes into the saved state.
    var name by remember { mutableStateOf(initial) }
    var refused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= TrackingSymptoms.NAME_MAX) name = it
                    refused = false
                },
                label = { Text(stringResource(R.string.tracking_symptom_name)) },
                singleLine = true,
                isError = refused,
                supportingText = if (refused) ({ Text(stringResource(R.string.tracking_symptom_name_problem)) }) else null,
            )
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { if (confirm(name)) onDismiss() else refused = true } }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.touchesIsland(tracking),
    )
}
