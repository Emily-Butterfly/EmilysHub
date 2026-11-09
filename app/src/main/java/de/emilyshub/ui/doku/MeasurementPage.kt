package de.emilyshub.ui.doku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.doku.Doku
import de.emilyshub.doku.FieldProblem
import de.emilyshub.doku.MeasurementForm
import de.emilyshub.doku.MeasurementView
import de.emilyshub.doku.Measurements
import de.emilyshub.doku.TestCatalog
import de.emilyshub.doku.TestNumber
import de.emilyshub.doku.TestValue
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.Problem
import kotlinx.coroutines.launch

/**
 * The tests measured at an appointment, in the order they were added, each with what it found and the sum; tapped,
 * one opens to change it ([onOpen]); "+ Test" measures one more ([onAdd]). Without either, only to read.
 */
@Composable
internal fun ColumnScope.MeasurementList(measured: List<MeasurementView>, onOpen: ((MeasurementView) -> Unit)?, onAdd: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(
            stringResource(R.string.doku_measurements),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onAdd != null) TextButton(onClick = onAdd) { Text(stringResource(R.string.doku_measurement_add)) }
    }
    measured.forEach { view ->
        ListItem(
            headlineContent = { Text(view.test.name) },
            supportingContent = { Text(listOfNotNull(foundText(view), view.remark.ifEmpty { null }).joinToString("\n")) },
            modifier = if (onOpen != null) Modifier.clickable { onOpen(view) } else Modifier,
        )
        HorizontalDivider()
    }
}

/** What [view] found, "Flexion 120° · Abduktion –", and its sum, if the test forms one. */
@Composable
private fun foundText(view: MeasurementView): String {
    val found = view.entries.joinToString(" · ") { (value, number) -> entryText(value.name, number, value.unit) }
    return if (view.formsSum) "$found · ${sumText(view.sum)}" else found
}

/** "Summe: 15", or "Summe: –" until every value has its number. */
@Composable
private fun sumText(sum: Long?): String =
    sum?.let { stringResource(R.string.doku_measurement_sum, TestNumber.format(it)) } ?: stringResource(R.string.doku_measurement_no_sum)

/** The tests not archived, to choose one to measure ([onChoose]); a hint where to set them up if there are none. */
@Composable
internal fun ChooseTest(doku: Doku, tests: TestCatalog, onChoose: (Long) -> Unit, onDismiss: () -> Unit) {
    val choosable = rememberDokuLoad(doku) { tests.choosable() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doku_measurement_choose)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (choosable?.isEmpty() == true) Text(stringResource(R.string.doku_measurement_none_to_choose))
                choosable.orEmpty().forEach { test ->
                    ListItem(headlineContent = { Text(test.name) }, modifier = Modifier.clickable { onChoose(test.id) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.touchesDoku(doku),
    )
}

/**
 * A measurement of a test at an appointment (SPEC.md §14), new or to change: a field for each value it asks for, with
 * its unit and range as a hint, numbers typed with a comma; the sum, live, once every value is there; and a remark.
 * One value at least is needed. Its menu deletes it, after asking. Saved, deleted or gone, the page closes ([onDone]).
 * Nothing typed goes into the saved state.
 */
@Composable
internal fun MeasurementPage(doku: Doku, measurements: Measurements, page: DokuPage.Measurement, contentPadding: PaddingValues, onDone: () -> Unit) {
    var loaded by remember { mutableStateOf<MeasurementForm?>(null) }
    LaunchedEffect(page) {
        val form = if (page.id == null) measurements.newForm(page.appointmentId, page.testId) else measurements.form(page.appointmentId, page.id)
        if (form == null) onDone() else loaded = form
    }
    val form = loaded ?: return
    var draft by remember { mutableStateOf(form.initial) }
    var tried by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val problems = form.problems(draft)

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                form.test.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(
                onClick = {
                    tried = true
                    if (problems.isEmpty) {
                        scope.launch {
                            measurements.save(page.appointmentId, page.id, form, draft)
                            onDone()
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.save))
            }
            if (page.id != null) MeasurementMenu(doku, onDelete = { deleting = true })
        }
        // Only the fields scroll: the test's name and "Speichern" stay in reach.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            form.fields.forEach { value ->
                NumberField(
                    value = value,
                    typed = draft.typed[value.id].orEmpty(),
                    problem = problems.values[value.id],
                    onChange = { draft = draft.copy(typed = draft.typed + (value.id to it)) },
                )
            }
            if (tried && problems.nothingEntered) Problem(stringResource(R.string.doku_measurement_nothing))
            if (form.formsSum) {
                Text(sumText(form.sum(draft)), style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = draft.remark,
                onValueChange = { draft = draft.copy(remark = it) },
                label = { Text(stringResource(R.string.doku_measurement_remark)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = problems.remarkTooLong,
                supportingText = { LengthHint(InputText.lengthOf(draft.cleanedRemark()), MeasurementForm.REMARK_MAX) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }

    if (deleting && page.id != null) {
        ConfirmDeleting(
            doku = doku,
            title = stringResource(R.string.doku_measurement_delete_title, form.test.name),
            text = stringResource(R.string.doku_measurement_delete_text),
            onDelete = {
                deleting = false
                scope.launch {
                    measurements.delete(page.id)
                    onDone()
                }
            },
            onDismiss = { deleting = false },
        )
    }
}

/** The number typed for [value], its unit and range as the hint, its [problem], if any, in place of it. */
@Composable
private fun NumberField(value: TestValue, typed: String, problem: FieldProblem?, onChange: (String) -> Unit) {
    val label = if (value.unit.isEmpty()) value.name else "${value.name} (${value.unit})"
    val range = rangeText(value)
    OutlinedTextField(
        value = typed,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = problem != null,
        supportingText = when (problem) {
            FieldProblem.NotANumber -> ({ Text(stringResource(R.string.doku_measurement_not_a_number)) })
            FieldProblem.OutOfRange -> ({ Text(listOfNotNull(stringResource(R.string.doku_measurement_out_of_range), range).joinToString(": ")) })
            null -> range?.let { { Text(it) } }
        },
        keyboardOptions = KeyboardOptions(keyboardType = if (value.decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The range of [value], "0 bis 180", "ab 0", "bis 10"; none without. */
@Composable
private fun rangeText(value: TestValue): String? {
    val min = value.min?.let(TestNumber::format)
    val max = value.max?.let(TestNumber::format)
    return when {
        min != null && max != null -> stringResource(R.string.doku_measurement_range, min, max)
        min != null -> stringResource(R.string.doku_measurement_from, min)
        max != null -> stringResource(R.string.doku_measurement_up_to, max)
        else -> null
    }
}

/** The three dots of a measurement, with "Löschen". */
@Composable
private fun MeasurementMenu(doku: Doku, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.doku_measurement_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.touchesDoku(doku)) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.doku_measurement_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
