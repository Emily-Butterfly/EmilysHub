package de.emilyshub.ui.notes

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.notes.LinkCandidate
import de.emilyshub.notes.LinkKind
import de.emilyshub.notes.Note
import de.emilyshub.notes.NoteLink
import de.emilyshub.notes.Notes
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.launchNow

/**
 * What the note is linked to (SPEC.md §7), "Ziel · Wortschatz", each to be taken off, and the way to link it to a goal,
 * a to-do or a routine, those done or archived last. One deleted meanwhile shows no more.
 */
@Composable
fun LinksField(notes: Notes, links: Set<NoteLink>, onChange: (Set<NoteLink>) -> Unit) {
    val candidates by remember(notes) { notes.linkCandidates() }.collectAsState(initial = null)
    var adding by rememberSaveable { mutableStateOf(false) }
    val all = candidates ?: return
    Column {
        SectionTitle(stringResource(R.string.note_links))
        linked(all, links).forEach { candidate ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(linkName(candidate), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onChange(links - candidate.link) }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.note_link_remove, candidate.name))
                }
            }
        }
        TextButton(onClick = { adding = true }) { Text(stringResource(R.string.note_link_add)) }
    }

    if (adding) {
        LinkDialog(
            offered = all.filter { it.link !in links },
            onChoose = { link ->
                adding = false
                onChange(links + link)
            },
            onDismiss = { adding = false },
        )
    }
}

/** What the note is linked to, to read: "Verknüpft mit" and each, as [LinksField] has them; nothing if none. */
@Composable
fun LinksSummary(notes: Notes, links: Set<NoteLink>) {
    val candidates by remember(notes) { notes.linkCandidates() }.collectAsState(initial = null)
    val shown = linked(candidates ?: return, links)
    if (shown.isEmpty()) return
    Column {
        SectionTitle(stringResource(R.string.note_links))
        shown.forEach { Text(linkName(it), style = MaterialTheme.typography.bodyLarge) }
    }
}

/**
 * The notes linked to [link], by title, as the other side of the link shows them (SPEC.md §7): tapping one opens it
 * ([onOpen]), each link can be taken off, and another note linked from here, right away.
 */
@Composable
fun LinkedNotes(notes: Notes, link: NoteLink, onOpen: (Long) -> Unit) {
    val linked by remember(notes, link) { notes.notesLinkedTo(link) }.collectAsState(initial = emptyList())
    var adding by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column {
        SectionTitle(stringResource(R.string.linked_notes))
        linked.forEach { note ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = stringResource(R.string.linked_note_open), role = Role.Button) { onOpen(note.id) }
                        .padding(vertical = 12.dp),
                )
                IconButton(onClick = { scope.launchNow { notes.unlink(note.id, link) } }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.note_link_remove, note.title))
                }
            }
        }
        TextButton(onClick = { adding = true }) { Text(stringResource(R.string.linked_note_add)) }
    }

    if (adding) {
        val book by remember(notes) { notes.notebook() }.collectAsState(initial = null)
        val linkedIds = linked.map { it.id }.toSet()
        NoteChoiceDialog(
            offered = book?.all().orEmpty().filter { it.id !in linkedIds }.sortedWith(compareBy(InputText.byName) { it.title }),
            onChoose = { noteId ->
                adding = false
                scope.launchNow { notes.link(noteId, link) }
            },
            onDismiss = { adding = false },
        )
    }
}

/** Offers the notes [offered] to link, each by title. */
@Composable
private fun NoteChoiceDialog(offered: List<Note>, onChoose: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.linked_note_add)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (offered.isEmpty()) Text(stringResource(R.string.note_link_none), modifier = Modifier.padding(vertical = 12.dp))
                offered.forEach { note ->
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onChoose(note.id) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Offers a goal, a to-do or a routine to link to, one kind at a time, each by name. */
@Composable
private fun LinkDialog(offered: List<LinkCandidate>, onChoose: (NoteLink) -> Unit, onDismiss: () -> Unit) {
    var kind by rememberSaveable { mutableStateOf(LinkKind.Goal) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.note_link_title)) },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LinkKind.entries.forEachIndexed { index, choice ->
                        SegmentedButton(
                            selected = kind == choice,
                            onClick = { kind = choice },
                            shape = SegmentedButtonDefaults.itemShape(index, LinkKind.entries.size),
                            label = { Text(stringResource(choice.label)) },
                        )
                    }
                }
                // Those done or archived after the others, as the other side links them too.
                val ofKind = offered.filter { it.link.kind == kind }
                    .sortedWith(compareBy<LinkCandidate> { !it.open }.thenBy(InputText.byName) { it.name })
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                ) {
                    if (ofKind.isEmpty()) Text(stringResource(R.string.note_link_none), modifier = Modifier.padding(vertical = 12.dp))
                    ofKind.forEach { candidate ->
                        Text(
                            text = if (candidate.open) candidate.name else stringResource(R.string.note_link_closed, candidate.name),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onChoose(candidate.link) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Those of [all] that [links] has, goals first, then to-dos, then routines, each by name. */
private fun linked(all: List<LinkCandidate>, links: Set<NoteLink>): List<LinkCandidate> =
    all.filter { it.link in links }.sortedWith(compareBy<LinkCandidate> { it.link.kind }.thenBy(InputText.byName) { it.name })

/** "Ziel · Wortschatz", "To-do · Milch kaufen", "Routine · Vokabeln". */
@Composable
private fun linkName(candidate: LinkCandidate): String =
    stringResource(R.string.note_link_named, stringResource(candidate.link.kind.label), candidate.name)

/** "Ziel", "To-do", "Routine". */
private val LinkKind.label: Int
    @StringRes get() = when (this) {
        LinkKind.Goal -> R.string.note_link_goal
        LinkKind.Todo -> R.string.note_link_todo
        LinkKind.Routine -> R.string.note_link_routine
    }
