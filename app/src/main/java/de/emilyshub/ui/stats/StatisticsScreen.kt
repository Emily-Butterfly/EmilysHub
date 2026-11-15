package de.emilyshub.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.routines.Routines
import de.emilyshub.stats.DoneRate
import de.emilyshub.stats.LastStreak
import de.emilyshub.stats.OverallHistory
import de.emilyshub.stats.RoutineHistory
import de.emilyshub.stats.StreakRule
import de.emilyshub.stats.Streaks
import de.emilyshub.todos.Todos
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.dateLine
import de.emilyshub.ui.rememberToday
import java.time.LocalDate

/**
 * The statistics (SPEC.md §8): the overall streak by [streakRule] (#109), routines and to-dos together, with its
 * calendar, and the done rates over all routines; then each routine with its streak and its done rate of the last 30
 * days, the archived ones too, as what they did stays. Tapping one opens its own ([onOpenRoutine]).
 */
@Composable
fun StatisticsScreen(
    routines: Routines,
    todos: Todos,
    day: Day,
    streakRule: StreakRule,
    onOpenRoutine: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val history by remember(routines) { routines.history() }.collectAsState(initial = null)
    val ticked by remember(todos) { todos.ticked() }.collectAsState(initial = null)
    val allRoutines by remember(routines) { routines.all() }.collectAsState(initial = null)
    StatisticsScaffold(stringResource(R.string.statistics_title), onBack) { contentPadding ->
        val all = history ?: return@StatisticsScaffold
        val todosDone = ticked ?: return@StatisticsScaffold
        val everyRoutine = allRoutines ?: return@StatisticsScaffold
        // Ticked-off to-dos alone have an overall streak too (#109).
        if (everyRoutine.isEmpty() && todosDone.isEmpty()) {
            PlaceholderScreen(stringResource(R.string.statistics_empty), contentPadding)
            return@StatisticsScaffold
        }
        val today = rememberToday(day)
        val overall = remember(all) { RoutineHistory(all) }
        // A to-do counts on the day it was ticked off, as in the heatmap.
        val overallStreak = remember(all, todosDone, streakRule, today) {
            OverallHistory(all, todosDone.map(day::dayOf), streakRule, today)
        }
        val byRoutine = remember(all) { all.groupBy { it.routineId }.mapValues { (_, outcomes) -> RoutineHistory(outcomes) } }
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle(stringResource(R.string.statistics_all))
                Summary(overallStreak.streaks(), overallStreak.lastStreak(), overall, today)
                SectionTitle(stringResource(R.string.statistics_calendar))
                HistoryCalendar(overallStreak.days, today)
            }
            if (everyRoutine.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp)) { SectionTitle(stringResource(R.string.statistics_each)) }
            }
            everyRoutine.forEach { routine ->
                val routineHistory = byRoutine[routine.id] ?: RoutineHistory(emptyList())
                ListItem(
                    headlineContent = { Text(routine.name) },
                    supportingContent = {
                        Column {
                            Text(
                                stringResource(
                                    R.string.statistics_routine_summary,
                                    days(routineHistory.streaks().current),
                                    SUMMARY_DAYS,
                                    percent(routineHistory.doneRate(today, period = SUMMARY_DAYS)),
                                ),
                            )
                            if (routine.status == RoutineStatus.Archived) Text(stringResource(R.string.statistics_archived))
                        }
                    },
                    modifier = Modifier.clickable { onOpenRoutine(routine.id) },
                )
            }
        }
    }
}

/** The statistics of one routine (SPEC.md §8): its streaks, its done rates, and a calendar of how its due days ended. */
@Composable
fun RoutineStatisticsScreen(routines: Routines, day: Day, routineId: Long, onBack: () -> Unit) {
    val history by remember(routines, routineId) { routines.history(routineId) }.collectAsState(initial = null)
    val allRoutines by remember(routines) { routines.all() }.collectAsState(initial = null)
    val routine = allRoutines?.find { it.id == routineId }
    StatisticsScaffold(routine?.name.orEmpty(), onBack) { contentPadding ->
        val occurrences = history ?: return@StatisticsScaffold
        if (routine == null) return@StatisticsScaffold
        val today = rememberToday(day)
        val routineHistory = remember(occurrences) { RoutineHistory(occurrences) }
        Column(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Summary(routineHistory.streaks(), routineHistory.lastStreak(), routineHistory, today)
            SectionTitle(stringResource(R.string.statistics_calendar))
            HistoryCalendar(routineHistory.days, today)
        }
    }
}

/**
 * The current and longest [streaks], without a current one the [last] one, if there was one (#99), and the done rates
 * of the last 7, 30 and 90 days of [history].
 */
@Composable
private fun Summary(streaks: Streaks, last: LastStreak?, history: RoutineHistory, today: LocalDate) {
    Text(stringResource(R.string.statistics_streak_current, days(streaks.current)))
    last?.let { last ->
        Text(stringResource(R.string.statistics_streak_last, days(last.days), last.until.dateLine()))
    }
    Text(stringResource(R.string.statistics_streak_longest, days(streaks.longest)))
    SectionTitle(stringResource(R.string.statistics_rate_title))
    RoutineHistory.PERIODS.forEach { period ->
        Text(stringResource(R.string.statistics_rate, period, percent(history.doneRate(today, period))))
    }
}

/** "1 Tag", "3 Tage". */
@Composable
private fun days(count: Int): String = pluralStringResource(R.plurals.statistics_days, count, count)

/** "75 %", or a dash while nothing counts. */
@Composable
private fun percent(rate: DoneRate): String =
    rate.percent?.let { stringResource(R.string.statistics_percent, it) } ?: stringResource(R.string.statistics_no_rate)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        content = content,
    )
}

/** The days of the done rate each routine shows in the list. */
private const val SUMMARY_DAYS = 30
