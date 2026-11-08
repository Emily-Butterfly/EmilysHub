package de.emilyshub.ui.doku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.NameProblem
import de.emilyshub.doku.Affected
import de.emilyshub.doku.Direction
import de.emilyshub.doku.Doku
import de.emilyshub.doku.TestCatalog
import de.emilyshub.doku.TestDetails
import de.emilyshub.doku.TestDraft
import de.emilyshub.doku.ValueDraft
import de.emilyshub.doku.ValueProblem
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.numericDate
import kotlinx.coroutines.launch

/**
 * A test to add or change (SPEC.md §14): its name, its values in their order, each with a unit, a range, whether it
 * takes decimals and which way is better; and, of two values or more, whether to form the sum. A value removed is
 * deleted if never measured, else retired, grey here with "Wieder aktivieren". Saving a test with measurements says how
 * many it touches first. Its menu archives or restores it, or deletes it for good after asking. Saved or gone, the page
 * closes ([onDone]). Nothing typed goes into the saved state.
 */
@Composable
internal fun TestEditorPage(doku: Doku, tests: TestCatalog, page: DokuPage.TestEditor, contentPadding: PaddingValues, onDone: () -> Unit) {
    var loaded by remember { mutableStateOf<Opened?>(null) }
    LaunchedEffect(page) {
        val list = tests.list() ?: return@LaunchedEffect
        val details = page.id?.let { id -> tests.details(id) ?: return@LaunchedEffect onDone() }
        val taken = (list.active + list.archived).filter { it.id != page.id }.map { it.name }
        loaded = Opened(details, taken)
    }
    val opened = loaded ?: return
    val details = opened.details
    var draft by remember { mutableStateOf(details?.let(tests::draftOf) ?: TestDraft()) }
    var tried by remember { mutableStateOf(false) }
    var confirmingSave by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val problems = draft.problems(opened.taken)
    val save = {
        scope.launch {
            tests.save(page.id, draft)
            onDone()
        }
        Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp)) {
            Text(
                stringResource(if (details == null) R.string.doku_test_new else R.string.doku_test_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(
                onClick = {
                    tried = true
                    when {
                        !problems.isEmpty -> Unit
                        details != null && details.affected.measurements > 0 -> confirmingSave = true
                        else -> save()
                    }
                },
            ) {
                Text(stringResource(R.string.save))
            }
            if (details != null) {
                TestMenu(
                    doku = doku,
                    archived = details.test.isArchived,
                    onArchive = { scope.launch { tests.archive(details.test.id).also { onDone() } } },
                    onRestore = { scope.launch { tests.restore(details.test.id).also { onDone() } } },
                    onDelete = { deleting = true },
                )
            }
        }
        // Not lazy: a test has a few values, and each field stays in reach of scrolling. Its name and "Speichern" stay put.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            details?.test?.archivedOn?.let { archivedOn ->
                Text(
                    stringResource(R.string.doku_test_archived_on, archivedOn.numericDate()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            val nameProblem = problems.name.takeIf { tried || it == NameProblem.TooLong || it == NameProblem.Taken }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.doku_test_name)) },
                singleLine = true,
                isError = nameProblem != null,
                supportingText = {
                    when (nameProblem) {
                        NameProblem.Missing -> Text(stringResource(R.string.doku_test_name_missing))
                        NameProblem.Taken -> Text(stringResource(R.string.doku_test_name_taken))
                        else -> LengthHint(draft.name.trim().length, TestDraft.NAME_MAX)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Text(
                stringResource(R.string.doku_test_values),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )
            draft.values.forEachIndexed { index, value ->
                val change = { changed: ValueDraft -> draft = draft.copy(values = draft.values.toMutableList().also { it[index] = changed }) }
                ValueCard(
                    value = value,
                    problems = problems.values[index].filter { tried || it != ValueProblem.NameMissing }.toSet(),
                    canMoveUp = index > 0,
                    canMoveDown = index < draft.values.lastIndex,
                    onChange = change,
                    onMove = { by -> draft = draft.copy(values = draft.values.moved(index, index + by)) },
                    onRemove = {
                        // Measured once, it is retired, so that no history goes unnoticed; else it goes.
                        if (value.id != null && details?.measured?.contains(value.id) == true) {
                            change(value.copy(retired = true))
                        } else {
                            draft = draft.copy(values = draft.values.filterIndexed { other, _ -> other != index })
                        }
                    },
                )
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                TextButton(onClick = { draft = draft.copy(values = draft.values + ValueDraft()) }) {
                    Text(stringResource(R.string.doku_test_value_add))
                }
                if (tried && problems.noActiveValue) Problem(stringResource(R.string.doku_test_no_active_value))
            }
            if (draft.canSum) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = draft.sum, role = Role.Switch, onValueChange = { draft = draft.copy(sum = it) }),
                    ) {
                        Text(stringResource(R.string.doku_test_sum), modifier = Modifier.weight(1f))
                        Switch(checked = draft.sum, onCheckedChange = null)
                    }
                    if (draft.sum) DirectionChoice(draft.sumDirection, onChange = { draft = draft.copy(sumDirection = it) })
                }
            }
        }
    }

    if (confirmingSave && details != null) {
        AlertDialog(
            onDismissRequest = { confirmingSave = false },
            title = { Text(stringResource(R.string.doku_test_save_title)) },
            text = { Text(affectedText(details.affected, R.string.doku_test_affected)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingSave = false
                        save()
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { confirmingSave = false }) { Text(stringResource(R.string.cancel)) } },
            modifier = Modifier.touchesDoku(doku),
        )
    }
    if (deleting && details != null) {
        ConfirmDeleting(
            doku = doku,
            title = stringResource(R.string.doku_test_delete_title, details.test.name),
            text = if (details.affected.measurements > 0) affectedText(details.affected, R.string.doku_test_deletes) else null,
            onDelete = {
                deleting = false
                scope.launch {
                    tests.delete(details.test.id)
                    onDone()
                }
            },
            onDismiss = { deleting = false },
        )
    }
}

/** What the page opened with: the test, none for a new one, and the names of the other tests. */
private data class Opened(val details: TestDetails?, val taken: List<String>)

/** "Betrifft 14 Messungen bei 3 Patienten.", or as [text] says it. */
@Composable
private fun affectedText(affected: Affected, text: Int): String = stringResource(
    text,
    pluralStringResource(R.plurals.doku_test_affected_measurements, affected.measurements, affected.measurements),
    affected.patients,
)

/** A value of the test: its fields, moved up or down, removed; retired, only its name and "Wieder aktivieren". */
@Composable
private fun ValueCard(
    value: ValueDraft,
    problems: Set<ValueProblem>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (ValueDraft) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val name = value.name.ifBlank { stringResource(R.string.doku_test_value_name) }
    Card(
        colors = if (value.retired) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.doku_test_value_up, name))
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.doku_test_value_down, name))
                }
            }
            if (value.retired) {
                Text(stringResource(R.string.doku_test_value_retired), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onChange(value.copy(retired = false)) }) { Text(stringResource(R.string.doku_test_value_reactivate)) }
                return@Column
            }
            OutlinedTextField(
                value = value.name,
                onValueChange = { onChange(value.copy(name = it)) },
                label = { Text(stringResource(R.string.doku_test_value_name)) },
                singleLine = true,
                isError = problems.any { it in NAME_PROBLEMS },
                supportingText = when {
                    ValueProblem.NameMissing in problems -> ({ Text(stringResource(R.string.doku_test_value_name_missing)) })
                    ValueProblem.NameTaken in problems -> ({ Text(stringResource(R.string.doku_test_value_name_taken)) })
                    ValueProblem.NameTooLong in problems -> ({ LengthHint(value.cleanedName().length, ValueDraft.NAME_MAX) })
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = value.unit,
                onValueChange = { onChange(value.copy(unit = it)) },
                label = { Text(stringResource(R.string.doku_test_value_unit)) },
                singleLine = true,
                isError = ValueProblem.UnitTooLong in problems,
                supportingText = if (ValueProblem.UnitTooLong in problems) ({ LengthHint(value.cleanedUnit().length, ValueDraft.UNIT_MAX) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoundField(value.min, R.string.doku_test_value_min, ValueProblem.MinInvalid in problems, Modifier.weight(1f)) { onChange(value.copy(min = it)) }
                BoundField(value.max, R.string.doku_test_value_max, ValueProblem.MaxInvalid in problems, Modifier.weight(1f)) { onChange(value.copy(max = it)) }
            }
            if (ValueProblem.RangeReversed in problems) Problem(stringResource(R.string.doku_test_value_range_reversed))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = value.decimals, role = Role.Switch, onValueChange = { onChange(value.copy(decimals = it)) }),
            ) {
                Text(stringResource(R.string.doku_test_value_decimals), modifier = Modifier.weight(1f))
                Switch(checked = value.decimals, onCheckedChange = null)
            }
            DirectionChoice(value.direction, onChange = { onChange(value.copy(direction = it)) })
            TextButton(onClick = onRemove) { Text(stringResource(R.string.doku_test_value_remove)) }
        }
    }
}

/** An end of a value's range, as typed. */
@Composable
private fun BoundField(text: String, label: Int, invalid: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = invalid,
        supportingText = if (invalid) ({ Text(stringResource(R.string.doku_test_value_bound_invalid)) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Which way is better: higher or lower. */
@Composable
private fun DirectionChoice(direction: Direction, onChange: (Direction) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Direction.entries.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = direction == choice,
                onClick = { onChange(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, count = Direction.entries.size),
                label = { Text(stringResource(choice.label)) },
            )
        }
    }
}

/** The three dots of a test: archive or restore, and delete for good. */
@Composable
private fun TestMenu(doku: Doku, archived: Boolean, onArchive: () -> Unit, onRestore: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.doku_test_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.touchesDoku(doku)) {
            val item = @Composable { label: Int, action: () -> Unit ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
            if (archived) item(R.string.doku_test_restore, onRestore) else item(R.string.doku_test_archive, onArchive)
            item(R.string.doku_test_delete, onDelete)
        }
    }
}

private val NAME_PROBLEMS = setOf(ValueProblem.NameMissing, ValueProblem.NameTaken, ValueProblem.NameTooLong)

/** With the item at [from] moved to [to]. */
private fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().also { it.add(to, it.removeAt(from)) }
