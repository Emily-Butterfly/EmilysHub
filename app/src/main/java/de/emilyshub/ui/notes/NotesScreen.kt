package de.emilyshub.ui.notes

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
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
import de.emilyshub.notes.NoteFolder
import de.emilyshub.notes.NoteSort
import de.emilyshub.notes.NoteTab
import de.emilyshub.notes.Notebook
import de.emilyshub.notes.Notes
import de.emilyshub.notes.TabSelection
import de.emilyshub.notes.folderNameProblem
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.fields.NameDialog
import de.emilyshub.ui.fields.RadioOption
import de.emilyshub.ui.fields.goalIndent
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.plusFabSpace

/**
 * The notes (SPEC.md §7) of the tab [selected]: "Alle", "Allgemein", then the user's own, in their order; a tab deleted
 * meanwhile shows "Alle". "Alle" shows every note with where it lies, the one changed last first. Any other tab shows
 * what lies in its folder [openFolder], or at its top: the folders first, each opening up to show what is in it, then
 * the notes, as the folder is sorted. Above them, the path down to the folder leads back to any level, as does going
 * back, a level at a time. Tapping a folder opens it, tapping a note opens it in its editor, "+" starts a note where the
 * list is, and the tabs are managed apart.
 */
@Composable
fun NotesScreen(
    notes: Notes,
    selected: TabSelection,
    openFolder: Long?,
    onSelect: (TabSelection) -> Unit,
    onOpenFolder: (Long?) -> Unit,
    contentPadding: PaddingValues,
    onOpenNote: (Long) -> Unit,
    onCreateNote: (Container) -> Unit,
    onManageTabs: () -> Unit,
) {
    val book by remember(notes) { notes.notebook() }.collectAsState(initial = null)
    val notebook = book ?: return
    val shown = if (selected is TabSelection.Own && notebook.tabs.none { it.id == selected.tabId }) TabSelection.All else selected
    // A folder deleted, or moved to another tab, meanwhile leaves the top of the tab shown.
    val here = shown.top?.let { top ->
        openFolder?.let(notebook::folder)?.takeIf { it.tabId == top.tabId }?.let { Container(top.tabId, it.id) } ?: top
    }

    val openedFolder = here?.folderId?.let(notebook::folder)
    BackHandler(enabled = openedFolder != null) { openedFolder?.let { onOpenFolder(notebook.containerOf(it).folderId) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(top = contentPadding.calculateTopPadding())) {
            TabBar(notebook.tabs, shown, onSelect, onManageTabs)
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
    fun lines(container: Container, depth: Int): List<Line> = notebook.foldersIn(container).flatMap { folder ->
        val inside = Container(folder.tabId, folder.id)
        listOf(Line.OfFolder(folder, depth, container)) + if (folder.id in expanded) lines(inside, depth + 1) else emptyList()
    } + notebook.notesIn(container).map { Line.OfNote(it, depth, container) }

    val up = stringResource(R.string.note_up)
    val down = stringResource(R.string.note_down)

    /** "Nach oben" and "Nach unten" for [id] among [ids], which [arrange] puts in their new order. */
    fun moves(id: Long, ids: List<Long>, arrange: suspend (List<Long>) -> Unit): List<MenuEntry> {
        val index = ids.indexOf(id)
        val move = { by: Int -> scope.launchNow { arrange(ids.toMutableList().apply { add(index + by, removeAt(index)) }) } }
        return listOf(
            MenuEntry(up, enabled = index > 0) { move(-1) },
            MenuEntry(down, enabled = index < ids.lastIndex) { move(1) },
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
    if (shownLines.isEmpty()) {
        PlaceholderScreen(
            stringResource(if (here.folderId == null) R.string.notes_tab_empty else R.string.notes_folder_empty),
            contentPadding,
        )
    } else {
        LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
            items(shownLines, key = Line::key) { line ->
                val byHand = notebook.sortOf(line.container) == NoteSort.Manual
                when (line) {
                    is Line.OfFolder -> {
                        val folder = line.folder
                        val inside = Container(folder.tabId, folder.id)
                        val siblings = notebook.foldersIn(line.container).map { it.id }
                        val order = when {
                            byHand -> moves(folder.id, siblings) { notes.arrangeFolders(line.container, it) }
                            else -> emptyList()
                        }
                        FolderRow(
                            folder = folder,
                            depth = line.depth,
                            hasContents = notebook.foldersIn(inside).isNotEmpty() || notebook.notesIn(inside).isNotEmpty(),
                            expanded = folder.id in expanded,
                            onToggle = { expanded = if (folder.id in expanded) expanded - folder.id else expanded + folder.id },
                            menu = listOf(
                                MenuEntry(rename) { renaming = folder.id },
                                MenuEntry(moveElsewhere) { movingFolder = folder.id },
                            ) + order + MenuEntry(delete) { deleting = folder.id },
                            onClick = { onOpenFolder(folder.id) },
                        )
                    }
                    is Line.OfNote -> {
                        val note = line.note
                        val siblings = notebook.notesIn(line.container).map { it.id }
                        val order = when {
                            byHand -> moves(note.id, siblings) { notes.arrangeNotes(line.container, it) }
                            else -> emptyList()
                        }
                        NoteRow(
                            note = note,
                            depth = line.depth,
                            lyingIn = null,
                            menu = listOf(MenuEntry(moveElsewhere) { movingNote = note.id }) + order,
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
    movingNote?.let { id -> notebook.all().find { it.id == id } }?.let { note ->
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
        val inside = Container(folder.tabId, folder.id)
        DeleteFolderDialog(
            folder = folder,
            hasContents = notebook.foldersIn(inside).isNotEmpty() || notebook.notesIn(inside).isNotEmpty(),
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
    val key: String
    val depth: Int
    val container: Container

    data class OfFolder(val folder: NoteFolder, override val depth: Int, override val container: Container) : Line {
        override val key get() = "folder ${folder.id}"
    }

    data class OfNote(val note: Note, override val depth: Int, override val container: Container) : Line {
        override val key get() = "note ${note.id}"
    }
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
        leadingContent = { Text(FOLDER, style = MaterialTheme.typography.titleMedium) },
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
            .padding(start = goalIndent(depth, INDENT)),
    )
}

/** A note with its first line, and where it is [lyingIn], if given; and its menu, if any. */
@Composable
private fun NoteRow(note: Note, depth: Int, lyingIn: String?, menu: List<MenuEntry>, onClick: () -> Unit) {
    val firstLine = note.text.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
    ListItem(
        overlineContent = lyingIn?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        headlineContent = { Text(note.title) },
        supportingContent = firstLine?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = if (menu.isEmpty()) null else ({ MoreMenu(note.title, menu) }),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = goalIndent(depth, INDENT)),
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

/** Asks for a folder's name, starting out with [initial]; one with a problem cannot be confirmed. */
@Composable
private fun FolderNameDialog(
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

/** Name, last change or by hand, [sort] chosen. */
@Composable
private fun SortDialog(sort: NoteSort, onChoose: (NoteSort) -> Unit, onDismiss: () -> Unit) {
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
private fun DeleteFolderDialog(
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
private fun sortName(sort: NoteSort): String = stringResource(
    when (sort) {
        NoteSort.Name -> R.string.note_sort_name
        NoteSort.Changed -> R.string.note_sort_changed
        NoteSort.Manual -> R.string.note_sort_manual
    },
)

@Composable
private fun nameOf(choice: TabSelection, own: List<NoteTab>): String = when (choice) {
    TabSelection.All -> stringResource(R.string.notes_tab_all)
    TabSelection.General -> tabName(null, own)
    is TabSelection.Own -> tabName(choice.tabId, own)
}

private const val FOLDER = "📁"
private val INDENT = 24.dp
