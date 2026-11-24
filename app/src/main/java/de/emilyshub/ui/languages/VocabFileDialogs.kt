package de.emilyshub.ui.languages

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.languages.Flashcards
import de.emilyshub.languages.Reconciliation
import de.emilyshub.languages.VocabField
import de.emilyshub.languages.VocabFile
import de.emilyshub.languages.VocabsTaken
import de.emilyshub.ui.DirectedBy
import de.emilyshub.ui.launchNow

/**
 * The preview of a Vokabeldatei [read] (SPEC.md §15, Abgleich; #165): "8 neu · 2 geändert · 50 unverändert", what does
 * not read and what is not taken over, the new cards to untick and the cards changed, field by field, each folded away;
 * "In beide Richtungen abfragen" for the new ones, ticked. Taking it over does all at once; with nothing new and nothing
 * changed, it says so and offers nothing to take.
 */
@Composable
fun VocabFilePreviewDialog(flashcards: Flashcards, languageId: Long, read: VocabFile.Read, onDismiss: () -> Unit) {
    val plan by produceState<Reconciliation?>(null, flashcards, languageId, read) { value = flashcards.reconciliation(languageId, read.vocabs) }
    var leftOut by rememberSaveable { mutableStateOf(emptySet<Int>()) }
    var bothWays by rememberSaveable { mutableStateOf(true) }
    var showAdded by rememberSaveable { mutableStateOf(false) }
    var showChanged by rememberSaveable { mutableStateOf(false) }
    var taking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shown = plan
    val adding = shown?.added?.count { it.entry !in leftOut } ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vocab_file_sync)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (shown == null) return@Column
                if (shown.upToDate) {
                    Text(stringResource(R.string.vocab_file_up_to_date), style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(
                        stringResource(R.string.vocab_file_summary, shown.added.size, shown.changed.size, shown.unchanged),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Hints(read, shown)
                if (shown.added.isNotEmpty()) {
                    Fold(pluralStringResource(R.plurals.vocab_file_added, shown.added.size, shown.added.size), showAdded) { showAdded = !showAdded }
                    if (showAdded) {
                        shown.added.forEach { vocab ->
                            val chosen = vocab.entry !in leftOut
                            val turn = { leftOut = if (chosen) leftOut + vocab.entry else leftOut - vocab.entry }
                            Row(Modifier.fillMaxWidth().clickable(onClick = turn), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = chosen, onCheckedChange = { turn() })
                                Column(Modifier.weight(1f)) {
                                    Side(vocab.front, MaterialTheme.typography.bodyLarge)
                                    Side(vocab.back, MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    BothWaysField(bothWays) { bothWays = it }
                }
                if (shown.changed.isNotEmpty()) {
                    Fold(pluralStringResource(R.plurals.vocab_file_changed, shown.changed.size, shown.changed.size), showChanged) { showChanged = !showChanged }
                    if (showChanged) {
                        shown.changed.forEach { change ->
                            Column(Modifier.padding(vertical = 4.dp)) {
                                Side(change.card.front, MaterialTheme.typography.bodyLarge)
                                if (change.handMade) Muted(stringResource(R.string.vocab_file_hand_made))
                                if (change.fields.isEmpty()) Muted(stringResource(R.string.vocab_file_key_only))
                                change.fields.forEach { field ->
                                    Muted(stringResource(labelOf(field.field)))
                                    Side("${field.old.ifEmpty { "–" }} → ${field.new}", MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (shown != null && !shown.upToDate) {
                TextButton(
                    enabled = !taking && (adding > 0 || shown.changed.isNotEmpty()),
                    onClick = {
                        taking = true
                        scope.launchNow {
                            val taken = flashcards.takeOver(languageId, read.vocabs, leftOut, bothWays)
                            tellTaken(context, taken)
                            onDismiss()
                        }
                    },
                ) { Text(stringResource(R.string.vocab_file_take_over)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
            }
        },
        dismissButton = {
            if (shown != null && !shown.upToDate) TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Says how many cards were made and how many changed. */
private fun tellTaken(context: Context, taken: VocabsTaken) {
    Toast.makeText(context, context.getString(R.string.vocab_file_taken, taken.created, taken.changed), Toast.LENGTH_LONG).show()
}

/** Why a Vokabeldatei picked, [file], is not taken over: it was refused, or none of its entries reads as a vocable. */
@Composable
fun VocabFileRefusedDialog(file: VocabFile, onDismiss: () -> Unit) {
    val text = when (file) {
        VocabFile.TooLarge -> stringResource(R.string.vocab_file_too_large)
        VocabFile.TooMany -> stringResource(R.string.vocab_file_too_many)
        VocabFile.NotVocabFile -> stringResource(R.string.vocab_file_not_vocab_file)
        is VocabFile.Read -> stringResource(R.string.vocab_file_none)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vocab_file_sync)) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/** What of [read] is not taken over: entries that do not read, keys that stand twice, fronts taken, and the topics to be made. */
@Composable
private fun Hints(read: VocabFile.Read, plan: Reconciliation) {
    if (read.unreadable.isNotEmpty()) {
        Muted(pluralStringResource(R.plurals.vocab_file_unreadable, read.unreadable.size, read.unreadable.size, read.unreadable.joinToString(", ")))
    }
    if (read.duplicate.isNotEmpty()) {
        Muted(pluralStringResource(R.plurals.vocab_file_duplicate, read.duplicate.size, read.duplicate.size, read.duplicate.joinToString(", ")))
    }
    if (plan.refused.isNotEmpty()) {
        Muted(pluralStringResource(R.plurals.vocab_file_refused, plan.refused.size, plan.refused.size, plan.refused.joinToString(", ") { it.front }))
    }
    if (plan.newTopics.isNotEmpty()) {
        Muted(pluralStringResource(R.plurals.vocab_file_new_topics, plan.newTopics.size, plan.newTopics.size, plan.newTopics.joinToString(", ")))
    }
}

/** A list folded away or not ([open]), named [title]; a tap turns it. */
@Composable
private fun Fold(title: String, open: Boolean, onTurn: () -> Unit) {
    Text(
        (if (open) "▾ " else "▸ ") + title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTurn)
            .padding(vertical = 8.dp),
    )
}

/** One side of a card, from the right if it is written right to left. */
@Composable
private fun Side(text: String, style: TextStyle) {
    DirectedBy(text) { Text(text, style = style, modifier = Modifier.fillMaxWidth()) }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun labelOf(field: VocabField): Int = when (field) {
    VocabField.Front -> R.string.vocab_file_field_front
    VocabField.Back -> R.string.vocab_file_field_back
    VocabField.Note -> R.string.vocab_file_field_note
    VocabField.Topic -> R.string.vocab_file_field_topic
}
