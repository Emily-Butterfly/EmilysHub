package de.emilyshub.ui.languages

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.languages.CardDraft
import de.emilyshub.languages.CardFile
import de.emilyshub.languages.CardImport
import de.emilyshub.languages.CardsMade
import de.emilyshub.languages.Flashcards
import de.emilyshub.languages.Topic
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.DirectedBy
import de.emilyshub.ui.launchNow

/**
 * "Karten aus der Notiz" (SPEC.md §15, Anlegen; #105): the lines of a lesson's [note] that read as cards of the
 * language [languageId], each to tick, those its language has not yet ticked; "Seiten tauschen" turns all of them round.
 * With one of the lesson's [topics] the cards get it; with several, one or none is chosen. Making them says how many
 * were made and how many skipped as there already. The note stays as it is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardsFromNoteDialog(flashcards: Flashcards, languageId: Long, note: String, topics: List<Topic>, onDismiss: () -> Unit) {
    var swapped by rememberSaveable { mutableStateOf(false) }
    val drafts = remember(note, swapped) { CardImport.fromNote(note).let { read -> if (swapped) read.map(CardDraft::swapped) else read } }
    val taken by produceState<List<Boolean>?>(null, flashcards, languageId, drafts) { value = flashcards.taken(languageId, drafts) }
    var ticked by remember(drafts, taken) { mutableStateOf(taken?.let { t -> drafts.indices.filter { !t[it] }.toSet() }.orEmpty()) }
    var topicId by rememberSaveable(topics) { mutableStateOf(topics.singleOrNull()?.id) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (drafts.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.cards_from_note)) },
            text = { Text(stringResource(R.string.cards_from_note_none)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cards_from_note)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { swapped = !swapped }) { Text(stringResource(R.string.cards_swap_sides)) }
                drafts.forEachIndexed { index, draft ->
                    val chosen = index in ticked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { ticked = if (chosen) ticked - index else ticked + index },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = chosen, onCheckedChange = { ticked = if (it) ticked + index else ticked - index })
                        Column(Modifier.weight(1f)) {
                            SideLine(draft.front, MaterialTheme.typography.bodyLarge)
                            SideLine(draft.back, MaterialTheme.typography.bodyMedium)
                            if (taken?.getOrNull(index) == true) {
                                Text(stringResource(R.string.cards_taken_mark), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (topics.size > 1) TopicChoice(topics, topicId, onChoose = { topicId = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = ticked.isNotEmpty(),
                onClick = {
                    val chosen = drafts.filterIndexed { index, _ -> index in ticked }
                    scope.launchNow {
                        tellMade(context, flashcards.createAll(languageId, chosen, topicId))
                        onDismiss()
                    }
                },
            ) { Text(pluralStringResource(R.plurals.cards_create_n, ticked.size, ticked.size)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * The preview of a file of cards [read] (SPEC.md §15, Anlegen; #105): how many cards it holds, how many of them the
 * language has already ([taken]), which lines do not read, the first cards to look at, and a topic of [topics] for all.
 * Making them makes all at once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardFilePreviewDialog(
    flashcards: Flashcards,
    languageId: Long,
    read: CardFile.Read,
    topics: List<Topic>,
    onDismiss: () -> Unit,
) {
    val drafts = remember(read) { read.cards.map { it.draft } }
    val taken by produceState<List<Boolean>?>(null, flashcards, languageId, drafts) { value = flashcards.taken(languageId, drafts) }
    var topicId by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val takenCount = taken?.count { it } ?: 0
    val toMake = drafts.size - takenCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cards_import)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(summary(drafts.size, takenCount, read.unreadable))
                drafts.take(PREVIEWED).forEach { draft ->
                    Column(Modifier.padding(vertical = 2.dp)) {
                        SideLine(draft.front, MaterialTheme.typography.bodyLarge)
                        SideLine(draft.back, MaterialTheme.typography.bodyMedium)
                    }
                }
                if (drafts.size > PREVIEWED) Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (topics.isNotEmpty()) TopicChoice(topics, topicId, onChoose = { topicId = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = taken != null && toMake > 0,
                onClick = {
                    scope.launchNow {
                        tellMade(context, flashcards.createAll(languageId, drafts, topicId))
                        onDismiss()
                    }
                },
            ) { Text(pluralStringResource(R.plurals.cards_create_n, toMake, toMake)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Why a file of cards is not read; [CardFile.Read] without any card included. */
@Composable
fun CardFileRefusedDialog(why: CardFile, onDismiss: () -> Unit) {
    val text = when (why) {
        CardFile.TooLarge -> R.string.cards_import_too_large
        CardFile.TooManyLines -> R.string.cards_import_too_many_lines
        CardFile.NotText -> R.string.cards_import_not_text
        is CardFile.Read -> R.string.cards_import_none
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cards_import)) },
        text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

/** "312 Karten, 5 schon vorhanden, 2 Zeilen nicht lesbar (Zeile 17, 40)": what of a file is there to make. */
@Composable
private fun summary(cards: Int, taken: Int, unreadable: List<Int>): String {
    val parts = mutableListOf(pluralStringResource(R.plurals.cards_import_found, cards, cards))
    if (taken > 0) parts += pluralStringResource(R.plurals.cards_import_taken, taken, taken)
    when (unreadable.size) {
        0 -> Unit
        1 -> parts += stringResource(R.string.cards_import_unreadable_one, unreadable.single().toString())
        else -> parts += stringResource(R.string.cards_import_unreadable, unreadable.size, unreadable.joinToString(", "))
    }
    return parts.joinToString(", ")
}

/** One side of a card, from the right if it is written right to left. */
@Composable
private fun SideLine(text: String, style: TextStyle) {
    DirectedBy(text) {
        Text(text, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    }
}

/** A topic of [topics] for all the cards, or none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicChoice(topics: List<Topic>, chosen: Long?, onChoose: (Long?) -> Unit) {
    Text(stringResource(R.string.cards_topic_for_all), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip(stringResource(R.string.card_topic_none), chosen = chosen == null) { onChoose(null) }
        topics.forEach { topic -> ChoiceChip(topic.name, chosen = chosen == topic.id) { onChoose(topic.id) } }
    }
}

/** Says how many cards were [made], and how many skipped as there already. */
private fun tellMade(context: Context, made: CardsMade) {
    val text = context.resources.getQuantityString(R.plurals.cards_made, made.created, made.created) +
        if (made.skipped > 0) context.resources.getQuantityString(R.plurals.cards_made_skipped, made.skipped, made.skipped) else ""
    Toast.makeText(context, "$text.", Toast.LENGTH_LONG).show()
}

/** How many cards of a file the preview shows. */
private const val PREVIEWED = 5
