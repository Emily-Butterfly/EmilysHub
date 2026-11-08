package de.emilyshub.ui.doku

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.doku.ArchivedPatient
import de.emilyshub.doku.Doku
import de.emilyshub.doku.Patients
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch

/**
 * The Doku's archive (SPEC.md §14): the closed patients, the one closed last first, with the day they were closed;
 * those closed twelve months ago or more marked, and deleted for good together, after asking. A patient tapped opens,
 * only to read ([onOpen]).
 */
@Composable
internal fun DokuArchivePage(doku: Doku, patients: Patients, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val archived = rememberDokuLoad(patients) { patients.archive() } ?: return
    val overdue = archived.count(ArchivedPatient::overdue)
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = contentPadding) {
        item {
            Text(
                stringResource(R.string.doku_archive_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
        }
        if (overdue > 0) {
            item {
                Button(
                    onClick = { deleting = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(pluralStringResource(R.plurals.doku_overdue_delete, overdue, overdue))
                }
            }
        }
        if (archived.isEmpty()) item { EmptyLine(stringResource(R.string.doku_archive_empty)) }
        items(archived, key = { it.id }) { patient ->
            val closed = stringResource(R.string.doku_archive_closed_on, patient.closedOn.numericDate())
            PatientLine(
                name = patient.name,
                detail = if (patient.overdue) "$closed · ${stringResource(R.string.doku_archive_overdue)}" else closed,
                onClick = { onOpen(patient.id) },
            )
        }
    }

    if (deleting) {
        ConfirmDeleting(
            doku = doku,
            title = pluralStringResource(R.plurals.doku_overdue_delete_title, overdue, overdue),
            text = stringResource(R.string.doku_overdue_delete_text),
            onDelete = {
                deleting = false
                scope.launch { patients.deleteOverdue() }
            },
            onDismiss = { deleting = false },
        )
    }
}
