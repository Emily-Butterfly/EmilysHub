package de.emilyshub.ui.doku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.doku.Appointments
import de.emilyshub.doku.Measurements
import de.emilyshub.doku.Doku
import de.emilyshub.doku.Patient
import de.emilyshub.doku.PatientField
import de.emilyshub.doku.toDraft
import de.emilyshub.notes.Markdown
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.notes.MarkdownText
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * A patient's page (SPEC.md §14): at the top the name and the year of birth with the age it makes, ✎ to change them,
 * and the patient's menu; below the four tabs Übersicht · Termine · Tests · Bögen, on [page]'s tab ([onTab]). The overview
 * shows the four texts as they read, each tapped to write it ([onEditText]). A closed patient's page is only to read,
 * its menu offering to restore them. The appointments, the latest first, open by a tap ([onOpenAppointment]), and an
 * active patient's "+ Termin" adds one, its documentation the template's, and opens it ([onAddedAppointment]). The tab Tests has a card for each test
 * measured, which opens its history ([onOpenHistory]); the tab Bögen one for each form with a result, which opens its
 * history ([onOpenAssessmentHistory]), and below the forms without, each opening ([onOpenAssessment]). Once closed or deleted, the page is gone ([onGone]).
 */
@Composable
internal fun PatientPage(
    doku: Doku,
    modules: DokuModules,
    page: DokuPage.Patient,
    contentPadding: PaddingValues,
    onTab: (PatientTab) -> Unit,
    onEditText: (PatientField) -> Unit,
    onOpenAppointment: (Long) -> Unit,
    onAddedAppointment: (Long) -> Unit,
    onOpenHistory: (testId: Long) -> Unit,
    onOpenAssessmentHistory: (assessmentId: String) -> Unit,
    onOpenAssessment: (appointmentId: Long, assessmentId: String, id: Long) -> Unit,
    onGone: (deleted: Boolean) -> Unit,
) {
    val (patients, appointments, _, measurements, assessments) = modules
    val patient = rememberDokuLoad(doku, page.id) { patients.get(page.id) } ?: return
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val addAppointment = {
        scope.launch { appointments.create(patient.id)?.let(onAddedAppointment) }
        Unit
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Header(
            doku = doku,
            patient = patient,
            year = patients.today().year,
            onRename = { renaming = true },
            onClose = {
                scope.launch {
                    patients.close(patient.id)
                    onGone(false)
                }
            },
            onRestore = { scope.launch { patients.restore(patient.id) } },
            onDelete = { deleting = true },
        )
        patient.closedOn?.let { closedOn ->
            Text(
                stringResource(R.string.doku_patient_closed_on, closedOn.numericDate()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        PrimaryTabRow(selectedTabIndex = page.tab.ordinal) {
            PatientTab.entries.forEach { tab ->
                Tab(selected = page.tab == tab, onClick = { onTab(tab) }, text = { Text(stringResource(tab.label)) })
            }
        }
        when (page.tab) {
            PatientTab.Overview -> Overview(patient, onEditText = onEditText.takeUnless { patient.isClosed })
            PatientTab.Appointments -> AppointmentList(
                doku = doku,
                appointments = appointments,
                patient = patient,
                onAdd = if (patient.isClosed) null else addAppointment,
                onOpen = onOpenAppointment,
            )
            PatientTab.Tests -> TestCards(doku, measurements, patient.id, onOpen = onOpenHistory)
            PatientTab.Assessments -> AssessmentCards(doku, assessments, patient.id, onOpenHistory = onOpenAssessmentHistory, onOpen = onOpenAssessment)
        }
    }

    if (renaming) {
        PatientDialog(
            doku = doku,
            title = stringResource(R.string.doku_patient_edit),
            confirm = stringResource(R.string.save),
            initial = patient.toDraft(),
            year = patients.today().year,
            onSave = { draft ->
                patients.rename(patient.id, draft)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDeleting(
            doku = doku,
            title = stringResource(R.string.doku_patient_delete_title, patient.name),
            text = stringResource(R.string.doku_patient_delete_text),
            onDelete = {
                deleting = false
                scope.launch {
                    patients.delete(patient.id)
                    onGone(true)
                }
            },
            onDismiss = { deleting = false },
        )
    }
}

/** The name, the year of birth and age, ✎ for an active patient, and the patient's menu. */
@Composable
private fun Header(
    doku: Doku,
    patient: Patient,
    year: Int,
    onRename: () -> Unit,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(patient.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            patient.birthYear?.let { born ->
                Text(stringResource(R.string.doku_patient_born, born, year - born), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!patient.isClosed) {
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.doku_patient_edit_action))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.doku_patient_menu))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.touchesDoku(doku)) {
                val item = @Composable { label: Int, action: () -> Unit ->
                    DropdownMenuItem(
                        text = { Text(stringResource(label)) },
                        onClick = {
                            menu = false
                            action()
                        },
                    )
                }
                if (patient.isClosed) item(R.string.doku_patient_restore, onRestore) else item(R.string.doku_patient_close, onClose)
                item(R.string.doku_patient_delete, onDelete)
            }
        }
    }
}

/** The four texts as they read, empty ones as "–"; each tapped to write it, unless only to read ([onEditText] null). */
@Composable
private fun Overview(patient: Patient, onEditText: ((PatientField) -> Unit)?) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(PatientField.entries) { field ->
            val text = patient.textOf(field)
            val blocks = remember(text) { Markdown.parse(text) }
            val label = stringResource(field.label)
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (onEditText != null) {
                            Modifier.clickable(onClickLabel = stringResource(R.string.doku_field_edit, label), role = Role.Button) {
                                onEditText(field)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                if (blocks.isEmpty()) {
                    Text(stringResource(R.string.doku_field_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MarkdownText(blocks)
                }
            }
            HorizontalDivider()
        }
    }
}

/**
 * The appointments of [patient], the latest first, each with its day, its time if any, and its preview line, "(leer)"
 * for no text; "+ Termin" on top, unless only to read ([onAdd] null).
 */
@Composable
private fun AppointmentList(doku: Doku, appointments: Appointments, patient: Patient, onAdd: (() -> Unit)?, onOpen: (Long) -> Unit) {
    val rows = rememberDokuLoad(doku, patient.id) { appointments.of(patient.id) } ?: return
    LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
        if (onAdd != null) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAdd) { Text(stringResource(R.string.doku_appointment_add)) }
                }
            }
        }
        if (rows.isEmpty()) item { EmptyLine(stringResource(R.string.doku_patient_no_appointments)) }
        items(rows, key = { it.id }) { row ->
            ListItem(
                headlineContent = { Text(appointmentWhen(row.date, row.time)) },
                supportingContent = {
                    Text(
                        row.preview ?: stringResource(R.string.doku_appointment_empty),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.clickable { onOpen(row.id) },
            )
            HorizontalDivider()
        }
    }
}

/**
 * A card for each test measured of the patient, archived ones too, the one measured last first: its name, the day of the
 * last measurement, and for the sum, or each value not retired, the last number and its change since the first. A
 * card tapped opens the test's history ([onOpen]).
 */
@Composable
private fun TestCards(doku: Doku, measurements: Measurements, patientId: Long, onOpen: (Long) -> Unit) {
    val cards = rememberDokuLoad(doku, patientId) { measurements.cards(patientId) } ?: return
    if (cards.isEmpty()) return EmptyLine(stringResource(R.string.doku_patient_no_tests))
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(cards, key = { it.test.id }) { card ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.doku_history_chart, card.test.name), role = Role.Button) { onOpen(card.test.id) },
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(card.test.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.doku_card_last, card.lastMeasured.numericDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    card.lines.forEach { line ->
                        val label = line.label ?: stringResource(R.string.doku_card_sum)
                        val change = changeText(line.change, line.unit)
                        Text(listOfNotNull("$label: ${withUnit(line.last, line.unit)}", change).joinToString(" "))
                    }
                }
            }
        }
    }
}

/** The day of an appointment and its time, if any: "23.10.2026, 09:30". */
@Composable
internal fun appointmentWhen(date: LocalDate, time: LocalTime?): String =
    time?.let { stringResource(R.string.doku_appointment_when, date.numericDate(), it.clockTime()) } ?: date.numericDate()

/** Whether to delete for good what [title] names, as [text] says it goes: with all that belongs to it, but not from older backups. */
@Composable
internal fun ConfirmDeleting(doku: Doku, title: String, text: String?, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.doku_patient_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.touchesDoku(doku),
    )
}

/** The name of a patient's text, as its heading. */
internal val PatientField.label: Int
    get() = when (this) {
        PatientField.Diagnosis -> R.string.doku_field_diagnosis
        PatientField.Anamnesis -> R.string.doku_field_anamnesis
        PatientField.Goals -> R.string.doku_field_goals
        PatientField.Other -> R.string.doku_field_other
    }

private val PatientTab.label: Int
    get() = when (this) {
        PatientTab.Overview -> R.string.doku_patient_overview
        PatientTab.Appointments -> R.string.doku_patient_appointments
        PatientTab.Tests -> R.string.doku_patient_tests
        PatientTab.Assessments -> R.string.doku_patient_assessments
    }
