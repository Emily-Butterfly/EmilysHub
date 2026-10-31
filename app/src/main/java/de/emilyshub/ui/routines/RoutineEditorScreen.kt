package de.emilyshub.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.notes.NoteLink
import de.emilyshub.notes.Notes
import de.emilyshub.places.Places
import de.emilyshub.routines.DraftProblem
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.routines.Routines
import de.emilyshub.routines.toDraft
import de.emilyshub.tags.Tags
import de.emilyshub.ui.fields.ChecklistFields
import de.emilyshub.ui.fields.GoalField
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.PlacesField
import de.emilyshub.ui.fields.PriorityField
import de.emilyshub.ui.fields.TagsField
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.notes.LinkedNotes
import de.emilyshub.wishes.Wishes
import java.io.Serializable
import java.time.LocalTime

/** Which routine the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface RoutineEditorTarget : Serializable {
    data object New : RoutineEditorTarget

    data class Edit(val routineId: Long) : RoutineEditorTarget
}

/**
 * Creates or changes a routine with its times, repetition, how it reminds and how long it can be caught up on, and the
 * goal it is part of with what it aims at for that (SPEC.md §6). Leaving without saving drops the changes; a tag made
 * here ([TagsField]) is saved at once, like one made in the settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    routines: Routines,
    wishes: Wishes,
    tags: Tags,
    places: Places,
    notes: Notes,
    target: RoutineEditorTarget,
    onClose: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
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
    // How the routine is in use; none for a new one.
    var status by rememberSaveable { mutableStateOf<RoutineStatus?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
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
                draft = routine.toDraft(routines.checklist(routineId), routines.tagIdsOf(routineId), routines.placeIdsOf(routineId))
                original = draft
                status = routine.status
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
    // Does [work] and closes the editor, once: its changes not saved are dropped.
    val closeWith = { work: suspend () -> Unit ->
        if (!closing) {
            closing = true
            scope.launchNow { work() }
            onClose()
        }
    }
    val save = {
        if (problems.isNotEmpty() || goneBy) {
            showMissingName = true
        } else {
            val saved = draft
            closeWith { if (routineId == null) routines.create(saved) else routines.update(routineId, saved) }
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
                    if (routineId != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_actions))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val paused = status == RoutineStatus.Paused
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (paused) R.string.routine_resume else R.string.routine_pause)) },
                                    onClick = {
                                        menuOpen = false
                                        closeWith { if (paused) routines.resume(routineId) else routines.pause(routineId) }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.routine_archive)) },
                                    onClick = {
                                        menuOpen = false
                                        closeWith { routines.archive(routineId) }
                                    },
                                )
                            }
                        }
                    }
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
                        else -> LengthHint(InputText.lengthOf(cleaned.name), RoutineDraft.NAME_MAX)
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
                supportingText = { LengthHint(InputText.lengthOf(cleaned.description), RoutineDraft.DESCRIPTION_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
            PriorityField(draft.priority, onChange = { draft = draft.copy(priority = it) })
            ChecklistFields(draft.checklist, onChange = { draft = draft.copy(checklist = it) })
            TagsField(tags, draft.tagIds, onChange = { draft = draft.copy(tagIds = it) })
            PlacesField(
                places = places,
                selected = draft.placeIds,
                hintNone = R.string.routine_places_everywhere,
                hintChosen = R.string.routine_places_chosen,
                onChange = { draft = draft.copy(placeIds = it) },
            )
            GoalField(
                wishes = wishes,
                selected = draft.goalId,
                label = R.string.goal_field,
                none = R.string.goal_field_none,
                onChange = { draft = draft.copy(goalId = it) },
            )
            if (draft.goalId != null) TargetFields(draft.target, onChange = { draft = draft.copy(target = it) })
            TimesFields(draft.times, onChange = { draft = draft.copy(times = it) })
            RepetitionFields(draft.repetition, today, goneBy, onChange = { draft = draft.copy(repetition = it) })
            FollowUpFields(draft.followUps, onChange = { draft = draft.copy(followUps = it) })
            FullScreenField(draft.fullScreen, onChange = { draft = draft.copy(fullScreen = it) })
            TimeWindowFields(draft.window, onChange = { draft = draft.copy(window = it) })
            if (routineId != null) LinkedNotes(notes, NoteLink.Routine(routineId), onOpen = onOpenNote)
        }
    }

    if (confirmingDelete && routineId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.routine_delete_title)) },
            text = { Text(stringResource(R.string.routine_delete_text, cleaned.name)) },
            confirmButton = {
                TextButton(
                    onClick = { closeWith { routines.delete(routineId) } },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
