package de.emilyshub.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.notes.Container
import de.emilyshub.notes.Note
import de.emilyshub.notes.NoteEntry
import de.emilyshub.notes.NoteFolder
import de.emilyshub.notes.NoteSort
import de.emilyshub.notes.NoteTab
import de.emilyshub.notes.Notebook
import de.emilyshub.notes.Notes
import de.emilyshub.notes.TabSelection
import de.emilyshub.notes.firstLine
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.fields.treeIndent
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.plusFabSpace
import java.io.Serializable

/**
 * Which tab the notes show, and which folder in it is open; none: its top. Serializable, so that it survives rotation
 * and the lock.
 */
data class NotesView(val tab: TabSelection = TabSelection.All, val folderId: Long? = null) : Serializable

/**
 * The notes (SPEC.md §7) as [view] has them: the tab "Alle", "Allgemein", then the user's own, in their order; a tab
 * deleted meanwhile shows "Alle". "Alle" shows every note with where it lies, the one changed last first. Any other tab
 * shows what lies in its open folder, or at its top: the folders first, each opening up to show what is in it, then
 * the notes, as the folder is sorted. Above them, the path down to the folder leads back to any level, as does going
 * back, a level at a time. Tapping a folder opens it, tapping a note opens it in its editor, "+" starts a note where the
 * list is, and the tabs are managed apart.
 */
@Composable
fun NotesScreen(
    notes: Notes,
    view: NotesView,
    onViewChange: (NotesView) -> Unit,
    contentPadding: PaddingValues,
    onOpenNote: (Long) -> Unit,
    onCreateNote: (Container) -> Unit,
    onManageTabs: () -> Unit,
) {
    val loaded by remember(notes) { notes.notebook() }.collectAsState(initial = null)
    val notebook = loaded ?: return
    val tab = view.tab
    val shown = if (tab is TabSelection.Own && notebook.tabs.none { it.id == tab.tabId }) TabSelection.All else tab
    // A folder deleted, or moved to another tab, meanwhile leaves the top of the tab shown.
    val here = shown.top?.let { top -> notebook.stillThere(top.copy(folderId = view.folderId)) }
    val onOpenFolder = { folderId: Long? -> onViewChange(view.copy(folderId = folderId)) }

    val openedFolder = here?.folderId?.let(notebook::folder)
    BackHandler(enabled = openedFolder != null) { openedFolder?.let { onOpenFolder(notebook.containerOf(it).folderId) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = contentPadding.calculateTopPadding())) {
            TabBar(notebook.tabs, shown, onSelect = { onViewChange(NotesView(it)) }, onManageTabs)
            val bottom = PaddingValues(bottom = contentPadding.calculateBottomPadding())
            if (here == null) {
                AllNotes(notebook, bottom, onOpenNote)
            } else {
                if (here.folderId != null) PathBar(notebook, here, onOpenFolder)
                ContainerContents(notes, notebook, here, bottom, onOpenFolder, onOpenNote)
            }
        }
        FloatingActionButton(
            onClick = { onCreateNote(here ?: Container()) },
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

/** The path from the tab down to the open folder, "Arabisch › Grammatik › Verben": each level above it opens with a tap. */
@Composable
private fun PathBar(notebook: Notebook, here: Container, onOpenFolder: (Long?) -> Unit) {
    val path = notebook.path(here.folderId)
    val label = stringResource(R.string.note_path)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onOpenFolder(null) }) { Text(tabName(here.tabId, notebook.tabs)) }
        path.dropLast(1).forEach { folder ->
            Text(PATH_SEPARATOR)
            TextButton(onClick = { onOpenFolder(folder.id) }) { Text(folder.name) }
        }
        Text(PATH_SEPARATOR)
        Text(path.last().name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

/** Every note, the one changed last first, each with where it lies. */
@Composable
private fun AllNotes(notebook: Notebook, contentPadding: PaddingValues, onOpenNote: (Long) -> Unit) {
    val all = notebook.all()
    if (all.isEmpty()) {
        PlaceholderScreen(stringResource(R.string.notes_empty), contentPadding)
        return
    }
    LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
        items(all, key = { it.id }) { note ->
            val lyingIn = pathName(notebook.containerOf(note), notebook)
            NoteRow(note, depth = 0, lyingIn = lyingIn, menu = emptyList(), onClick = { onOpenNote(note.id) })
        }
    }
}

/**
 * What lies in [here], as it is sorted, which can be chosen, with a way to make a folder there. Each folder opens up and
 * closes again by its arrow; each folder and note has its menu: to move it elsewhere, up and down when sorted by hand,
 * and, a folder, to rename and delete it.
 */
@Composable
private fun ContainerContents(
    notes: Notes,
    notebook: Notebook,
    here: Container,
    contentPadding: PaddingValues,
    onOpenFolder: (Long?) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    // The folders opened up, to show what is in them.
    var expanded by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var choosingSort by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    var movingFolder by rememberSaveable { mutableStateOf<Long?>(null) }
    var movingNote by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    /** The entries of [container] at [depth], each folder opened up followed by what is in it, the same way. */
    fun lines(container: Container, depth: Int): List<Line> = notebook.entriesIn(container).flatMap { entry ->
        when (entry) {
            is NoteEntry.OfFolder -> listOf(Line.OfFolder(entry.folder, depth, container)) +
                if (entry.folder.id in expanded) lines(entry.folder.inside, depth + 1) else emptyList()
            is NoteEntry.OfNote -> listOf(Line.OfNote(entry.note, depth, container))
        }
    }

    val upLabel = stringResource(R.string.note_up)
    val downLabel = stringResource(R.string.note_down)

    /** "Nach oben" and "Nach unten" for [id] among [ids], which [arrange] puts in their new order. */
    fun moves(id: Long, ids: List<Long>, arrange: suspend (List<Long>) -> Unit): List<MenuEntry> {
        val index = ids.indexOf(id)
        val move = { by: Int -> scope.launchNow { arrange(ids.toMutableList().apply { add(index + by, removeAt(index)) }) } }
        return listOf(
            MenuEntry(upLabel, enabled = index > 0) { move(-1) },
            MenuEntry(downLabel, enabled = index < ids.lastIndex) { move(1) },
        )
    }

    val sort = notebook.sortOf(here)
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { creating = true }) { Text(stringResource(R.string.note_folder_create)) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { choosingSort = true }) { Text(stringResource(R.string.note_sort, sortName(sort))) }
    }
    val shownLines = lines(here, depth = 0)
    val rename = stringResource(R.string.note_folder_rename_confirm)
    val moveElsewhere = stringResource(R.string.note_move)
    val delete = stringResource(R.string.delete)
    val pin = stringResource(R.string.note_pin)
    val unpin = stringResource(R.string.note_unpin)
    val onToday = stringResource(R.string.note_pin_today)
    val offToday = stringResource(R.string.note_unpin_today)
    if (shownLines.isEmpty()) {
        PlaceholderScreen(
            stringResource(if (here.folderId == null) R.string.notes_tab_empty else R.string.notes_folder_empty),
            contentPadding,
        )
    } else {
        LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
            // By place, not by key: so that one pinned, going up, does not take the list along out of sight.
            items(shownLines) { line ->
                val byHand = notebook.sortOf(line.container) == NoteSort.Manual
                when (line) {
                    is Line.OfFolder -> {
                        val folder = line.folder
                        // Moved among those pinned, or among those not.
                        val siblings = notebook.foldersIn(line.container).filter { it.pinned == folder.pinned }.map { it.id }
                        val order = if (byHand) {
                            moves(folder.id, siblings) { notes.arrangeFolders(line.container, it) }
                        } else {
                            emptyList()
                        }
                        FolderRow(
                            folder = folder,
                            depth = line.depth,
                            hasContents = !notebook.isEmpty(folder.inside),
                            expanded = folder.id in expanded,
                            onToggle = { expanded = if (folder.id in expanded) expanded - folder.id else expanded + folder.id },
                            menu = listOf(
                                MenuEntry(rename) { renaming = folder.id },
                                MenuEntry(if (folder.pinned) unpin else pin) {
                                    scope.launchNow { notes.pinFolder(folder.id, !folder.pinned) }
                                },
                                MenuEntry(moveElsewhere) { movingFolder = folder.id },
                            ) + order + MenuEntry(delete) { deleting = folder.id },
                            onClick = { onOpenFolder(folder.id) },
                        )
                    }
                    is Line.OfNote -> {
                        val note = line.note
                        val siblings = notebook.notesIn(line.container).filter { it.pinned == note.pinned }.map { it.id }
                        val order = if (byHand) {
                            moves(note.id, siblings) { notes.arrangeNotes(line.container, it) }
                        } else {
                            emptyList()
                        }
                        NoteRow(
                            note = note,
                            depth = line.depth,
                            lyingIn = null,
                            menu = listOf(
                                MenuEntry(if (note.pinned) unpin else pin) {
                                    scope.launchNow { notes.pinNote(note.id, !note.pinned) }
                                },
                                MenuEntry(if (note.onToday) offToday else onToday) {
                                    scope.launchNow { notes.pinOnToday(note.id, !note.onToday) }
                                },
                                MenuEntry(moveElsewhere) { movingNote = note.id },
                            ) + order,
                            onClick = { onOpenNote(note.id) },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        FolderNameDialog(
            title = R.string.note_folder_create,
            confirm = R.string.note_folder_create_confirm,
            initial = "",
            onConfirm = { name ->
                creating = false
                scope.launchNow { notes.createFolder(name, here) }
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let(notebook::folder)?.let { folder ->
        FolderNameDialog(
            title = R.string.note_folder_rename,
            confirm = R.string.note_folder_rename_confirm,
            initial = folder.name,
            onConfirm = { name ->
                renaming = null
                scope.launchNow { notes.renameFolder(folder.id, name) }
            },
            onDismiss = { renaming = null },
        )
    }
    if (choosingSort) {
        SortDialog(
            sort = sort,
            onChoose = { chosen ->
                choosingSort = false
                scope.launchNow { notes.sort(here, chosen) }
            },
            onDismiss = { choosingSort = false },
        )
    }
    movingFolder?.let(notebook::folder)?.let { folder ->
        ContainerChoiceDialog(
            notebook = notebook,
            current = notebook.containerOf(folder),
            excluding = notebook.inAndBelow(folder.id),
            onChoose = { to ->
                movingFolder = null
                scope.launchNow { notes.moveFolder(folder.id, to) }
            },
            onDismiss = { movingFolder = null },
        )
    }
    movingNote?.let(notebook::note)?.let { note ->
        ContainerChoiceDialog(
            notebook = notebook,
            current = notebook.containerOf(note),
            onChoose = { to ->
                movingNote = null
                scope.launchNow { notes.moveNote(note.id, to) }
            },
            onDismiss = { movingNote = null },
        )
    }
    deleting?.let(notebook::folder)?.let { folder ->
        DeleteFolderDialog(
            folder = folder,
            hasContents = !notebook.isEmpty(folder.inside),
            onDelete = { withContents ->
                deleting = null
                scope.launchNow {
                    if (withContents) notes.deleteFolderWithContents(folder.id) else notes.deleteFolderMovingUp(folder.id)
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

/** A line of the list: a folder, or a note, at its depth, in its [container]. */
private sealed interface Line {
    val depth: Int
    val container: Container

    data class OfFolder(val folder: NoteFolder, override val depth: Int, override val container: Container) : Line

    data class OfNote(val note: Note, override val depth: Int, override val container: Container) : Line
}

/** What a menu offers: [label], done by [onClick] unless not [enabled]. */
private data class MenuEntry(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** A folder, opening when tapped; one with something in it opens up and closes again by its arrow. */
@Composable
private fun FolderRow(
    folder: NoteFolder,
    depth: Int,
    hasContents: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    menu: List<MenuEntry>,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Text(if (folder.pinned) PIN + FOLDER else FOLDER, style = MaterialTheme.typography.titleMedium) },
        headlineContent = { Text(folder.name) },
        trailingContent = {
            Row {
                if (hasContents) {
                    val action = stringResource(
                        if (expanded) R.string.note_folder_collapse else R.string.note_folder_expand,
                        folder.name,
                    )
                    IconButton(onClick = onToggle, modifier = Modifier.semantics { contentDescription = action }) {
                        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                }
                MoreMenu(folder.name, menu)
            }
        },
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.note_folder_open), onClick = onClick)
            .padding(start = treeIndent(depth, INDENT)),
    )
}

/** A note with its first line, and where it is [lyingIn], if given; and its menu, if any. */
@Composable
private fun NoteRow(note: Note, depth: Int, lyingIn: String?, menu: List<MenuEntry>, onClick: () -> Unit) {
    ListItem(
        overlineContent = lyingIn?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = if (note.pinned) ({ Text(PIN, style = MaterialTheme.typography.titleMedium) }) else null,
        headlineContent = { Text(note.title) },
        supportingContent = note.firstLine?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = if (menu.isEmpty()) null else ({ MoreMenu(note.title, menu) }),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = treeIndent(depth, INDENT)),
    )
}

/** "⋮", offering the [entries] for [name]. */
@Composable
private fun MoreMenu(name: String, entries: List<MenuEntry>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.note_more, name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    enabled = entry.enabled,
                    onClick = {
                        open = false
                        entry.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun nameOf(choice: TabSelection, own: List<NoteTab>): String = when (choice) {
    TabSelection.All -> stringResource(R.string.notes_tab_all)
    TabSelection.General -> tabName(null, own)
    is TabSelection.Own -> tabName(choice.tabId, own)
}

private const val FOLDER = "📁"
internal const val PIN = "📌"
private val INDENT = 24.dp
