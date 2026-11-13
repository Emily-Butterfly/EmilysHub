package de.emilyshub.ui.notes

import android.widget.Toast
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.data.Saving
import de.emilyshub.notes.Container
import de.emilyshub.notes.Markdown
import de.emilyshub.notes.NoteDraft
import de.emilyshub.notes.NoteEditing
import de.emilyshub.notes.NoteProblem
import de.emilyshub.notes.Notes
import de.emilyshub.tags.Tags
import de.emilyshub.ui.differsAsSaved
import de.emilyshub.ui.fields.FirstField
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.SaveBar
import de.emilyshub.ui.fields.TagsField
import de.emilyshub.ui.fields.rememberFirstField
import de.emilyshub.ui.rememberClosing
import de.emilyshub.ui.rememberLeaving
import java.io.Serializable

/** Which note the editor shows. Serializable, so that the open editor survives rotation and the lock. */
sealed interface NoteEditorTarget : Serializable {
    /** A new note, in [container]. */
    data class New(val container: Container) : NoteEditorTarget

    data class Edit(val noteId: Long) : NoteEditorTarget
}

/**
 * Writes a note (SPEC.md §7): its title, its text in Markdown, where it lies, its tags and what it is linked to, while
 * "Bearbeiten" shows; "Vorschau" shows the text as it reads, without HTML and with no link to open (SPEC.md §9), and
 * what it is linked to. A new note starts in "Bearbeiten", one saved opens in "Vorschau", to be read. Deleting one asks
 * first; left with changes it saves them by itself, if it can, else it asks; archiving it saves them first. One in the archive (SPEC.md §7) only
 * shows its preview, to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(notes: Notes, tags: Tags, target: NoteEditorTarget, onClose: () -> Unit) {
    val noteId = (target as? NoteEditorTarget.Edit)?.noteId
    val startIn = (target as? NoteEditorTarget.New)?.container ?: Container()
    // What is edited, as it was saved: known at once for a new one, read first for one saved.
    var editing by rememberSaveable { mutableStateOf(if (noteId == null) NoteEditing.New else null) }
    var draft by rememberSaveable { mutableStateOf(NoteDraft(title = "", container = startIn)) }
    // The form as a new one starts out, to tell whether anything was typed.
    val fresh = rememberSaveable { draft }
    var previewing by rememberSaveable { mutableStateOf(noteId != null) }
    // Only after a first try to save, so that a fresh form does not start out in red.
    var showMissingTitle by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    // Counts the tries to save without a title fit to save, each of which goes to the title.
    var titleAsked by remember { mutableIntStateOf(0) }

    val opened = editing
    if (opened == null) {
        LaunchedEffect(noteId) {
            val saved = notes.editing(checkNotNull(noteId))
            if (saved == null) {
                onClose()
            } else {
                draft = checkNotNull(saved.original)
                editing = saved
            }
        }
        return
    }

    val problems = draft.problems
    val closeWith = rememberClosing(onClose)
    // A new one opens with the keyboard in its title (SPEC.md §8), once, not again on each return from the preview.
    val title = rememberFirstField(new = noteId == null)
    /** Saves the note, then does [after] with it, if any; or shows what keeps it from being saved. */
    fun save(after: (suspend (Long) -> Unit)? = null) {
        if (problems.isNotEmpty()) {
            // The problem shows at its field, which a long text may have scrolled away.
            showMissingTitle = true
            previewing = false
            if (NoteProblem.TitleMissing in problems || NoteProblem.TitleTooLong in problems) titleAsked++
        } else {
            val saved = draft
            closeWith {
                val saving = notes.save(opened, saved)
                if (saving is Saving.Saved) after?.invoke(saving.id)
            }
        }
    }

    if (opened.archived) {
        ArchivedNote(notes, draft, onClose)
        return
    }
    val context = LocalContext.current.applicationContext
    val leave = rememberLeaving(
        closing = closeWith,
        changed = { differsAsSaved(draft, opened.original ?: fresh, NoteDraft::cleaned) },
        onSave = { save() },
        onDiscard = onClose,
        // Saved by itself if it can be, a title missing taken from its text (SPEC.md §8); a new one with neither title
        // nor text is no note.
        leavesItself = leavesItself@{
            val titled = draft.withTitleFromText()
            when {
                noteId == null && !InputText.isVisible(titled.title) && !InputText.isVisible(titled.text) -> closeWith {}
                titled.problems.isNotEmpty() -> return@leavesItself false
                else -> closeWith {
                    if (notes.save(opened, titled) is Saving.Saved) {
                        Toast.makeText(context, R.string.note_saved_itself, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            true
        },
    )

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
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (noteId != null) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                        TextButton(onClick = { save(after = notes::archiveNote) }) { Text(stringResource(R.string.note_archive)) }
                    }
                },
            )
        },
        bottomBar = { SaveBar(onSave = { save() }) },
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
                Writing(notes, tags, draft, problems, showMissingTitle, titleAsked, title, onChange = { draft = it })
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

/** A note in the archive, only to read: its preview, with what it is linked to, and that it is in the archive. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedNote(notes: Notes, draft: NoteDraft, onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.note_archived), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            ReadOnlyHint()
            Preview(notes, draft)
        }
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
    tags: Tags,
    draft: NoteDraft,
    problems: Set<NoteProblem>,
    showMissingTitle: Boolean,
    titleAsked: Int,
    title: FirstField,
    onChange: (NoteDraft) -> Unit,
) {
    val cleaned = draft.cleaned()
    LaunchedEffect(titleAsked) { if (titleAsked > 0) title.focus() }
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
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            isError = NoteProblem.TitleTooLong in problems || titleMissing,
            supportingText = {
                when {
                    titleMissing -> Text(stringResource(R.string.note_title_missing))
                    else -> LengthHint(InputText.lengthOf(cleaned.title), NoteDraft.TITLE_MAX)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(title.requester),
        )
        LocationField(notes, draft.container, onChange = { onChange(draft.copy(container = it)) })
        TagsField(tags, draft.tagIds, onChange = { onChange(draft.copy(tagIds = it)) })
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
