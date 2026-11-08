package de.emilyshub.ui.doku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.doku.Doku
import de.emilyshub.doku.PatientDraft
import de.emilyshub.doku.PatientOrder
import de.emilyshub.doku.Patients
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch

/**
 * The Doku's start page (SPEC.md §14): its own search over the patients, active and archived; the hint to delete those
 * closed twelve months ago or more, which opens the archive ([onOpenArchive]); and the active patients, by the last
 * appointment or by name, as last chosen, with "+ Patient". A patient tapped or added opens ([onOpen]); an appointment
 * the search found, by its documentation, opens itself ([onOpenAppointment]).
 */
@Composable
internal fun DokuHome(
    doku: Doku,
    patients: Patients,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    onOpenAppointment: (patientId: Long, id: Long) -> Unit,
    onOpenArchive: () -> Unit,
) {
    // Not saveable, as all that is typed in the Doku: nothing of it goes into the saved state.
    var query by remember { mutableStateOf("") }
    var order by remember { mutableStateOf<PatientOrder?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(patients) { order = patients.order() }
    val rows = rememberDokuLoad(patients, order) { order?.let { patients.active(it) } }
    val hits = rememberDokuLoad(patients, query) { if (query.isBlank()) null else patients.search(query) }
    val overdue = rememberDokuLoad(patients) { patients.overdueCount() } ?: 0

    LazyColumn(contentPadding = contentPadding) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.doku_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (query.isNotBlank()) {
            val found = hits.orEmpty()
            if (hits != null && found.isEmpty()) item { EmptyLine(stringResource(R.string.doku_search_nothing)) }
            items(found, key = { hit -> hit.appointment?.let { "appointment ${it.id}" } ?: "patient ${hit.id}" }) { hit ->
                val appointment = hit.appointment
                val details = listOfNotNull(
                    appointment?.let { stringResource(R.string.doku_search_appointment, it.date.numericDate()) },
                    stringResource(R.string.doku_search_archived).takeIf { hit.archived },
                )
                PatientLine(
                    name = hit.name,
                    detail = details.joinToString(" · ").ifEmpty { null },
                    onClick = { if (appointment != null) onOpenAppointment(hit.id, appointment.id) else onOpen(hit.id) },
                )
            }
            return@LazyColumn
        }
        if (overdue > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable(role = Role.Button, onClick = onOpenArchive),
                ) {
                    Text(pluralStringResource(R.plurals.doku_overdue_hint, overdue, overdue), modifier = Modifier.padding(16.dp))
                }
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                listOf(PatientOrder.LastSeen to R.string.doku_order_last_seen, PatientOrder.Name to R.string.doku_order_name).forEach { (choice, label) ->
                    ChoiceChip(
                        label = stringResource(label),
                        chosen = order == choice,
                        onClick = {
                            order = choice
                            scope.launch { patients.setOrder(choice) }
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text(stringResource(R.string.doku_patient_add)) }
            }
        }
        val active = rows ?: return@LazyColumn
        if (active.isEmpty()) item { EmptyLine(stringResource(R.string.doku_no_patients)) }
        items(active, key = { it.id }) { row ->
            PatientLine(
                name = row.name,
                detail = row.lastSeen?.let { stringResource(R.string.doku_last_seen, it.numericDate()) } ?: stringResource(R.string.doku_never_seen),
                onClick = { onOpen(row.id) },
            )
        }
    }

    if (adding) {
        PatientDialog(
            doku = doku,
            title = stringResource(R.string.doku_patient_new),
            confirm = stringResource(R.string.doku_patient_create),
            initial = PatientDraft(""),
            year = patients.today().year,
            onSave = { draft ->
                val id = patients.create(draft)
                adding = false
                if (id != null) onOpen(id)
            },
            onDismiss = { adding = false },
        )
    }
}

/** A patient in a list, by [name], with a [detail] below if any. */
@Composable
internal fun PatientLine(name: String, detail: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = detail?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

/** That a list has nothing in it. */
@Composable
internal fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}

/**
 * What [load] reads of the Doku, read anew as [patients] change and as [keys] do; null until read, and once the Doku
 * is locked. What was read last stays while it is read anew, so that nothing flickers.
 */
@Composable
internal fun <T> rememberDokuLoad(patients: Patients, vararg keys: Any?, load: suspend () -> T?): T? {
    var value by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(patients.changes, *keys) { value = load() }
    return value
}
