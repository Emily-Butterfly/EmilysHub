package de.emilyshub.ui.languages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.languages.CardRow
import de.emilyshub.languages.CardsTab
import de.emilyshub.languages.Flashcards
import de.emilyshub.languages.Language
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.plusFabSpace
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The tab Karten of a language (SPEC.md §15): all its cards by front, each with when it is due next and whether it is
 * "sicher", shown for one topic only if one is chosen; tapped, a card opens in its editor ([onOpenCard]). Its menu sets
 * how many new cards a day takes.
 */
@Composable
internal fun CardsTabContent(flashcards: Flashcards, day: Day, languageId: Long, onOpenCard: (Long) -> Unit) {
    val tab by remember(flashcards, languageId) { flashcards.tab(languageId) }.collectAsState(initial = null)
    val today by remember(day) { day.dates() }.collectAsState(initial = day.today())
    var topicFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var settingNew by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val shown = tab ?: return

    // One deleted meanwhile filters no more.
    val filter = shown.topics.find { it.id == topicFilter }
    val cards = if (filter == null) shown.cards else shown.cards.filter { it.card.topicId == filter.id }
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
                    if (shown.topics.isNotEmpty()) {
                        ChoiceChip(stringResource(R.string.cards_all_topics), chosen = filter == null) { topicFilter = null }
                        shown.topics.forEach { topic -> ChoiceChip(topic.name, chosen = filter?.id == topic.id) { topicFilter = topic.id } }
                    }
                }
                CardsMenu(shown, onSetNew = { settingNew = true })
            }
        }
        when {
            shown.cards.isEmpty() -> item(key = "empty") { PlaceholderScreen(stringResource(R.string.cards_empty), PaddingValues()) }
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
}

/** A card: its front, its back, and when it is due next, "neu" while never asked, and "sicher" if it is. */
@Composable
private fun CardLine(row: CardRow, today: LocalDate, onClick: () -> Unit) {
    val card = row.card
    val due = when {
        card.due == null -> stringResource(R.string.card_new_mark)
        !card.due.isAfter(today) -> stringResource(R.string.card_due_today)
        else -> stringResource(R.string.card_due_on, card.due.numericDate())
    }
    val details = listOfNotNull(due, stringResource(R.string.card_sure).takeIf { card.sure }, row.topic?.name).joinToString(" · ")
    ListItem(
        headlineContent = { Text(card.front, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(card.back, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = { Text(details, style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

/** The menu of the tab: how many new cards a day takes. */
@Composable
private fun CardsMenu(tab: CardsTab, onSetNew: () -> Unit) {
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
        }
    }
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
