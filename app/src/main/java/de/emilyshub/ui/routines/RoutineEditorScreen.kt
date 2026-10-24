package de.emilyshub.ui.routines

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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.DraftProblem
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import de.emilyshub.ui.launchNow
import java.io.Serializable
import java.time.LocalTime

/** Which routine the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface RoutineEditorTarget : Serializable {
    data object New : RoutineEditorTarget

    data class Edit(val routineId: Long) : RoutineEditorTarget
}

/**
 * Creates or changes a routine with its times, repetition, how it reminds and how long it can be caught up on.
 * Leaving without saving drops the changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(routines: Routines, target: RoutineEditorTarget, onClose: () -> Unit) {
    val routineId = (target as? RoutineEditorTarget.Edit)?.routineId
    var draft by rememberSaveable {
        mutableStateOf(RoutineDraft(name = "", description = "", times = listOf(DEFAULT_TIME), repetition = Repetition.Daily))
    }
    // The routine as it was before editing; none for a new one.
    var original by rememberSaveable { mutableStateOf<RoutineDraft?>(null) }
    var loaded by rememberSaveable { mutableStateOf(routineId == null) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var showMissingName by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Saving and deleting close the editor at once; this keeps a second tap in the same frame from doing it twice.
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val today = remember(routines) { routines.today() }

    if (!loaded && routineId != null) {
        LaunchedEffect(routineId) {
            val routine = routines.get(routineId)
            if (routine == null) {
                onClose()
            } else {
                draft = with(routine) { RoutineDraft(name, description, times, repetition, followUps, fullScreen, window) }
                original = draft
                loaded = true
            }
        }
        return
    }

    val problems = draft.problems
    val cleaned = draft.cleaned()
    // Only for new times or a new date: a routine kept as it was after its date may still be renamed.
    // Saving does not check this again, since the moment can pass in between.
    val rescheduled = original.let { it == null || it.times != cleaned.times || it.repetition != cleaned.repetition }
    val goneBy = rescheduled && !routines.comesDue(cleaned.times, cleaned.repetition)
    val save = {
        if (problems.isNotEmpty() || goneBy) {
            showMissingName = true
        } else if (!closing) {
            closing = true
            val saved = draft
            scope.launchNow { if (routineId == null) routines.create(saved) else routines.update(routineId, saved) }
            onClose()
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
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.routine_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = DraftProblem.NameTooLong in problems || (showMissingName && DraftProblem.NameMissing in problems),
                supportingText = {
                    when {
                        showMissingName && DraftProblem.NameMissing in problems -> Text(stringResource(R.string.routine_name_missing))
                        else -> LengthHint(RoutineDraft.lengthOf(cleaned.name), RoutineDraft.NAME_MAX)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it) },
                label = { Text(stringResource(R.string.routine_description)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = DraftProblem.DescriptionTooLong in problems,
                supportingText = { LengthHint(RoutineDraft.lengthOf(cleaned.description), RoutineDraft.DESCRIPTION_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
            TimesFields(draft.times, onChange = { draft = draft.copy(times = it) })
            RepetitionFields(draft.repetition, today, goneBy, onChange = { draft = draft.copy(repetition = it) })
            FollowUpFields(draft.followUps, onChange = { draft = draft.copy(followUps = it) })
            FullScreenField(draft.fullScreen, onChange = { draft = draft.copy(fullScreen = it) })
            TimeWindowFields(draft.window, onChange = { draft = draft.copy(window = it) })
        }
    }

    if (confirmingDelete && routineId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.routine_delete_title)) },
            text = { Text(stringResource(R.string.routine_delete_text, cleaned.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!closing) {
                            closing = true
                            scope.launchNow { routines.delete(routineId) }
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
internal fun LabelledValue(label: String, value: String, modifier: Modifier = Modifier) {
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

private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
