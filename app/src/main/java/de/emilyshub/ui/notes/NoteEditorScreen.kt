package de.emilyshub.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.notes.Markdown
import de.emilyshub.notes.NoteDraft
import de.emilyshub.notes.NoteProblem
import de.emilyshub.notes.NoteTab
import de.emilyshub.notes.Notes
import de.emilyshub.notes.toDraft
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.RadioOption
import de.emilyshub.ui.launchNow
import java.io.Serializable

/** Which note the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface NoteEditorTarget : Serializable {
    /** A new note, in the tab [tabId]; none: in "Allgemein". */
    data class New(val tabId: Long?) : NoteEditorTarget

    data class Edit(val noteId: Long) : NoteEditorTarget
}

/**
 * Writes a note (SPEC.md §7): its title, its text in Markdown and the tab it is in, while "Bearbeiten" shows; "Vorschau"
 * shows the text as it reads, without HTML and with no link to open (SPEC.md §9). A new note starts in "Bearbeiten", one
 * saved opens in "Vorschau", to be read. Deleting one asks first; leaving without saving drops the changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(notes: Notes, target: NoteEditorTarget, onClose: () -> Unit) {
    val noteId = (target as? NoteEditorTarget.Edit)?.noteId
    var draft by rememberSaveable { mutableStateOf(NoteDraft(title = "", tabId = (target as? NoteEditorTarget.New)?.tabId)) }
    var loaded by rememberSaveable { mutableStateOf(noteId == null) }
    var previewing by rememberSaveable { mutableStateOf(noteId != null) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var showMissingTitle by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Counts the tries to save without a title fit to save, each of which goes to the title.
    var titleAsked by remember { mutableIntStateOf(0) }
    // Saving and deleting close the editor at once; this keeps a second tap in the same frame from doing it twice.
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (!loaded && noteId != null) {
        LaunchedEffect(noteId) {
            val note = notes.get(noteId)
            if (note == null) {
                onClose()
            } else {
                draft = note.toDraft()
                loaded = true
            }
        }
        return
    }

    val problems = draft.problems
    val closeWith = { work: suspend () -> Unit ->
        if (!closing) {
            closing = true
            scope.launchNow { work() }
            onClose()
        }
    }
    val save = {
        if (problems.isNotEmpty()) {
            // The problem shows at its field, which a long text may have scrolled away.
            showMissingTitle = true
            previewing = false
            if (NoteProblem.TitleMissing in problems || NoteProblem.TitleTooLong in problems) titleAsked++
        } else {
            val saved = draft
            closeWith { if (noteId == null) notes.create(saved) else notes.update(noteId, saved) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (noteId == null) R.string.note_new else R.string.note_edit),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (noteId != null) {
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
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            ModeChoice(previewing, onChange = { previewing = it })
            if (previewing) {
                Preview(draft)
            } else {
                Writing(notes, draft, problems, showMissingTitle, titleAsked, onChange = { draft = it })
            }
        }
    }

    if (confirmingDelete && noteId != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.note_delete_title)) },
            text = { Text(stringResource(R.string.note_delete_text, draft.cleaned().title)) },
            confirmButton = {
                TextButton(onClick = { closeWith { notes.delete(noteId) } }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** "Bearbeiten" or "Vorschau". */
@Composable
private fun ModeChoice(previewing: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        listOf(false to R.string.note_writing, true to R.string.note_preview).forEachIndexed { index, (preview, label) ->
            SegmentedButton(
                selected = previewing == preview,
                onClick = { onChange(preview) },
                shape = SegmentedButtonDefaults.itemShape(index, count = 2),
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun Writing(
    notes: Notes,
    draft: NoteDraft,
    problems: Set<NoteProblem>,
    showMissingTitle: Boolean,
    titleAsked: Int,
    onChange: (NoteDraft) -> Unit,
) {
    val cleaned = draft.cleaned()
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(titleAsked) { if (titleAsked > 0) titleFocus.requestFocus() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val titleMissing = showMissingTitle && NoteProblem.TitleMissing in problems
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onChange(draft.copy(title = it)) },
            label = { Text(stringResource(R.string.note_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            isError = NoteProblem.TitleTooLong in problems || titleMissing,
            supportingText = {
                when {
                    titleMissing -> Text(stringResource(R.string.note_title_missing))
                    else -> LengthHint(InputText.lengthOf(cleaned.title), NoteDraft.TITLE_MAX)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(titleFocus),
        )
        TabField(notes, draft.tabId, onChange = { onChange(draft.copy(tabId = it)) })
        OutlinedTextField(
            value = draft.text,
            onValueChange = { onChange(draft.copy(text = it)) },
            label = { Text(stringResource(R.string.note_text)) },
            minLines = 8,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            isError = NoteProblem.TextTooLong in problems,
            supportingText = {
                Column {
                    Text(stringResource(R.string.note_text_hint))
                    LengthHint(InputText.lengthOf(cleaned.text), NoteDraft.TEXT_MAX)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )
    }
}

/** The title and text of [draft] as they read, the text read anew only once it changes. */
@Composable
private fun Preview(draft: NoteDraft) {
    val blocks = remember(draft.text) { Markdown.parse(draft.text) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(InputText.singleLine(draft.title), style = MaterialTheme.typography.headlineMedium) }
        if (blocks.isEmpty()) {
            item { Text(stringResource(R.string.note_preview_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        markdownBlocks(blocks)
    }
}

/** The tab the note is in: tapped, it offers "Allgemein" and the user's tabs. One deleted meanwhile is chosen no more. */
@Composable
private fun TabField(notes: Notes, selected: Long?, onChange: (Long?) -> Unit) {
    val tabs by remember(notes) { notes.tabs() }.collectAsState(initial = null)
    var choosing by rememberSaveable { mutableStateOf(false) }
    val list = tabs ?: return
    val chosen = list.find { it.id == selected }
    val general = stringResource(R.string.notes_tab_general)
    LabelledValue(
        label = stringResource(R.string.note_tab),
        value = chosen?.name ?: general,
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.note_tab_choose), role = Role.Button) { choosing = true },
    )

    if (choosing) {
        val choose = { tab: NoteTab? ->
            choosing = false
            onChange(tab?.id)
        }
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.note_tab_choose)) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                ) {
                    RadioOption(general, selected = chosen == null, onSelect = { choose(null) })
                    list.forEach { tab -> RadioOption(tab.name, selected = tab == chosen, onSelect = { choose(tab) }) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
