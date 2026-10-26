package de.emilyshub.ui.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.notes.Container
import de.emilyshub.notes.NoteTab
import de.emilyshub.notes.Notebook
import de.emilyshub.ui.fields.RadioOption
import de.emilyshub.ui.fields.goalIndent

/**
 * Where something is to lie (SPEC.md §7: "Verschieben nach…"): the top of "Allgemein" and of each tab, each with its
 * folders below it, indented, in the order they show in; but not the folders [excluding], so that no folder goes into
 * itself. What it lies in now, [current], is chosen.
 */
@Composable
fun ContainerChoiceDialog(
    notebook: Notebook,
    current: Container,
    onChoose: (Container) -> Unit,
    onDismiss: () -> Unit,
    excluding: Set<Long> = emptySet(),
) {
    /** The container and those in it, each folder in turn the same way. */
    fun choices(container: Container, depth: Int): List<Pair<Container, Int>> = listOf(container to depth) +
        notebook.foldersIn(container).filterNot { it.id in excluding }.flatMap { choices(Container(it.tabId, it.id), depth + 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_move_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                val tops = (listOf(null) + notebook.tabs.map { it.id }).map { Container(it) }
                tops.flatMap { choices(it, depth = 0) }.forEach { (container, depth) ->
                    val name = container.folderId?.let { notebook.folder(it)?.name } ?: tabName(container.tabId, notebook.tabs)
                    Column(Modifier.padding(start = goalIndent(depth, step = 16.dp))) {
                        RadioOption(name, selected = container == current, onSelect = { onChoose(container) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Where [container] is, from its tab down: "Arabisch › Grammatik › Verben". */
@Composable
fun pathName(container: Container, notebook: Notebook): String =
    (listOf(tabName(container.tabId, notebook.tabs)) + notebook.path(container.folderId).map { it.name }).joinToString(PATH_SEPARATOR)

/** The name of the tab [tabId] among the user's [own]; none, or one deleted meanwhile: "Allgemein". */
@Composable
fun tabName(tabId: Long?, own: List<NoteTab>): String =
    own.find { it.id == tabId }?.name ?: stringResource(R.string.notes_tab_general)

const val PATH_SEPARATOR = " › "
