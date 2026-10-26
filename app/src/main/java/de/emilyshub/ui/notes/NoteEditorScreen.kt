package de.emilyshub.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import de.emilyshub.notes.Container
import de.emilyshub.notes.Markdown
import de.emilyshub.notes.NoteDraft
import de.emilyshub.notes.NoteProblem
import de.emilyshub.notes.Notes
import de.emilyshub.notes.toDraft
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.launchNow
import java.io.Serializable

/** Which note the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface NoteEditorTarget : Serializable {
    /** A new note, in [container]. */
    data class New(val container: Container) : NoteEditorTarget

    data class Edit(val noteId: Long) : NoteEditorTarget
}

/**
 * Writes a note (SPEC.md §7): its title, its text in Markdown, where it lies and what it is linked to, while
 * "Bearbeiten" shows; "Vorschau" shows the text as it reads, without HTML and with no link to open (SPEC.md §9), and
 * what it is linked to. A new note starts in "Bearbeiten", one saved opens in "Vorschau", to be read. Deleting one asks
 * first; leaving without saving drops the changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(notes: Notes, target: NoteEditorTarget, onClose: () -> Unit) {
    val noteId = (target as? NoteEditorTarget.Edit)?.noteId
    val startIn = (target as? NoteEditorTarget.New)?.container ?: Container()
    var draft by rememberSaveable { mutableStateOf(NoteDraft(title = "", container = startIn)) }
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
                draft = note.toDraft(notes.linksOf(noteId))
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
                Preview(notes, draft)
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
        LocationField(notes, draft.container, onChange = { onChange(draft.copy(container = it)) })
        LinksField(notes, draft.links, onChange = { onChange(draft.copy(links = it)) })
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

/** The title and text of [draft] as they read, the text read anew only once it changes, and what it is linked to. */
@Composable
private fun Preview(notes: Notes, draft: NoteDraft) {
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
        item { LinksSummary(notes, draft.links) }
    }
}

/** Where the note lies, "Arabisch › Grammatik": tapped, it offers the top of each tab and every folder. */
@Composable
private fun LocationField(notes: Notes, container: Container, onChange: (Container) -> Unit) {
    val loaded by remember(notes) { notes.notebook() }.collectAsState(initial = null)
    var choosing by rememberSaveable { mutableStateOf(false) }
    val notebook = loaded ?: return
    // As the note would be saved: a tab or folder deleted meanwhile, or a folder moved to another tab, is left out.
    val shown = notebook.stillThere(container)
    LabelledValue(
        label = stringResource(R.string.note_location),
        value = pathName(shown, notebook),
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.note_move), role = Role.Button) { choosing = true },
    )

    if (choosing) {
        ContainerChoiceDialog(
            notebook = notebook,
            current = shown,
            onChoose = { chosen ->
                choosing = false
                onChange(chosen)
            },
            onDismiss = { choosing = false },
        )
    }
}
