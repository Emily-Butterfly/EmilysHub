package de.emilyshub.ui.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.DraftProblem
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import de.emilyshub.ui.clockTime
import kotlinx.coroutines.launch
import java.io.Serializable
import java.time.LocalTime

/** Which routine the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface RoutineEditorTarget : Serializable {
    data object New : RoutineEditorTarget

    data class Edit(val routineId: Long) : RoutineEditorTarget
}

/** Creates or changes a routine; for now one that is due daily at one time. Leaving without saving drops the changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(routines: Routines, target: RoutineEditorTarget, onClose: () -> Unit) {
    val routineId = (target as? RoutineEditorTarget.Edit)?.routineId
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf(DEFAULT_TIME) }
    var loaded by rememberSaveable { mutableStateOf(routineId == null) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var showMissingName by rememberSaveable { mutableStateOf(false) }
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (!loaded && routineId != null) {
        LaunchedEffect(routineId) {
            val routine = routines.get(routineId)
            if (routine == null) {
                onClose()
            } else {
                name = routine.name
                description = routine.description
                time = routine.time
                loaded = true
            }
        }
        return
    }

    val draft = RoutineDraft(name = name, description = description, time = time)
    val problems = draft.problems
    val save = {
        if (problems.isNotEmpty()) {
            showMissingName = true
        } else if (!busy) {
            busy = true
            scope.launch {
                if (routineId == null) routines.create(draft) else routines.update(routineId, draft)
                onClose()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (routineId == null) R.string.routine_new else R.string.routine_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (routineId != null) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    TextButton(onClick = save) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.routine_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = DraftProblem.NameTooLong in problems || (showMissingName && DraftProblem.NameMissing in problems),
                supportingText = {
                    when {
                        showMissingName && DraftProblem.NameMissing in problems -> Text(stringResource(R.string.routine_name_missing))
                        else -> LengthHint(name.trim().length, RoutineDraft.NAME_MAX)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.routine_description)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = DraftProblem.DescriptionTooLong in problems,
                supportingText = { LengthHint(description.trim().length, RoutineDraft.DESCRIPTION_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
            LabelledValue(
                label = stringResource(R.string.routine_time),
                value = time.clockTime(),
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(R.string.routine_time_change),
                    role = Role.Button,
                    onClick = { choosingTime = true },
                ),
            )
            LabelledValue(label = stringResource(R.string.routine_repeat), value = stringResource(R.string.routine_repeat_daily))
        }
    }

    if (choosingTime) {
        TimeDialog(
            initial = time,
            onConfirm = {
                time = it
                choosingTime = false
            },
            onDismiss = { choosingTime = false },
        )
    }
    if (confirmingDelete && routineId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.routine_delete_title)) },
            text = { Text(stringResource(R.string.routine_delete_text, name.trim())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        busy = true
                        scope.launch {
                            routines.delete(routineId)
                            onClose()
                        }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** How much of [max] is used up, or that it is exceeded. */
@Composable
private fun LengthHint(length: Int, max: Int) {
    Text(
        if (length > max) stringResource(R.string.text_too_long, max) else stringResource(R.string.text_length, length, max),
    )
}

@Composable
private fun LabelledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Typing the time is quicker than a clock dial: four digits, always on the 24-hour clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routine_time_title)) },
        text = { TimeInput(state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
