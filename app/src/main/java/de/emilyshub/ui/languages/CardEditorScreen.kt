package de.emilyshub.ui.languages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.data.Saving
import de.emilyshub.languages.Card
import de.emilyshub.languages.CardDraft
import de.emilyshub.languages.CardProblem
import de.emilyshub.languages.Flashcards
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.DirectedBy
import de.emilyshub.ui.differsAsSaved
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.fields.SaveBar
import de.emilyshub.ui.fields.rememberFirstField
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.rememberClosing
import de.emilyshub.ui.rememberLeaving
import kotlinx.coroutines.flow.map
import java.io.Serializable

/** What the editor of cards opens: a new one of the language [New.languageId], or one saved already. */
sealed interface CardEditorTarget : Serializable {
    data class New(val languageId: Long) : CardEditorTarget

    data class Edit(val cardId: Long) : CardEditorTarget
}

/**
 * A card of the Vokabeltrainer (SPEC.md §15), new or saved: its front and back, needed, its transcription and note, and
 * one of its language's topics if wished. A front another card of the language has already is refused ("Diese Karte
 * gibt es schon"). Each field is laid out right to left while what is typed starts so, as in the notes. A saved one is
 * deleted here, after asking, with all it learnt.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardEditorScreen(flashcards: Flashcards, target: CardEditorTarget, onClose: () -> Unit) {
    val cardId = (target as? CardEditorTarget.Edit)?.cardId
    var languageId by rememberSaveable { mutableStateOf((target as? CardEditorTarget.New)?.languageId) }
    var original by rememberSaveable { mutableStateOf<CardDraft?>(null) }
    var draft by rememberSaveable { mutableStateOf(CardDraft()) }
    // The form as a new one starts out, to tell whether anything was typed.
    val fresh = rememberSaveable { draft }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    // Found only on saving: a front taken, a topic gone.
    var refused by rememberSaveable { mutableStateOf(emptySet<CardProblem>()) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    // Whether the card was asked from its back already: ticking that off then forgets what was planned so, once asked (#141).
    var reverseAsked by rememberSaveable { mutableStateOf(false) }
    var untickingReverse by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val language = languageId
    if (language == null || (cardId != null && original == null)) {
        LaunchedEffect(cardId) {
            val saved = flashcards.card(checkNotNull(cardId))
            if (saved == null) {
                onClose()
            } else {
                draft = CardDraft.of(saved)
                original = draft
                reverseAsked = saved.reverseLastReview != null
                languageId = saved.languageId
            }
        }
        return
    }
    val topics by remember(flashcards, language) { flashcards.tab(language).map { it?.topics.orEmpty() } }.collectAsState(initial = emptyList())
    val problems = draft.problems + refused
    val closeWith = rememberClosing(onClose)
    val firstField = rememberFirstField(new = cardId == null)
    val save = {
        if (draft.problems.isNotEmpty()) {
            showProblems = true
            firstField.saveFailed(atField = draft.problems.any { it == CardProblem.FrontMissing || it == CardProblem.FrontTooLong })
        } else {
            val saved = draft
            closeWith.leaveThrough {
                val saving = if (cardId == null) flashcards.create(language, saved) else flashcards.save(cardId, saved)
                when (saving) {
                    is Saving.Saved -> onClose()
                    is Saving.Refused -> {
                        refused = saving.problems
                        showProblems = true
                        if (CardProblem.TopicNotOffered in saving.problems) draft = draft.copy(topicId = null)
                        closeWith.reopen()
                    }
                }
            }
        }
    }
    val leave = rememberLeaving(
        closing = closeWith,
        changed = { differsAsSaved(draft, original ?: fresh, CardDraft::cleaned) },
        onSave = save,
        onDiscard = onClose,
    )
    val change = { changed: CardDraft ->
        draft = changed
        refused = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (cardId == null) R.string.card_new else R.string.card_edit), modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (cardId != null) {
                        IconButton(onClick = { deleting = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.card_delete))
                        }
                    }
                },
            )
        },
        bottomBar = { SaveBar(onSave = save) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val clean = draft.cleaned()
            val frontProblem = when {
                showProblems && CardProblem.FrontMissing in problems -> stringResource(R.string.card_front_missing)
                CardProblem.FrontTaken in problems -> stringResource(R.string.card_front_taken)
                else -> null
            }
            DirectedBy(draft.front) {
                OutlinedTextField(
                    value = draft.front,
                    onValueChange = { change(draft.copy(front = it)) },
                    label = { Text(stringResource(R.string.card_front)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    isError = frontProblem != null || CardProblem.FrontTooLong in problems,
                    supportingText = { if (frontProblem != null) Text(frontProblem) else LengthHint(InputText.lengthOf(clean.front), Card.FRONT_MAX) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstField.requester),
                )
            }
            val backMissing = showProblems && CardProblem.BackMissing in problems
            DirectedBy(draft.back) {
                OutlinedTextField(
                    value = draft.back,
                    onValueChange = { change(draft.copy(back = it)) },
                    label = { Text(stringResource(R.string.card_back)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = backMissing || CardProblem.BackTooLong in problems,
                    supportingText = {
                        if (backMissing) Text(stringResource(R.string.card_back_missing)) else LengthHint(InputText.lengthOf(clean.back), Card.BACK_MAX)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DirectedBy(draft.transcription) {
                OutlinedTextField(
                    value = draft.transcription,
                    onValueChange = { change(draft.copy(transcription = it)) },
                    label = { Text(stringResource(R.string.card_transcription)) },
                    singleLine = true,
                    isError = CardProblem.TranscriptionTooLong in problems,
                    supportingText = { LengthHint(InputText.lengthOf(clean.transcription), Card.TRANSCRIPTION_MAX) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DirectedBy(draft.note) {
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { change(draft.copy(note = it)) },
                    label = { Text(stringResource(R.string.card_note)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = CardProblem.NoteTooLong in problems,
                    supportingText = { LengthHint(InputText.lengthOf(clean.note), Card.NOTE_MAX) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (topics.isNotEmpty() || draft.topicId != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.card_topic), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(stringResource(R.string.card_topic_none), chosen = draft.topicId == null) { change(draft.copy(topicId = null)) }
                        topics.forEach { topic ->
                            ChoiceChip(topic.name, chosen = draft.topicId == topic.id) { change(draft.copy(topicId = topic.id)) }
                        }
                    }
                    if (CardProblem.TopicNotOffered in problems) Problem(stringResource(R.string.card_topic_gone))
                }
            }
            BothWaysField(draft.bothWays) { ticked ->
                if (!ticked && reverseAsked && original?.bothWays == true) untickingReverse = true else change(draft.copy(bothWays = ticked))
            }
        }
    }

    if (untickingReverse) {
        AlertDialog(
            onDismissRequest = { untickingReverse = false },
            title = { Text(stringResource(R.string.card_both_ways_off_title)) },
            text = { Text(stringResource(R.string.card_both_ways_off_text)) },
            confirmButton = {
                TextButton(onClick = {
                    untickingReverse = false
                    change(draft.copy(bothWays = false))
                }) { Text(stringResource(R.string.card_both_ways_off)) }
            },
            dismissButton = { TextButton(onClick = { untickingReverse = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (deleting && cardId != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.card_delete_title)) },
            text = { Text(stringResource(R.string.card_delete_text, (original ?: draft).cleaned().front)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launchNow {
                        flashcards.delete(cardId)
                        onClose()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
