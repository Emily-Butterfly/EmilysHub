package de.emilyshub.ui.notes

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.notes.NoteTab
import de.emilyshub.notes.Notes
import de.emilyshub.notes.TabSelection
import de.emilyshub.notes.tabNameProblem
import de.emilyshub.ui.fields.NameDialog
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.plusFabSpace

/**
 * The user's own tabs of notes (SPEC.md §7), in their order, after "Alle" and "Allgemein", which are always there: "+"
 * adds one, tapping one renames it, its arrows move it, and deleting one with notes asks whether they go to "Allgemein"
 * or with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTabsScreen(notes: Notes, onBack: () -> Unit) {
    val tabs by remember(notes) { notes.tabs() }.collectAsState(initial = null)
    val all by remember(notes) { notes.notes(TabSelection.All) }.collectAsState(initial = null)
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.note_tabs_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.note_tab_create))
            }
        },
    ) { contentPadding ->
        val list = tabs ?: return@Scaffold
        LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.note_tabs_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (list.isEmpty()) {
                item(key = "none") { Text(stringResource(R.string.note_tabs_empty), modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            itemsIndexed(list, key = { _, tab -> tab.id }) { index, tab ->
                val moved = { by: Int ->
                    val order = list.map { it.id }.toMutableList().apply { add(index + by, removeAt(index)) }
                    scope.launchNow { notes.arrangeTabs(order) }
                }
                ListItem(
                    headlineContent = { Text(tab.name) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { moved(-1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.note_tab_up, tab.name))
                            }
                            IconButton(onClick = { moved(1) }, enabled = index < list.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.note_tab_down, tab.name))
                            }
                            IconButton(onClick = { deleting = tab.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.note_tab_delete, tab.name))
                            }
                        }
                    },
                    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.note_tab_rename)) { renaming = tab.id },
                )
            }
        }
    }

    val list = tabs.orEmpty()
    if (creating) {
        TabNameDialog(
            title = R.string.note_tab_create,
            confirm = R.string.note_tab_create_confirm,
            initial = "",
            others = list,
            onConfirm = { name ->
                creating = false
                scope.launchNow { notes.createTab(name) }
            },
            onDismiss = { creating = false },
        )
    }
    list.find { it.id == renaming }?.let { tab ->
        TabNameDialog(
            title = R.string.note_tab_rename,
            confirm = R.string.note_tab_rename_confirm,
            initial = tab.name,
            others = list - tab,
            onConfirm = { name ->
                renaming = null
                scope.launchNow { notes.renameTab(tab.id, name) }
            },
            onDismiss = { renaming = null },
        )
    }
    val notesIn = all ?: return
    list.find { it.id == deleting }?.let { tab ->
        val count = notesIn.count { it.tabId == tab.id }
        val delete = { work: suspend () -> Unit ->
            deleting = null
            scope.launchNow { work() }
        }
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.note_tab_delete_title)) },
            text = {
                Text(
                    if (count == 0) {
                        stringResource(R.string.note_tab_delete_text, tab.name)
                    } else {
                        pluralStringResource(R.plurals.note_tab_delete_notes_text, count, count, tab.name)
                    },
                )
            },
            confirmButton = {
                Row {
                    if (count == 0) {
                        TextButton(onClick = { delete { notes.deleteTabMovingNotes(tab.id) } }) {
                            Text(stringResource(R.string.delete))
                        }
                    } else {
                        TextButton(onClick = { delete { notes.deleteTabWithNotes(tab.id) } }) {
                            Text(stringResource(R.string.note_tab_delete_with_notes))
                        }
                        TextButton(onClick = { delete { notes.deleteTabMovingNotes(tab.id) } }) {
                            Text(stringResource(R.string.note_tab_delete_moving))
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/** Asks for a tab's name, starting out with [initial]; one with a problem, also next to the [others], cannot be confirmed. */
@Composable
private fun TabNameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    initial: String,
    others: List<NoteTab>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) = NameDialog(
    title = title,
    confirm = confirm,
    label = R.string.note_tab_name,
    taken = R.string.note_tab_name_taken,
    max = NoteTab.NAME_MAX,
    initial = initial,
    problemOf = { tabNameProblem(it, others) },
    onConfirm = onConfirm,
    onDismiss = onDismiss,
)
