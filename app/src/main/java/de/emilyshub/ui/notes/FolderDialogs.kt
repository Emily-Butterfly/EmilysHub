package de.emilyshub.ui.notes

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.notes.NoteFolder
import de.emilyshub.notes.NoteSort
import de.emilyshub.notes.folderNameProblem
import de.emilyshub.ui.fields.NameDialog
import de.emilyshub.ui.fields.RadioOption

/** Asks for a folder's name, starting out with [initial]; one with a problem cannot be confirmed. */
@Composable
internal fun FolderNameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) = NameDialog(
    title = title,
    confirm = confirm,
    label = R.string.note_folder_name,
    max = NoteFolder.NAME_MAX,
    initial = initial,
    problemOf = ::folderNameProblem,
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)

/** Asks how to sort what lies in a folder: by name, by last change or by hand, [sort] chosen now. */
@Composable
internal fun SortDialog(sort: NoteSort, onChoose: (NoteSort) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_sort_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                NoteSort.entries.forEach { choice ->
                    RadioOption(sortName(choice), selected = choice == sort, onSelect = { onChoose(choice) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Deleting a folder asks first; one with something in it, whether that moves up a level or goes with it. */
@Composable
internal fun DeleteFolderDialog(
    folder: NoteFolder,
    hasContents: Boolean,
    onDelete: (withContents: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_folder_delete_title)) },
        text = {
            val text = if (hasContents) R.string.note_folder_delete_contents_text else R.string.note_folder_delete_text
            Text(stringResource(text, folder.name))
        },
        confirmButton = {
            Row {
                if (hasContents) {
                    TextButton(onClick = { onDelete(true) }) { Text(stringResource(R.string.note_folder_delete_with_contents)) }
                }
                TextButton(onClick = { onDelete(false) }) {
                    Text(stringResource(if (hasContents) R.string.note_folder_delete_moving_up else R.string.delete))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun sortName(sort: NoteSort): String = stringResource(
    when (sort) {
        NoteSort.Name -> R.string.note_sort_name
        NoteSort.Changed -> R.string.note_sort_changed
        NoteSort.Manual -> R.string.note_sort_manual
    },
)
