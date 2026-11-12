package de.emilyshub.ui.languages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.languages.LanguagePage
import de.emilyshub.languages.Languages
import de.emilyshub.languages.LessonRow
import de.emilyshub.ledger.Money
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.plusFabSpace
import de.emilyshub.wishes.Wishes
import kotlinx.coroutines.flow.map

/**
 * A language's page (SPEC.md §15), with three tabs: "Sessions", its lessons, the newest first, which open in their editor
 * ([onOpenLesson]) and can be shown for one teacher only; "Themen", its topics by exam, to do by hand
 * ([TopicsTabContent]); and "Auswertung", what each teacher's lessons of it come to for
 * the month, the year and in all, with the cost where prices were given. "+" enters a lesson of it ([onCreateLesson]).
 * Gone meanwhile, it closes. It opens on "Themen" if [showTopics], as an exam at a goal does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePageScreen(
    languages: Languages,
    wishes: Wishes,
    languageId: Long,
    showTopics: Boolean,
    onOpenLesson: (Long) -> Unit,
    onCreateLesson: () -> Unit,
    onBack: () -> Unit,
) {
    val page by remember(languages, languageId) { languages.page(languageId).map<LanguagePage?, PageState?> { it?.let(::Shown) } }
        .collectAsState(initial = Loading)
    var tab by rememberSaveable { mutableIntStateOf(if (showTopics) TOPICS_TAB else 0) }
    var teacherFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    val shown = page
    if (shown == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        (shown as? Shown)?.page?.language?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateLesson) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lesson_create))
            }
        },
    ) { contentPadding ->
        val loaded = (shown as? Shown)?.page ?: return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(R.string.language_tab_lessons, R.string.language_tab_topics, R.string.language_tab_evaluation).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            when (tab) {
                0 -> LessonsTab(loaded, teacherFilter, onFilterChange = { teacherFilter = it }, onOpenLesson)
                TOPICS_TAB -> TopicsTabContent(languages, wishes, languageId, loaded.topics, onOpenLesson)
                else -> EvaluationTab(loaded)
            }
        }
    }
}

/** The lessons, the newest first, those of [teacherFilter] only if one is chosen among those who held them. */
@Composable
private fun LessonsTab(page: LanguagePage, teacherFilter: Long?, onFilterChange: (Long?) -> Unit, onOpenLesson: (Long) -> Unit) {
    if (page.lessons.isEmpty()) {
        PlaceholderScreen(stringResource(R.string.language_lessons_empty), PaddingValues())
        return
    }
    // One no longer among them filters no more.
    val filter = page.byTeacher.find { it.teacher.id == teacherFilter }?.teacher
    val lessons = if (filter == null) page.lessons else page.lessons.filter { it.teacher.id == filter.id }
    LazyColumn(contentPadding = PaddingValues().plusFabSpace()) {
        if (page.byTeacher.size > 1) {
            item(key = "filter") {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChoiceChip(stringResource(R.string.language_all_teachers), chosen = filter == null) { onFilterChange(null) }
                    page.byTeacher.forEach { (teacher) ->
                        ChoiceChip(teacher.name, chosen = filter?.id == teacher.id) { onFilterChange(teacher.id) }
                    }
                }
            }
        }
        if (lessons.isEmpty() && filter != null) {
            item(key = "none") {
                Text(
                    stringResource(R.string.language_lessons_filtered_empty, filter.name),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(lessons, key = { it.lesson.id }) { row -> LessonLine(row, onClick = { onOpenLesson(row.lesson.id) }) }
    }
}

/** A lesson: its date and how long, with whom, and what it cost if known. */
@Composable
private fun LessonLine(row: LessonRow, onClick: () -> Unit) {
    val lesson = row.lesson
    ListItem(
        overlineContent = { Text(stringResource(R.string.language_lesson_line, lesson.date.numericDate(), durationText(lesson.minutes.toLong()))) },
        headlineContent = { Text(row.teacher.name) },
        supportingContent = lesson.priceCents?.let { cents -> { Text(Money.format(cents)) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider()
}

/** What each teacher's lessons of it come to, for the month, the year and in all. */
@Composable
private fun EvaluationTab(page: LanguagePage) {
    if (page.byTeacher.isEmpty()) {
        PlaceholderScreen(stringResource(R.string.language_lessons_empty), PaddingValues())
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(page.byTeacher, key = { it.teacher.id }) { tally ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tally.teacher.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    PeriodLines(tally.periods)
                }
            }
        }
    }
}

/** Where "Themen" is among the tabs. */
private const val TOPICS_TAB = 1

/** The page as it is read: not yet, or as it is; none once its language is gone. */
private sealed interface PageState

private data object Loading : PageState

private data class Shown(val page: LanguagePage) : PageState
