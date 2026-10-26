package de.emilyshub.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.notes.Note
import de.emilyshub.notes.NoteTab
import de.emilyshub.notes.Notes
import de.emilyshub.notes.TabSelection
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.plusFabSpace

/**
 * The notes (SPEC.md §7) of the tab [selected]: "Alle", "Allgemein", then the user's own, in their order; a tab deleted
 * meanwhile shows "Alle". Each note shows its title and first line, in "Alle" also its tab. Tapping one opens it, "+"
 * starts one in the tab shown, and the tabs are managed apart.
 */
@Composable
fun NotesScreen(
    notes: Notes,
    selected: TabSelection,
    onSelect: (TabSelection) -> Unit,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    onCreate: (tabId: Long?) -> Unit,
    onManageTabs: () -> Unit,
) {
    val tabs by remember(notes) { notes.tabs() }.collectAsState(initial = null)
    val own = tabs ?: return
    val shown = if (selected is TabSelection.Own && own.none { it.id == selected.tabId }) TabSelection.All else selected
    val list by remember(notes, shown) { notes.notes(shown) }.collectAsState(initial = null)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = contentPadding.calculateTopPadding())) {
            TabBar(own, shown, onSelect, onManageTabs)
            val bottom = PaddingValues(bottom = contentPadding.calculateBottomPadding())
            val shownList = list
            when {
                shownList == null -> Unit
                shownList.isEmpty() -> PlaceholderScreen(
                    stringResource(if (shown == TabSelection.All) R.string.notes_empty else R.string.notes_tab_empty),
                    bottom,
                )
                else -> LazyColumn(contentPadding = bottom.plusFabSpace()) {
                    items(shownList, key = { it.id }) { note ->
                        val tabName = if (shown == TabSelection.All) tabNameOf(note, own) else null
                        NoteRow(note, tabName, onClick = { onOpen(note.id) })
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onCreate((shown as? TabSelection.Own)?.tabId) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(contentPadding)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.note_create))
        }
    }
}

/** "Alle", "Allgemein" and the user's tabs, side by side, scrolling sideways once they do not fit; then the way to manage them. */
@Composable
private fun TabBar(own: List<NoteTab>, shown: TabSelection, onSelect: (TabSelection) -> Unit, onManageTabs: () -> Unit) {
    val choices = listOf(TabSelection.All, TabSelection.General) + own.map { TabSelection.Own(it.id) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PrimaryScrollableTabRow(selectedTabIndex = choices.indexOf(shown), edgePadding = 8.dp, modifier = Modifier.weight(1f)) {
            choices.forEach { choice ->
                Tab(
                    selected = choice == shown,
                    onClick = { onSelect(choice) },
                    text = { Text(nameOf(choice, own), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        IconButton(onClick = onManageTabs) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.note_tabs_manage))
        }
    }
}

/** A note with its first line, and the [tabName] it is in, if given. */
@Composable
private fun NoteRow(note: Note, tabName: String?, onClick: () -> Unit) {
    val firstLine = note.text.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
    ListItem(
        overlineContent = tabName?.let { { Text(it) } },
        headlineContent = { Text(note.title) },
        supportingContent = firstLine?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun nameOf(choice: TabSelection, own: List<NoteTab>): String = when (choice) {
    TabSelection.All -> stringResource(R.string.notes_tab_all)
    TabSelection.General -> stringResource(R.string.notes_tab_general)
    is TabSelection.Own -> own.first { it.id == choice.tabId }.name
}

@Composable
private fun tabNameOf(note: Note, own: List<NoteTab>): String =
    own.find { it.id == note.tabId }?.name ?: stringResource(R.string.notes_tab_general)
