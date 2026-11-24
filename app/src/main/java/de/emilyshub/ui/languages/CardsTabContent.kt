package de.emilyshub.ui.languages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.languages.Answering
import de.emilyshub.languages.CardFile
import de.emilyshub.languages.VocabFile
import de.emilyshub.languages.CardRow
import de.emilyshub.languages.CardsTab
import de.emilyshub.languages.DailyLimits
import de.emilyshub.languages.Flashcards
import de.emilyshub.languages.Language
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.DirectedBy
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.plusFabSpace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The tab Karten of a language (SPEC.md §15): all its cards by front, each with when it is due next and whether it is
 * "sicher", shown for one topic only if one is chosen ([topicFilter], [onTopicFilterChange]), and only those a search
 * typed finds (#107), with how many it shows of how many (#144); tapped, a card opens in its editor ([onOpenCard]).
 * One that hakt is marked so, in the error colour, and while there are such, "Hakt" beside the topics shows only them
 * (#149).
 * Atop, how many are due today with "Üben" ([onPractice]), or that today's are done, as on the start page, while the
 * language is not archived. Its menu sets how many new cards a day takes, whether the practice asks for the answer
 * uncovered or typed (#153), once a card of the language has vowel marks whether the question leaves them out (#154),
 * and imports cards from a text file picked in Android's file dialog, after a preview.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun CardsTabContent(
    flashcards: Flashcards,
    day: Day,
    languageId: Long,
    topicFilter: Long?,
    onTopicFilterChange: (Long?) -> Unit,
    onOpenCard: (Long) -> Unit,
    onPractice: () -> Unit,
) {
    val tab by remember(flashcards, languageId) { flashcards.tab(languageId) }.collectAsState(initial = null)
    val today by remember(day) { day.dates() }.collectAsState(initial = day.today())
    val workload by remember(flashcards, day, languageId) { day.dates().flatMapLatest { flashcards.workloads.of(languageId, it) } }
        .collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    // Only the cards that hakt (#149), in the place of a topic.
    var stuckOnly by rememberSaveable { mutableStateOf(false) }
    var settingNew by rememberSaveable { mutableStateOf(false) }
    var settingAnswering by rememberSaveable { mutableStateOf(false) }
    var settingLimit by rememberSaveable { mutableStateOf(false) }
    val answering by remember(flashcards, languageId) { flashcards.answering(languageId) }.collectAsState(initial = Answering())
    // A file picked and read, to look at before its cards are made; not kept past the screen.
    var picked by remember { mutableStateOf<CardFile?>(null) }
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launchNow { picked = flashcards.readFile(uri.toString()) }
    }
    // A Vokabeldatei picked and read, to look at before it is taken over (#165); not kept past the screen.
    var pickedVocabs by remember { mutableStateOf<VocabFile?>(null) }
    val pickVocabs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launchNow { pickedVocabs = flashcards.readVocabFile(uri.toString()) }
    }
    val shown = tab ?: return

    // One deleted meanwhile filters no more; none hakt any more, neither does that filter.
    val anyStuck = shown.cards.any { it.stuck }
    val stuckFilter = stuckOnly && anyStuck
    val filter = shown.topics.find { it.id == topicFilter }.takeUnless { stuckFilter }
    val cards = when {
        stuckFilter -> shown.cards.filter { it.stuck }
        filter != null -> shown.cards.filter { it.card.topicId == filter.id }
        else -> shown.cards
    }.filter { it.matches(query) }
    val chooseTopic = { topicId: Long? ->
        stuckOnly = false
        onTopicFilterChange(topicId)
    }
    LazyColumn(contentPadding = PaddingValues().plusFabSpace()) {
        item(key = "bar") {
            Row(
                Modifier.padding(start = 8.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (shown.topics.isNotEmpty() || anyStuck) {
                        ChoiceChip(stringResource(R.string.cards_all_topics), chosen = filter == null && !stuckFilter) { chooseTopic(null) }
                        shown.topics.forEach { topic -> ChoiceChip(topic.name, chosen = filter?.id == topic.id) { chooseTopic(topic.id) } }
                    }
                    if (anyStuck) {
                        ChoiceChip(stringResource(R.string.cards_stuck_filter), chosen = stuckFilter) {
                            // In the place of the topic, which does not come back once none hakt any more.
                            onTopicFilterChange(null)
                            stuckOnly = true
                        }
                    }
                }
                CardsMenu(
                    shown,
                    typed = answering.typed,
                    // Offered once a card has them to leave out; also while left out, to be shown again.
                    hideVowelMarks = answering.hideVowelMarks.takeIf { it || shown.cards.any { row -> InputText.hasVowelMarks(row.card.front) } },
                    onHideVowelMarks = { hide -> scope.launchNow { flashcards.setHideVowelMarks(languageId, hide) } },
                    onSetNew = { settingNew = true },
                    onSetLimit = { settingLimit = true },
                    onSetAnswering = { settingAnswering = true },
                    onImport = { pick.launch(arrayOf("text/*")) },
                    // Known by what it holds, not its name: "vokabeln.json.txt" reads as one too.
                    onSyncVocabs = { pickVocabs.launch(arrayOf("*/*")) },
                )
            }
        }
        val practice = workload
        if (shown.cards.isNotEmpty() && practice != null) {
            item(key = "practice") {
                WorkloadLine(
                    practice,
                    onPractice,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        if (shown.cards.isNotEmpty()) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.cards_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        if (cards.isNotEmpty()) {
            item(key = "count") {
                val total = shown.cards.size
                Text(
                    if (cards.size == total) {
                        pluralStringResource(R.plurals.cards_count, total, total)
                    } else {
                        pluralStringResource(R.plurals.cards_count_of, total, cards.size, total)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        when {
            shown.cards.isEmpty() -> item(key = "empty") { PlaceholderScreen(stringResource(R.string.cards_empty), PaddingValues()) }
            cards.isEmpty() && query.isNotBlank() -> item(key = "none") {
                Text(
                    stringResource(R.string.cards_search_none, query.trim()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            cards.isEmpty() && filter != null -> item(key = "none") {
                Text(
                    stringResource(R.string.cards_filtered_empty, filter.name),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(cards, key = { it.card.id }) { row -> CardLine(row, today, onClick = { onOpenCard(row.card.id) }) }
    }

    when (val file = picked) {
        null -> Unit
        is CardFile.Read if file.cards.isNotEmpty() ->
            CardFilePreviewDialog(flashcards, languageId, file, shown.topics, onDismiss = { picked = null })
        else -> CardFileRefusedDialog(file, onDismiss = { picked = null })
    }
    when (val file = pickedVocabs) {
        null -> Unit
        is VocabFile.Read if file.vocabs.isNotEmpty() -> VocabFilePreviewDialog(flashcards, languageId, file, onDismiss = { pickedVocabs = null })
        else -> VocabFileRefusedDialog(file, onDismiss = { pickedVocabs = null })
    }

    if (settingNew) {
        NewPerDayDialog(
            current = shown.newPerDay,
            onConfirm = { count ->
                settingNew = false
                scope.launchNow { flashcards.setNewPerDay(languageId, count) }
            },
            onDismiss = { settingNew = false },
        )
    }
    if (settingLimit) {
        LimitDialog(
            title = R.string.cards_limit,
            text = R.string.cards_limit_text,
            current = shown.dailyLimit,
            range = DailyLimits.PER_LANGUAGE,
            onConfirm = { limit ->
                settingLimit = false
                scope.launchNow { flashcards.setDailyLimit(languageId, limit) }
            },
            onDismiss = { settingLimit = false },
        )
    }
    if (settingAnswering) {
        AnsweringDialog(
            typed = answering.typed,
            onChoose = { typed ->
                settingAnswering = false
                scope.launchNow { flashcards.setTypedAnswers(languageId, typed) }
            },
            onDismiss = { settingAnswering = false },
        )
    }
}

/**
 * A card on three lines, each the whole width (#144): its front, its back, and beneath them small when it is due next
 * ("fällig heute" in the primary colour), "neu" while never asked one of its ways, "sicher" if it is, its topic, and
 * "hakt" in the error colour if it does (#149).
 */
@Composable
private fun CardLine(row: CardRow, today: LocalDate, onClick: () -> Unit) {
    val card = row.card
    // Asked both ways, the sooner of its two (#141).
    val next = card.nextDue
    val dueToday = next != null && !next.isAfter(today)
    val due = when {
        dueToday -> stringResource(R.string.card_due_today)
        // New one way, it may come before the other is due.
        card.newAnyWay || next == null -> stringResource(R.string.card_new_mark)
        else -> stringResource(R.string.card_due_on, next.numericDate())
    }
    val rest = listOfNotNull(stringResource(R.string.card_sure).takeIf { card.sure }, row.topic?.name)
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error
    val stuck = stringResource(R.string.card_stuck_mark)
    val details = buildAnnotatedString {
        if (dueToday) withStyle(SpanStyle(color = primary)) { append(due) } else append(due)
        rest.forEach { append(" · $it") }
        if (row.stuck) {
            append(" · ")
            withStyle(SpanStyle(color = error)) { append(stuck) }
        }
    }
    ListItem(
        // Each side at the right if it is written right to left, as in the notes; the details in the app's direction.
        headlineContent = { DirectedLine(card.front, maxLines = 2) },
        supportingContent = {
            Column {
                DirectedLine(card.back, maxLines = 1)
                Text(details, style = MaterialTheme.typography.labelMedium)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

/** [text] on a line of its own, from the right if it is written right to left. */
@Composable
private fun DirectedLine(text: String, maxLines: Int) {
    DirectedBy(text) {
        Box(Modifier.fillMaxWidth()) { Text(text, maxLines = maxLines, overflow = TextOverflow.Ellipsis) }
    }
}

/**
 * The menu of the tab: how many new cards a day takes, how many ways a day's workload asks at most (#156), whether the practice asks for the answer [typed] (#153), whether
 * the question leaves out the vowel marks, ticked or not as [hideVowelMarks] says and not offered while it is none
 * (#154), cards from a file of the phone ([onImport]), and a Vokabeldatei to take over ([onSyncVocabs], #165).
 */
@Composable
private fun CardsMenu(
    tab: CardsTab,
    typed: Boolean,
    hideVowelMarks: Boolean?,
    onHideVowelMarks: (Boolean) -> Unit,
    onSetNew: () -> Unit,
    onSetLimit: () -> Unit,
    onSetAnswering: () -> Unit,
    onImport: () -> Unit,
    onSyncVocabs: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cards_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cards_new_per_day_value, tab.newPerDay)) },
                onClick = {
                    expanded = false
                    onSetNew()
                },
            )
            DropdownMenuItem(
                text = { Text(tab.dailyLimit?.let { stringResource(R.string.cards_limit_value, it) } ?: stringResource(R.string.cards_limit_none_value)) },
                onClick = {
                    expanded = false
                    onSetLimit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (typed) R.string.cards_answering_typed_value else R.string.cards_answering_reveal_value)) },
                onClick = {
                    expanded = false
                    onSetAnswering()
                },
            )
            hideVowelMarks?.let { hidden ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.cards_hide_vowel_marks)) },
                    trailingIcon = { Checkbox(checked = hidden, onCheckedChange = null) },
                    onClick = {
                        expanded = false
                        onHideVowelMarks(!hidden)
                    },
                    // TalkBack says whether it is ticked.
                    modifier = Modifier.semantics { toggleableState = ToggleableState(hidden) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cards_import)) },
                onClick = {
                    expanded = false
                    onImport()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.vocab_file_sync)) },
                onClick = {
                    expanded = false
                    onSyncVocabs()
                },
            )
        }
    }
}

/**
 * Asks for a limit of the day's workloads (#156), of [title] and [text]: "Ohne Grenze", or a number of [range] in steps
 * of [LIMIT_STEP], from [current]; [onConfirm] gives none for no limit.
 */
@Composable
internal fun LimitDialog(title: Int, text: Int, current: Int?, range: IntRange, onConfirm: (Int?) -> Unit, onDismiss: () -> Unit) {
    var limited by rememberSaveable { mutableStateOf(current != null) }
    var value by rememberSaveable { mutableFloatStateOf((current ?: range.first).toFloat()) }
    val chosen = (value.roundToInt() / LIMIT_STEP * LIMIT_STEP).coerceIn(range)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(text))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = !limited, role = Role.Checkbox) { limited = !it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = !limited, onCheckedChange = null)
                    Text(stringResource(R.string.cards_limit_none), modifier = Modifier.padding(start = 12.dp))
                }
                if (limited) {
                    Text(chosen.toString(), style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = (range.last - range.first) / LIMIT_STEP - 1,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(chosen.takeIf { limited }) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The steps a limit is chosen in. */
private const val LIMIT_STEP = 10

/** Asks whether the practice of the language uncovers the answer or asks for it [typed] (#153), in all its ways. */
@Composable
private fun AnsweringDialog(typed: Boolean, onChoose: (Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cards_answering)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(stringResource(R.string.cards_answering_text), modifier = Modifier.padding(bottom = 8.dp))
                RadioOption(stringResource(R.string.cards_answering_reveal), selected = !typed) { onChoose(false) }
                RadioOption(stringResource(R.string.cards_answering_typed), selected = typed) { onChoose(true) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Asks how many new cards a day takes, from [current], within [Language.NEW_CARDS]. */
@Composable
private fun NewPerDayDialog(current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(current.toFloat()) }
    val range = Language.NEW_CARDS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cards_new_per_day)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.cards_new_per_day_text))
                Text(stringResource(R.string.cards_new_per_day_value, value.roundToInt()), style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    steps = range.last - range.first - 1,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.roundToInt()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
