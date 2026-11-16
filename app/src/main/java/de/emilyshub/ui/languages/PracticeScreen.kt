package de.emilyshub.ui.languages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.languages.Prompt
import de.emilyshub.languages.Flashcards
import de.emilyshub.languages.Rating
import de.emilyshub.languages.Round
import de.emilyshub.languages.Workload
import de.emilyshub.ui.launchNow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Practising the cards of the language [languageId] (SPEC.md §15): its workload of today, one card at a time, "7 von 23"
 * above it, its front large; "Aufdecken" shows the back, the transcription and the note, then the four answers, each
 * with the interval it would give. Each answer is kept at once; "Nochmal" brings the card again at the end of the round.
 * Left any time, "Üben" goes on where it was. At the end, "Für heute geschafft". Archived or gone meanwhile, it closes.
 * With a [topicId], it asks all the cards of that topic instead, each once, those due first (#107).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun PracticeScreen(flashcards: Flashcards, day: Day, languageId: Long, onBack: () -> Unit, topicId: Long? = null) {
    val state by remember(flashcards, day, languageId, topicId) {
        day.dates().flatMapLatest { today ->
            if (topicId == null) {
                flashcards.workload(languageId, today).map { it?.let(::Practising) }
            } else {
                flashcards.topicPractice(topicId, today).map { it?.let(::PractisingTopic) }
            }
        }.map<PracticeState?, PracticeState> { it ?: Gone }
    }.collectAsState(initial = Reading)
    var round by rememberSaveable { mutableStateOf(Round()) }
    val scope = rememberCoroutineScope()
    // The card being answered, shown until the round has taken the answer in: the workload may come first.
    var answering by remember { mutableStateOf<Prompt?>(null) }

    val shown = state
    if (shown == Gone) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val name by remember(flashcards, languageId) { flashcards.languageName(languageId) }.collectAsState(initial = "")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        val workload = when (shown) {
            is Practising -> shown.workload
            // Each card of the topic once, and again after "Nochmal".
            is PractisingTopic -> Workload.ofTopic(shown.cards, round, day.today())
            else -> return@Scaffold
        }
        val card = answering ?: round.current(workload)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (card == null) {
                Spacer(Modifier.padding(top = 48.dp))
                val done = if (topicId == null) R.plurals.practice_done else R.plurals.practice_topic_done
                Text(
                    pluralStringResource(done, workload.doneToday.size, workload.doneToday.size),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onBack) { Text(stringResource(R.string.practice_back)) }
                return@Column
            }
            val (position, total) = round.position(workload)
            Text(stringResource(R.string.practice_position, position, total), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CardFace(
                prompt = card,
                intervals = flashcards.intervals(card),
                hint = workload.firstTime,
                enabled = answering == null,
                onAnswer = { rating ->
                    answering = card
                    scope.launchNow {
                        // Practising a topic, its new cards do not count against those of the day (#107).
                        flashcards.answer(card.id, rating, countsAsNew = topicId == null, reverse = card.reverse)
                        round = round.answered(card, rating)
                        answering = null
                    }
                },
            )
        }
    }
}

/**
 * The card shown, the way [prompt] asks it: what is asked large, its back, or reversed its front, with "Rückrichtung"
 * above it (#141); once revealed, the other side, the transcription and the note, and the four answers, each with its
 * interval of [intervals] under it, and the [hint] under them on the first practice.
 */
@Composable
internal fun CardFace(prompt: Prompt, intervals: Map<Rating, Int>, hint: Boolean, enabled: Boolean, onAnswer: (Rating) -> Unit) {
    val card = prompt.card
    // Each card starts covered, each way.
    var revealed by rememberSaveable(card.id, prompt.reverse, prompt.lastReview) { mutableStateOf(false) }
    if (prompt.reverse) {
        Text(stringResource(R.string.practice_reverse), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Text(prompt.question, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (!revealed) {
        Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.practice_reveal)) }
        return
    }
    HorizontalDivider()
    Text(prompt.answer, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (card.transcription.isNotEmpty()) {
        Text(card.transcription, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    if (card.note.isNotEmpty()) {
        Text(card.note, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ANSWERS.forEach { (rating, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                // Four in a row leave little room on a narrow phone: less padding, and the label shrinks rather than being cut off.
                FilledTonalButton(
                    onClick = { onAnswer(rating) },
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(label), maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize))
                }
                Text(stringResource(R.string.practice_days, intervals.getValue(rating)), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (hint) {
        Text(stringResource(R.string.practice_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The four answers, in their order, with their labels. */
private val ANSWERS = listOf(
    Rating.Again to R.string.practice_again,
    Rating.Hard to R.string.practice_hard,
    Rating.Good to R.string.practice_good,
    Rating.Easy to R.string.practice_easy,
)

/** The practice as it is read: not yet, the workload of the day, or nothing once the language is archived or gone. */
private sealed interface PracticeState

private data object Reading : PracticeState

private data object Gone : PracticeState

private data class Practising(val workload: Workload) : PracticeState

/** A topic practised (#107): all its [cards], the ways they are asked, in their order. */
private data class PractisingTopic(val cards: List<Prompt>) : PracticeState
