package de.emilyshub.ui.tracking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tracking.Medication
import de.emilyshub.tracking.MedicationProblem
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingMedications
import de.emilyshub.tracking.TrackingModules
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.TimeDialog
import de.emilyshub.ui.island.KeepIslandDraft
import de.emilyshub.ui.island.rememberIslandLoad
import de.emilyshub.ui.island.touchesIsland
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * A medication as typed in (SPEC.md §17, "Medikamente"), new or of [page]'s id: its name, form, times, what for, the two
 * switches and the side effects watched for; and its timeline, start, end and dose changes. Saved as the page closes
 * and before Tracking locks, once it has a name and a time; "Fertig" says what is missing. Dose changes are noted at
 * once, on a medication saved.
 */
@Composable
internal fun MedicationPage(tracking: Tracking, modules: TrackingModules, page: TrackingPage.Medication, contentPadding: PaddingValues, onDone: () -> Unit) {
    val medications = modules.medications
    var loaded by remember { mutableStateOf<Medication?>(null) }
    LaunchedEffect(page) {
        val found = if (page.id == null) Medication(name = "", start = modules.entries.today()) else medications.timeline(page.id)?.medication
        if (found == null) onDone() else loaded = found
    }
    val original = loaded ?: return
    // Not saveable, as all that is typed in Tracking: nothing of it goes into the saved state.
    var draft by remember { mutableStateOf(original) }
    var triedDone by remember { mutableStateOf(false) }
    val problem = TrackingMedications.problemOf(draft)
    KeepIslandDraft(tracking, page, modules.writes) {
        val toSave = draft
        if (toSave == original || TrackingMedications.problemOf(toSave) != null) null else ({ database -> medications.write(database, toSave) })
    }
    var choosing by remember { mutableStateOf<DateChoice?>(null) }
    var addingTime by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            // Outside the scrolling, so that nothing scrolls below the bars.
            .padding(contentPadding),
    ) {
        // Above the scrolling, so that "Fertig" and the hint stay in reach.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
            Text(
                stringResource(R.string.tracking_medication_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    triedDone = true
                    if (problem == null) onDone()
                },
            ) { Text(stringResource(R.string.tracking_done)) }
        }
        MedicationFields(
            tracking = tracking,
            modules = modules,
            original = original,
            draft = draft,
            onDraftChange = { draft = it },
            problem = problem.takeIf { triedDone },
            onChooseDate = { choosing = it },
            onAddTime = { addingTime = true },
        )
    }
    choosing?.let { choice ->
        DateDialog(
            initial = (if (choice == DateChoice.Start) draft.start else draft.end) ?: modules.entries.today(),
            earliest = draft.start.takeIf { choice == DateChoice.End },
            onConfirm = {
                draft = if (choice == DateChoice.Start) draft.copy(start = it) else draft.copy(end = it)
                choosing = null
            },
            onDismiss = { choosing = null },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
    if (addingTime) {
        TimeDialog(
            initial = draft.times.lastOrNull() ?: LocalTime.of(8, 0),
            onConfirm = {
                draft = draft.copy(times = (draft.times + it).distinct().sorted())
                addingTime = false
            },
            onDismiss = { addingTime = false },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}

/**
 * The fields of a medication's [draft], which scroll: those typed in, its times, the switches, and its timeline, the
 * dose changes only once it is saved ([original] has an id). What is wrong shows only as [problem] says.
 */
@Composable
private fun MedicationFields(
    tracking: Tracking,
    modules: TrackingModules,
    original: Medication,
    draft: Medication,
    onDraftChange: (Medication) -> Unit,
    problem: MedicationProblem?,
    onChooseDate: (DateChoice) -> Unit,
    onAddTime: () -> Unit,
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {        OutlinedTextField(
            value = draft.name,
            onValueChange = { typed -> if (typed.length <= TrackingMedications.SHORT_MAX) onDraftChange(draft.copy(name = typed)) },
            label = { Text(stringResource(R.string.tracking_medication_name)) },
            singleLine = true,
            isError = problem == MedicationProblem.NoName,
            supportingText = if (problem == MedicationProblem.NoName) ({ Text(stringResource(R.string.tracking_medication_no_name)) }) else null,
            modifier = Modifier.fillMaxWidth(),
        )
        ShortField(R.string.tracking_medication_form, draft.form) { onDraftChange(draft.copy(form = it)) }
        Text(stringResource(R.string.tracking_medication_times), style = MaterialTheme.typography.bodyLarge)
        draft.times.forEach { time ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(time.clockTime(), modifier = Modifier.weight(1f))
                IconButton(onClick = { onDraftChange(draft.copy(times = draft.times - time)) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tracking_medication_remove_time, time.clockTime()))
                }
            }
        }
        if (problem == MedicationProblem.NoTime) {
            Text(stringResource(R.string.tracking_medication_no_time), color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = { onAddTime() }) { Text(stringResource(R.string.tracking_medication_add_time)) }
        ShortField(R.string.tracking_medication_purpose, draft.purpose) { onDraftChange(draft.copy(purpose = it)) }
        SwitchRow(stringResource(R.string.tracking_medication_thrombosis), draft.thrombosisRisk) { onDraftChange(draft.copy(thrombosisRisk = it)) }
        SwitchRow(stringResource(R.string.tracking_medication_lowers_pulse), draft.lowersPulse) { onDraftChange(draft.copy(lowersPulse = it)) }
        OutlinedTextField(
            value = draft.sideEffects,
            onValueChange = { typed -> if (typed.length <= TrackingMedications.TEXT_MAX) onDraftChange(draft.copy(sideEffects = typed)) },
            label = { Text(stringResource(R.string.tracking_medication_side_effects)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        EntryHeading(stringResource(R.string.tracking_medication_timeline))
        DateValue(stringResource(R.string.tracking_medication_start), draft.start, onClick = { onChooseDate(DateChoice.Start) })
        val end = draft.end
        if (end == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tracking_medication_end), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.tracking_medication_running), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { onChooseDate(DateChoice.End) }) { Text(stringResource(R.string.tracking_medication_stop)) }
            }
        } else {
            DateValue(stringResource(R.string.tracking_medication_end), end, onClick = { onChooseDate(DateChoice.End) })
            TextButton(onClick = { onDraftChange(draft.copy(end = null)) }) { Text(stringResource(R.string.tracking_medication_resume)) }
        }
        if (problem == MedicationProblem.EndBeforeStart) {
            Text(stringResource(R.string.tracking_medication_end_before_start), color = MaterialTheme.colorScheme.error)
        }
        if (original.id == 0L) {
            Text(stringResource(R.string.tracking_dose_changes_after_saving), style = MaterialTheme.typography.bodySmall)
        } else {
            DoseChanges(tracking, modules, original.id)
        }
        }
}

/** Which date of the timeline is being chosen. */
private enum class DateChoice { Start, End }

/** A field of one line for [text], under [label]. */
@Composable
private fun ShortField(label: Int, text: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = { typed -> if (typed.length <= TrackingMedications.SHORT_MAX) onChange(typed) },
        label = { Text(stringResource(label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A switch for [label]. */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The dose changes of medication [id] in the order of their dates, each to take off; and one to add. */
@Composable
private fun DoseChanges(tracking: Tracking, modules: TrackingModules, id: Long) {
    val medications = modules.medications
    val changes = rememberIslandLoad(tracking, id) { medications.timeline(id)?.doseChanges } ?: return
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    changes.forEach { change ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tracking_dose_change, change.date.numericDate(), change.from, change.to), modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { medications.removeDoseChange(change) } }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.tracking_dose_change_remove, change.date.numericDate()))
            }
        }
    }
    OutlinedButton(onClick = { adding = true }) { Text(stringResource(R.string.tracking_dose_change_add)) }
    if (adding) {
        DoseChangeDialog(tracking, initial = modules.entries.today(), onDismiss = { adding = false }) { date, from, to ->
            medications.addDoseChange(id, date, from, to)
        }
    }
}

/** Asks for a dose change: its date, the dose before and after, as typed; [confirm] false keeps it open. */
@Composable
private fun DoseChangeDialog(tracking: Tracking, initial: LocalDate, onDismiss: () -> Unit, confirm: suspend (LocalDate, String, String) -> Boolean) {
    // Not saveable, as all that is typed in Tracking.
    var date by remember { mutableStateOf(initial) }
    var before by remember { mutableStateOf("") }
    var after by remember { mutableStateOf("") }
    var choosingDate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tracking_dose_change_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateValue(stringResource(R.string.tracking_dose_change_date), date, onClick = { choosingDate = true })
                OutlinedTextField(
                    value = before,
                    onValueChange = { if (it.length <= TrackingMedications.SHORT_MAX) before = it },
                    label = { Text(stringResource(R.string.tracking_dose_before)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = after,
                    onValueChange = { if (it.length <= TrackingMedications.SHORT_MAX) after = it },
                    label = { Text(stringResource(R.string.tracking_dose_after)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { scope.launch { if (confirm(date, before, after)) onDismiss() } },
                enabled = before.isNotBlank() && after.isNotBlank(),
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.touchesIsland(tracking),
    )
    if (choosingDate) {
        DateDialog(
            initial = date,
            onConfirm = {
                date = it
                choosingDate = false
            },
            onDismiss = { choosingDate = false },
            modifier = Modifier.touchesIsland(tracking),
        )
    }
}
