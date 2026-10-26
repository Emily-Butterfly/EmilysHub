package de.emilyshub.ui.wishes

import androidx.annotation.StringRes
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.notes.NoteLink
import de.emilyshub.notes.Notes
import de.emilyshub.tags.Tags
import de.emilyshub.todos.Todos
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.GoalField
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.TagsField
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.notes.LinkedNotes
import de.emilyshub.wishes.Goal
import de.emilyshub.wishes.WishDraft
import de.emilyshub.wishes.WishProblem
import de.emilyshub.wishes.WishStatus
import de.emilyshub.wishes.Wishes
import de.emilyshub.wishes.toDraft
import kotlinx.coroutines.flow.flowOf
import java.io.Serializable
import java.time.LocalDate
import kotlin.math.roundToInt

/** Which wish the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface WishEditorTarget : Serializable {
    /** A new wish, or a new subgoal of the goal [parentId]. */
    data class New(val parentId: Long? = null) : WishEditorTarget

    data class Edit(val wishId: Long) : WishEditorTarget
}

/**
 * Creates or changes a wish with its title, description, period, status, tags, the goal it is part of and its progress
 * (SPEC.md §6), or deletes it: one with subgoals with them, or with its parts moved up a level, as the user chooses.
 * Done, it came true today unless another day gone by is chosen. A wish saved can have a subgoal added, [onOpenWish], or
 * be made into a to-do hanging under it with a tap, [onOpenTodo]; both save it first. Leaving without saving drops the
 * changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishEditorScreen(
    wishes: Wishes,
    todos: Todos,
    tags: Tags,
    notes: Notes,
    target: WishEditorTarget,
    onClose: () -> Unit,
    onOpenWish: (WishEditorTarget) -> Unit,
    onOpenTodo: (Long) -> Unit,
) {
    val wishId = (target as? WishEditorTarget.Edit)?.wishId
    val parentId = (target as? WishEditorTarget.New)?.parentId
    var draft by rememberSaveable { mutableStateOf(WishDraft(title = "", parentId = parentId)) }
    var loaded by rememberSaveable { mutableStateOf(wishId == null) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var showMissingTitle by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Saving and deleting close the editor at once; this keeps a second tap in the same frame from doing it twice.
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // As saved, with its parts.
    val goal by remember(wishes, wishId) { wishId?.let(wishes::goal) ?: flowOf(null) }.collectAsState(initial = null)

    if (!loaded && wishId != null) {
        LaunchedEffect(wishId) {
            val wish = wishes.get(wishId)
            if (wish == null) {
                onClose()
            } else {
                draft = wish.toDraft(wishes.tagIdsOf(wishId))
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
            closeWith { if (wishId == null) wishes.create(saved) else wishes.update(wishId, saved) }
        }
    }

    /** Saves the wish, if it can be, and then goes on with [next], which leaves the editor. */
    fun saveAndThen(next: suspend (wishId: Long) -> Unit) {
        if (wishId == null || closing) return
        if (problems.isNotEmpty()) {
            showMissingTitle = true
            return
        }
        closing = true
        val saved = draft
        scope.launchNow {
            wishes.update(wishId, saved)
            next(wishId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                wishId != null -> R.string.wish_edit
                                parentId != null -> R.string.wish_subgoal_new
                                else -> R.string.wish_new
                            },
                        ),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (wishId != null) {
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
            val titleMissing = showMissingTitle && WishProblem.TitleMissing in problems
            OutlinedTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = { Text(stringResource(R.string.wish_title)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = WishProblem.TitleTooLong in problems || titleMissing,
                supportingText = {
                    when {
                        titleMissing -> Text(stringResource(R.string.wish_title_missing))
                        else -> LengthHint(InputText.lengthOf(cleaned.title), WishDraft.TITLE_MAX)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { draft = draft.copy(description = it) },
                label = { Text(stringResource(R.string.wish_description)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = WishProblem.DescriptionTooLong in problems,
                supportingText = { LengthHint(InputText.lengthOf(cleaned.description), WishDraft.DESCRIPTION_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.period,
                onValueChange = { draft = draft.copy(period = it) },
                label = { Text(stringResource(R.string.wish_period)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = WishProblem.PeriodTooLong in problems,
                supportingText = {
                    if (WishProblem.PeriodTooLong in problems) {
                        LengthHint(InputText.lengthOf(cleaned.period), WishDraft.PERIOD_MAX)
                    } else {
                        Text(stringResource(R.string.wish_period_hint))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            val today = wishes.today()
            StatusField(
                status = draft.status,
                doneOn = draft.copy(status = WishStatus.Done).cameTrueOn(today) ?: today,
                today = today,
                onChange = { status, doneOn -> draft = draft.copy(status = status, doneOn = doneOn) },
            )
            TagsField(tags, draft.tagIds, onChange = { draft = draft.copy(tagIds = it) })
            GoalField(
                wishes = wishes,
                selected = draft.parentId,
                label = R.string.wish_parent,
                none = R.string.wish_parent_none,
                onChange = { draft = draft.copy(parentId = it) },
                excluding = wishId,
            )
            ProgressField(
                goal = goal,
                status = draft.status,
                manual = draft.manualProgress,
                onChange = { draft = draft.copy(manualProgress = it) },
            )
            if (wishId != null) {
                LinkedNotes(notes, NoteLink.Goal(wishId))
                OutlinedButton(onClick = { saveAndThen { onOpenWish(WishEditorTarget.New(parentId = it)) } }) {
                    Text(stringResource(R.string.wish_add_subgoal))
                }
                OutlinedButton(onClick = { saveAndThen { onOpenTodo(todos.create(wishes.todoDraftOf(it))) } }) {
                    Text(stringResource(R.string.wish_to_todo))
                }
            }
        }
    }

    if (confirmingDelete && wishId != null) {
        val withSubgoals = goal?.subgoals?.isNotEmpty() == true
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.wish_delete_title)) },
            text = {
                Text(stringResource(if (withSubgoals) R.string.wish_delete_parts_text else R.string.wish_delete_text, cleaned.title))
            },
            confirmButton = {
                Row {
                    if (withSubgoals) {
                        TextButton(onClick = { closeWith { wishes.delete(wishId) } }) {
                            Text(stringResource(R.string.wish_delete_with_subgoals))
                        }
                    }
                    TextButton(onClick = { closeWith { wishes.deleteMovingPartsUp(wishId) } }) {
                        Text(stringResource(if (withSubgoals) R.string.wish_delete_moving_up else R.string.delete))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * How far the wish has come (SPEC.md §6): worked out from the parts of its [goal] as saved, none for one new, or set by
 * hand in steps of 5 % until reset; all of it once done.
 */
@Composable
private fun ProgressField(goal: Goal?, status: WishStatus, manual: Int?, onChange: (Int?) -> Unit) {
    val automatic = goal?.automaticPercent ?: 0
    Column {
        SectionTitle(stringResource(R.string.wish_progress))
        when {
            status == WishStatus.Done -> ProgressValue(FULL, R.string.wish_status_done)
            manual != null -> {
                ProgressValue(manual, R.string.wish_progress_manual)
                val label = stringResource(R.string.wish_progress)
                Slider(
                    value = manual.toFloat(),
                    onValueChange = { onChange((it / STEP).roundToInt() * STEP) },
                    valueRange = 0f..FULL.toFloat(),
                    steps = FULL / STEP - 1,
                    modifier = Modifier.semantics { contentDescription = label },
                )
                TextButton(onClick = { onChange(null) }) {
                    Text(
                        if (goal?.hasCountingParts == true) {
                            stringResource(R.string.wish_progress_reset_to, automatic)
                        } else {
                            stringResource(R.string.wish_progress_reset)
                        },
                    )
                }
            }
            else -> {
                val source = if (goal?.hasCountingParts == true) R.string.wish_progress_automatic else R.string.wish_progress_no_parts
                ProgressValue(automatic, source)
                TextButton(onClick = { onChange(automatic) }) { Text(stringResource(R.string.wish_progress_set)) }
            }
        }
    }
}

/** "40 %" and where it comes from, as [source] says. */
@Composable
private fun ProgressValue(percent: Int, @StringRes source: Int) {
    LabelledValue(stringResource(source), stringResource(R.string.statistics_percent, percent))
}

private const val FULL = WishDraft.PROGRESS_MAX
private const val STEP = 5

/**
 * Open, in progress or done (SPEC.md §6); done, on the day it came true, [doneOn], which can be any day up to [today].
 * The day is kept while switching back and forth, until saved.
 */
@Composable
private fun StatusField(status: WishStatus, doneOn: LocalDate, today: LocalDate, onChange: (WishStatus, LocalDate) -> Unit) {
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    Column {
        SectionTitle(stringResource(R.string.wish_status))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WishStatus.entries.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = status == choice,
                    onClick = { onChange(choice, doneOn) },
                    shape = SegmentedButtonDefaults.itemShape(index, WishStatus.entries.size),
                    label = { Text(stringResource(choice.label)) },
                )
            }
        }
        if (status == WishStatus.Done) DateValue(stringResource(R.string.wish_done_on), doneOn, onClick = { choosingDate = true })
    }

    if (choosingDate) {
        DateDialog(
            initial = doneOn,
            latest = today,
            onConfirm = { picked ->
                choosingDate = false
                onChange(status, picked)
            },
            onDismiss = { choosingDate = false },
        )
    }
}
