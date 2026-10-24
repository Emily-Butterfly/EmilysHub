package de.emilyshub.ui.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.places.Places
import de.emilyshub.tags.Tags
import de.emilyshub.todos.Due
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.TodoProblem
import de.emilyshub.todos.Todos
import de.emilyshub.todos.toDraft
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.ChecklistFields
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.PlacesField
import de.emilyshub.ui.fields.PriorityField
import de.emilyshub.ui.fields.TagsField
import de.emilyshub.ui.fields.TimeDialog
import de.emilyshub.ui.launchNow
import java.io.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/** Which to-do the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface TodoEditorTarget : Serializable {
    data object New : TodoEditorTarget

    data class Edit(val todoId: Long) : TodoEditorTarget
}

/**
 * Creates or changes a to-do with its title, description, due, places, priority, checklist and tags (SPEC.md §5), or
 * deletes it. Leaving without saving drops the changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorScreen(todos: Todos, tags: Tags, places: Places, target: TodoEditorTarget, onClose: () -> Unit) {
    val todoId = (target as? TodoEditorTarget.Edit)?.todoId
    var draft by rememberSaveable { mutableStateOf(TodoDraft(title = "")) }
    var loaded by rememberSaveable { mutableStateOf(todoId == null) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var showMissingTitle by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Saving and deleting close the editor at once; this keeps a second tap in the same frame from doing it twice.
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (!loaded && todoId != null) {
        LaunchedEffect(todoId) {
            val todo = todos.get(todoId)
            if (todo == null) {
                onClose()
            } else {
                draft = todo.toDraft(todos.checklist(todoId), todos.tagIdsOf(todoId), todos.placeIdsOf(todoId))
                loaded = true
            }
        }
        return
    }

    val problems = draft.problems
    val cleaned = draft.cleaned()
    val closeWith = { work: suspend () -> Unit ->
        if (!closing) {
            closing = true
            scope.launchNow { work() }
            onClose()
        }
    }
    val save = {
        if (problems.isNotEmpty()) {
            showMissingTitle = true
        } else {
            val saved = draft
            closeWith { if (todoId == null) todos.create(saved) else todos.update(todoId, saved) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (todoId == null) R.string.todo_new else R.string.todo_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (todoId != null) {
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
            val titleMissing = showMissingTitle && TodoProblem.TitleMissing in problems
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text(stringResource(R.string.todo_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = TodoProblem.TitleTooLong in problems || titleMissing,
                supportingText = {
                    when {
                        titleMissing -> Text(stringResource(R.string.todo_title_missing))
                        else -> LengthHint(InputText.lengthOf(cleaned.title), TodoDraft.TITLE_MAX)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it) },
                label = { Text(stringResource(R.string.todo_description)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = TodoProblem.DescriptionTooLong in problems,
                supportingText = { LengthHint(InputText.lengthOf(cleaned.description), TodoDraft.DESCRIPTION_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
            DueFields(
                due = draft.due,
                today = todos.today(),
                start = todos.nextFullHour(),
                onChange = { draft = draft.copy(due = it) },
            )
            PlacesField(
                places = places,
                selected = draft.placeIds,
                hintNone = R.string.todo_places_none,
                hintChosen = R.string.todo_places_chosen,
                onChange = { draft = draft.copy(placeIds = it) },
            )
            PriorityField(draft.priority, onChange = { draft = draft.copy(priority = it) })
            ChecklistFields(draft.checklist, onChange = { draft = draft.copy(checklist = it) })
            TagsField(tags, draft.tagIds, onChange = { draft = draft.copy(tagIds = it) })
        }
    }

    if (confirmingDelete && todoId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.todo_delete_title)) },
            text = { Text(stringResource(R.string.todo_delete_text, cleaned.title)) },
            confirmButton = {
                TextButton(onClick = { closeWith { todos.delete(todoId) } }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Which of [Due] the editor offers, in its order. */
private enum class DueKind { None, On, At }

/**
 * Whenever, on a day, or on a day at a time (SPEC.md §5). A day starts out [today], a day with a time at the [start];
 * switching back and forth keeps the day and time chosen.
 */
@Composable
private fun DueFields(due: Due, today: LocalDate, start: LocalDateTime, onChange: (Due) -> Unit) {
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    // What was chosen last, also while due whenever.
    var chosenDate by rememberSaveable { mutableStateOf(due.date) }
    var chosenTime by rememberSaveable { mutableStateOf((due as? Due.At)?.time) }
    val change = { changed: Due ->
        changed.date?.let { chosenDate = it }
        (changed as? Due.At)?.let { chosenTime = it.time }
        onChange(changed)
    }
    val date = due.date
    val kind = when (due) {
        Due.None -> DueKind.None
        is Due.On -> DueKind.On
        is Due.At -> DueKind.At
    }
    Column {
        SectionTitle(stringResource(R.string.todo_due))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            DueKind.entries.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = kind == choice,
                    onClick = {
                        change(
                            when (choice) {
                                DueKind.None -> Due.None
                                DueKind.On -> Due.On(chosenDate ?: today)
                                DueKind.At -> Due.At(chosenDate ?: start.toLocalDate(), chosenTime ?: start.toLocalTime())
                            },
                        )
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, DueKind.entries.size),
                    label = {
                        Text(
                            stringResource(
                                when (choice) {
                                    DueKind.None -> R.string.todo_due_none
                                    DueKind.On -> R.string.todo_due_on
                                    DueKind.At -> R.string.todo_due_at
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (date != null) DateValue(stringResource(R.string.todo_due_date), date, onClick = { choosingDate = true })
        if (due is Due.At) {
            LabelledValue(
                label = stringResource(R.string.todo_due_time),
                value = due.time.clockTime(),
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(R.string.field_time_change),
                    role = Role.Button,
                    onClick = { choosingTime = true },
                ),
            )
        }
    }

    if (choosingDate && date != null) {
        DateDialog(
            initial = date,
            earliest = null,
            onConfirm = { picked ->
                choosingDate = false
                change(if (due is Due.At) due.copy(date = picked) else Due.On(picked))
            },
            onDismiss = { choosingDate = false },
        )
    }
    if (choosingTime && due is Due.At) {
        TimeDialog(
            initial = due.time,
            onConfirm = { picked ->
                choosingTime = false
                change(due.copy(time = picked))
            },
            onDismiss = { choosingTime = false },
        )
    }
}
