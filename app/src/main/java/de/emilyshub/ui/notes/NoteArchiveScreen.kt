package de.emilyshub.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.notes.NoteEntry
import de.emilyshub.notes.Notebook
import de.emilyshub.notes.Notes
import de.emilyshub.notes.firstLine
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.launchNow

/**
 * The notes and folders archived (SPEC.md §7), the last archived first, each with where it comes from: each can be
 * brought back there, or deleted for good after asking. Only to read: a note opens in its preview ([onOpenNote]), a
 * folder shows what is in it, its notes opening the same way, and going back leaves it a level at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteArchiveScreen(notes: Notes, onOpenNote: (Long) -> Unit, onBack: () -> Unit) {
    val loaded by remember(notes) { notes.notebook() }.collectAsState(initial = null)
    // The folder in the archive open; none: the archive itself.
    var folderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<NoteEntryKey?>(null) }
    val scope = rememberCoroutineScope()
    val notebook = loaded
    // One brought back or deleted meanwhile leaves the archive shown.
    val open = folderId?.let { notebook?.folder(it) }?.takeIf { notebook?.isArchived(it) == true }
    val up = {
        // Up to the folder it is in while that is in the archive too, else to the archive itself.
        folderId = open?.parentId?.takeIf { parent -> notebook?.folder(parent)?.let(notebook::isArchived) == true }
    }
    BackHandler(enabled = open != null, onBack = up)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(open?.name ?: stringResource(R.string.archive_title), modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    IconButton(onClick = if (open == null) onBack else up) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        if (notebook == null) return@Scaffold
        val entries = if (open == null) notebook.archived() else notebook.entriesIn(open.inside, withArchived = true)
        if (entries.isEmpty()) {
            PlaceholderScreen(
                stringResource(if (open == null) R.string.note_archive_empty else R.string.notes_folder_empty),
                contentPadding,
            )
            return@Scaffold
        }
        LazyColumn(contentPadding = contentPadding) {
            if (open != null) item { ReadOnlyHint() }
            items(entries, key = { it.key.toString() }) { entry ->
                ArchivedRow(
                    entry = entry,
                    // Where it comes from, in the archive itself; within a folder, all comes from it.
                    from = if (open == null) pathName(notebook.containerOf(entry), notebook) else null,
                    onRestore = if (open == null) ({ scope.launchNow { restore(notes, entry) } }) else null,
                    onDelete = if (open == null) ({ deleting = entry.key }) else null,
                    onClick = {
                        when (entry) {
                            is NoteEntry.OfFolder -> folderId = entry.folder.id
                            is NoteEntry.OfNote -> onOpenNote(entry.note.id)
                        }
                    },
                )
            }
        }
    }

    val doomed = notebook?.archived()?.find { it.key == deleting } ?: return
    AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.note_delete_for_good_title)) },
        text = {
            Text(
                when (doomed) {
                    is NoteEntry.OfFolder -> stringResource(R.string.note_folder_delete_for_good_text, doomed.folder.name)
                    is NoteEntry.OfNote -> stringResource(R.string.note_delete_text, doomed.note.title)
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    deleting = null
                    scope.launchNow {
                        when (doomed) {
                            is NoteEntry.OfFolder -> notes.deleteArchivedFolder(doomed.folder.id)
                            is NoteEntry.OfNote -> notes.delete(doomed.note.id)
                        }
                    }
                },
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Which of a folder or a note an entry is, to be kept across rotation. */
private data class NoteEntryKey(val folder: Boolean, val id: Long) : java.io.Serializable {
    override fun toString() = if (folder) "folder:$id" else "note:$id"
}

private val NoteEntry.key: NoteEntryKey
    get() = when (this) {
        is NoteEntry.OfFolder -> NoteEntryKey(folder = true, folder.id)
        is NoteEntry.OfNote -> NoteEntryKey(folder = false, note.id)
    }

private fun Notebook.containerOf(entry: NoteEntry) = when (entry) {
    is NoteEntry.OfFolder -> containerOf(entry.folder)
    is NoteEntry.OfNote -> containerOf(entry.note)
}

private suspend fun restore(notes: Notes, entry: NoteEntry) = when (entry) {
    is NoteEntry.OfFolder -> notes.restoreFolder(entry.folder.id)
    is NoteEntry.OfNote -> notes.restoreNote(entry.note.id)
}

/** That what is in the archive is only to read. */
@Composable
internal fun ReadOnlyHint() {
    Text(
        stringResource(R.string.note_archived_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * A folder or a note in the archive, with where it comes [from], if given; brought back by [onRestore] and deleted for
 * good by [onDelete], where they are given.
 */
@Composable
private fun ArchivedRow(entry: NoteEntry, from: String?, onRestore: (() -> Unit)?, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    val (name, line) = when (entry) {
        is NoteEntry.OfFolder -> "$FOLDER ${entry.folder.name}" to null
        is NoteEntry.OfNote -> entry.note.title to entry.note.firstLine
    }
    ListItem(
        overlineContent = from?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        headlineContent = { Text(name) },
        supportingContent = line?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = if (onRestore == null && onDelete == null) {
            null
        } else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onRestore?.let { TextButton(onClick = it) { Text(stringResource(R.string.archive_restore)) } }
                    onDelete?.let {
                        val plain = when (entry) {
                            is NoteEntry.OfFolder -> entry.folder.name
                            is NoteEntry.OfNote -> entry.note.title
                        }
                        IconButton(onClick = it) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.note_delete_for_good, plain))
                        }
                    }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
