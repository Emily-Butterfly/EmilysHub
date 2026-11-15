package de.emilyshub.ui.languages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.languages.ExamStatus
import de.emilyshub.languages.Flashcards
import de.emilyshub.languages.LanguageCard
import de.emilyshub.languages.Languages
import de.emilyshub.languages.Periods
import de.emilyshub.languages.Workload
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.plusFabSpace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest

/**
 * The start page of Sprachen (SPEC.md §15), in Sonstiges: on top what all lessons come to this month, this year and in
 * all; below it a card for each language not archived, with its own, which opens its page ([onOpenLanguage]), and once
 * it has cards, how many are due today with "Üben" ([onPractice]), or that today's are done. "+" enters a lesson
 * ([onCreateLesson]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun LanguagesHomeScreen(
    languages: Languages,
    flashcards: Flashcards,
    day: Day,
    contentPadding: PaddingValues,
    onOpenLanguage: (Long) -> Unit,
    onPractice: (Long) -> Unit,
    onCreateLesson: () -> Unit,
) {
    val overview by remember(languages) { languages.overview() }.collectAsState(initial = null)
    val workloads by remember(flashcards, day) { day.dates().flatMapLatest(flashcards::workloads) }.collectAsState(initial = emptyMap())
    Box(
        Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val shown = overview
        when {
            shown == null -> Unit
            shown.cards.isEmpty() && shown.all.total.count == 0 ->
                PlaceholderScreen(stringResource(R.string.languages_empty), PaddingValues())
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp).plusFabSpace(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "all") { PeriodsCard(stringResource(R.string.languages_all), shown.all, nextExam = null, onClick = null) }
                items(shown.cards, key = { it.language.id }) { card: LanguageCard ->
                    PeriodsCard(
                        card.language.name, card.periods, card.nextExam, onClick = { onOpenLanguage(card.language.id) },
                        workload = workloads[card.language.id], onPractice = { onPractice(card.language.id) },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onCreateLesson,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lesson_create))
        }
    }
}

/**
 * A card of [title] with what [periods] come to and the [nextExam] if any; one that opens something with [onClick], else
 * only to read. With a [workload] of cards, how many are due today and "Üben" ([onPractice]), or that it is done.
 */
@Composable
private fun PeriodsCard(
    title: String,
    periods: Periods,
    nextExam: ExamStatus?,
    onClick: (() -> Unit)?,
    workload: Workload? = null,
    onPractice: () -> Unit = {},
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            PeriodLines(periods)
            nextExam?.let {
                Text(
                    "${stringResource(R.string.exam_next, it.exam.name)} · ${examStatusText(it)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            workload?.let { WorkloadLine(it, onPractice) }
        }
    }
    if (onClick == null) {
        Card(Modifier.fillMaxWidth()) { content() }
    } else {
        ElevatedCard(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) { content() }
    }
}

/** How many cards are due today, with "Üben", or that today's are done. */
@Composable
private fun WorkloadLine(workload: Workload, onPractice: () -> Unit) {
    if (workload.done) {
        Text(stringResource(R.string.cards_done_today), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.cards_due_today, workload.remaining.size),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onPractice) { Text(stringResource(R.string.cards_practice)) }
    }
}

/** The three dots of Sprachen in the top bar: where the languages and the teachers are managed (SPEC.md §15). */
@Composable
fun LanguagesMenu(onManageLanguages: () -> Unit, onManageTeachers: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.languages_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.languages_manage)) },
                onClick = {
                    expanded = false
                    onManageLanguages()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.teachers_manage)) },
                onClick = {
                    expanded = false
                    onManageTeachers()
                },
            )
        }
    }
}
