package de.emilyshub.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.notes.Notes
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.launchNow

/**
 * The notes pinned on Heute (SPEC.md §7), the one changed last first, each with its first line: tapping one opens it,
 * and each can be taken off Heute. Nothing while none is.
 */
@Composable
fun PinnedNotes(notes: Notes, onOpen: (Long) -> Unit) {
    val pinned by remember(notes) { notes.onToday() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    if (pinned.isEmpty()) return
    Column {
        Column(Modifier.padding(horizontal = 16.dp)) { SectionTitle(stringResource(R.string.today_notes)) }
        pinned.forEach { note ->
            val firstLine = note.text.lineSequence().map(String::trim).firstOrNull(String::isNotEmpty)
            ListItem(
                leadingContent = { Text(PIN, style = MaterialTheme.typography.titleMedium) },
                headlineContent = { Text(note.title) },
                supportingContent = firstLine?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
                trailingContent = {
                    IconButton(onClick = { scope.launchNow { notes.putOnToday(note.id, onToday = false) } }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.today_note_unpin, note.title))
                    }
                },
                modifier = Modifier.clickable { onOpen(note.id) },
            )
        }
    }
}
