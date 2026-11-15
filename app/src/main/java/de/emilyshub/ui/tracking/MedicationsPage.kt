package de.emilyshub.ui.tracking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.Medication
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingMedications
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.numericDate

/**
 * The medications (SPEC.md §17): those still taken on top, the ones stopped below, each with its times and since when;
 * tapped, it opens with its timeline. Changed or stopped only with those treating, which the page says first.
 */
@Composable
internal fun MedicationsPage(tracking: Tracking, medications: TrackingMedications, contentPadding: PaddingValues, onOpen: (Long?) -> Unit) {
    val list = rememberIslandLoad(tracking) { medications.list() } ?: return
    Column(
        Modifier
            .fillMaxSize()
            // Outside the scrolling, so that nothing scrolls below the bars.
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.tracking_medication_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
        if (list.active.isEmpty() && list.ended.isEmpty()) {
            Text(stringResource(R.string.tracking_medications_empty), modifier = Modifier.padding(horizontal = 16.dp))
        }
        list.active.forEach { MedicationLine(it, onClick = { onOpen(it.id) }) }
        if (list.ended.isNotEmpty()) {
            Text(
                stringResource(R.string.tracking_medications_ended),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .semantics { heading() },
            )
            list.ended.forEach { MedicationLine(it, onClick = { onOpen(it.id) }) }
        }
        OutlinedButton(onClick = { onOpen(null) }, modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.tracking_medication_add))
        }
    }
}

/** A medication in the list: its name, then its form, times and since when, or from when to when. */
@Composable
private fun MedicationLine(medication: Medication, onClick: () -> Unit) {
    val span = medication.end?.let { stringResource(R.string.tracking_medication_from_to, medication.start.numericDate(), it.numericDate()) }
        ?: stringResource(R.string.tracking_medication_since, medication.start.numericDate())
    val details = listOf(medication.form, medication.times.joinToString { it.clockTime() }, span).filter { it.isNotBlank() }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(medication.name, style = MaterialTheme.typography.bodyLarge)
        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
