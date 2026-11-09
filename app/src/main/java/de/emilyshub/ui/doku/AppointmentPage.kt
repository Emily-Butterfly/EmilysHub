package de.emilyshub.ui.doku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import de.emilyshub.R
import de.emilyshub.doku.Appointment
import de.emilyshub.doku.AppointmentView
import de.emilyshub.doku.Appointments
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuDraft
import de.emilyshub.doku.MeasurementView
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.TimeDialog
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * An appointment (SPEC.md §14): its day, in the past or to come, and its time if any, each changed at once; and its
 * documentation in Markdown, saved as the page closes, by "Fertig" ([onDone]), back or a rotation, and before the Doku
 * locks, never into the saved state. A new one left untouched goes again as the page closes or the Doku locks, though
 * not under a page opened over it ([Appointments.draft]), so that "+ Termin" alone keeps nothing. Below it the
 * tests measured at it, each opening to change it, and "+ Test" to measure one ([onMeasure], with the measurement's id,
 * or none for a new one); below them the forms filled in at it, and "+ Bogen" to fill one in ([onFill], new). "Löschen" in
 * its menu asks first, naming how many measurements and forms go with it. A closed patient's is only to read. Once deleted, or gone, the page closes
 * ([onDone]).
 */
@Composable
internal fun AppointmentPage(
    doku: Doku,
    modules: DokuModules,
    page: DokuPage.Appointment,
    contentPadding: PaddingValues,
    onMeasure: (testId: Long, id: Long?) -> Unit,
    onFill: (assessmentId: String, id: Long, new: Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val (_, appointments, tests, measurements, assessments) = modules
    // The text once, as it was on opening; the day and time anew with each change.
    var loaded by remember { mutableStateOf<AppointmentView?>(null) }
    LaunchedEffect(page) {
        val view = appointments.page(page.id)
        if (view == null) onDone() else loaded = view
    }
    val opened = loaded ?: return
    val appointment = rememberDokuLoad(doku, page.id) { appointments.page(page.id)?.appointment }
    var text by remember { mutableStateOf(opened.appointment.text) }
    var deleted by remember { mutableStateOf(false) }
    if (opened.canChange) {
        KeepDraft(doku, page) { if (deleted) DokuDraft.None else appointments.draft(page.id, opened.appointment.text, text) }
    }
    val current = appointment ?: return
    val scope = rememberCoroutineScope()
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val measured = rememberDokuLoad(doku, page.id) { measurements.at(page.id) }
    val filled = rememberDokuLoad(doku, page.id) { assessments.at(page.id) }
    var choosingForm by remember { mutableStateOf(false) }

    DokuTextEditor(
        title = opened.patientName,
        label = stringResource(R.string.doku_appointment_documentation),
        text = text,
        onTextChange = if (opened.canChange) ({ typed: String -> text = typed }) else null,
        hint = stringResource(R.string.doku_field_hint),
        contentPadding = contentPadding,
        actions = {
            if (opened.canChange) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.doku_field_done)) }
                AppointmentMenu(doku, onDelete = { deleting = true })
            }
        },
        header = {
            val time = current.time?.clockTime() ?: stringResource(R.string.doku_appointment_no_time)
            if (opened.canChange) {
                DateValue(stringResource(R.string.doku_appointment_date), current.date, onClick = { pickingDate = true })
                LabelledValue(
                    label = stringResource(R.string.doku_appointment_time),
                    value = time,
                    modifier = Modifier.clickable(
                        onClickLabel = stringResource(R.string.doku_appointment_time_change),
                        role = Role.Button,
                        onClick = { pickingTime = true },
                    ),
                )
                if (current.time != null) {
                    TextButton(onClick = { scope.launch { appointments.setTime(page.id, null) } }) {
                        Text(stringResource(R.string.doku_appointment_remove_time))
                    }
                }
            } else {
                LabelledValue(stringResource(R.string.doku_appointment_date), current.date.numericDate())
                LabelledValue(stringResource(R.string.doku_appointment_time), time)
            }
        },
        footer = {
            MeasurementList(
                measured = measured.orEmpty(),
                onOpen = if (opened.canChange) ({ view: MeasurementView -> onMeasure(view.test.id, view.id) }) else null,
                onAdd = if (opened.canChange) ({ choosing = true }) else null,
            )
            // Only to read, they open all the same.
            AssessmentList(
                filled = filled.orEmpty(),
                onOpen = { form -> onFill(form.assessmentId, form.id, false) },
                onAdd = if (opened.canChange) ({ choosingForm = true }) else null,
            )
        },
    )

    if (choosing) {
        ChooseTest(doku, tests, onChoose = { testId ->
            choosing = false
            onMeasure(testId, null)
        }, onDismiss = { choosing = false })
    }

    if (choosingForm) {
        ChooseAssessment(doku, assessments, onChoose = { assessmentId ->
            choosingForm = false
            scope.launch {
                assessments.create(page.id, assessmentId)?.let { onFill(assessmentId, it, true) }
            }
        }, onDismiss = { choosingForm = false })
    }

    if (pickingDate) {
        // The time is typed: its fields too ask the keyboard to learn nothing.
        DokuInsel(doku) {
            DateDialog(
                initial = current.date,
                onConfirm = { date ->
                    pickingDate = false
                    scope.launch { appointments.setDate(page.id, date) }
                },
                onDismiss = { pickingDate = false },
                modifier = Modifier.touchesDoku(doku),
            )
        }
    }
    if (pickingTime) {
        DokuInsel(doku) {
            TimeDialog(
                initial = current.time ?: LocalTime.of(DEFAULT_HOUR, 0),
                onConfirm = { time ->
                    pickingTime = false
                    scope.launch { appointments.setTime(page.id, time) }
                },
                onDismiss = { pickingTime = false },
                modifier = Modifier.touchesDoku(doku),
            )
        }
    }
    if (deleting) {
        ConfirmDeletingAppointment(
            doku = doku,
            appointment = current,
            measurements = measured.orEmpty().size,
            assessments = filled.orEmpty().size,
            onDelete = {
                deleting = false
                deleted = true
                scope.launch {
                    appointments.delete(page.id)
                    onDone()
                }
            },
            onDismiss = { deleting = false },
        )
    }
}

/** What the page opened with: the appointment as it was, the patient's name, and whether it may be changed. */

/** The three dots of an appointment, with "Löschen". */
@Composable
private fun AppointmentMenu(doku: Doku, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.doku_appointment_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.touchesDoku(doku)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.doku_appointment_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Whether to delete [appointment] with its documentation, naming how many [measurements] and [assessments] go with it. */
@Composable
private fun ConfirmDeletingAppointment(
    doku: Doku,
    appointment: Appointment,
    measurements: Int,
    assessments: Int,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val counted = listOfNotNull(
        pluralStringResource(R.plurals.doku_count_measurements, measurements, measurements).takeIf { measurements > 0 },
        pluralStringResource(R.plurals.doku_count_assessments, assessments, assessments).takeIf { assessments > 0 },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doku_appointment_delete_title, appointment.date.numericDate())) },
        text = {
            Text(
                when (counted.size) {
                    0 -> stringResource(R.string.doku_appointment_delete_text_plain)
                    1 -> stringResource(R.string.doku_appointment_delete_text, counted[0])
                    else -> stringResource(R.string.doku_appointment_delete_text_both, counted[0], counted[1])
                },
            )
        },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.doku_appointment_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.touchesDoku(doku),
    )
}

/** The hour a time to pick starts at, for an appointment without one yet. */
private const val DEFAULT_HOUR = 9
