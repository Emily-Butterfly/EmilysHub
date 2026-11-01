package de.emilyshub.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Routines
import de.emilyshub.stats.Heatmap
import de.emilyshub.stats.HeatmapDay
import de.emilyshub.todos.Todos
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.numericDayAndMonth
import de.emilyshub.ui.theme.heatmapColors
import de.emilyshub.ui.weekdayDayAndMonth
import java.time.LocalDate

/**
 * Under the entries of Heute, what was done each day of the last six months up to [today] (SPEC.md §1): the
 * occurrences of routines done, archived ones' too, and the to-dos ticked off, those in the archive too; one deleted for
 * good no more. A box a day, in four levels of lilac, the weeks as columns from Monday to Sunday, the current one on the
 * right; as wide as there is room, so that it never scrolls sideways. Tapping a day says when it was and how much.
 */
@Composable
fun TodayHeatmap(today: LocalDate, routines: Routines, todos: Todos, modifier: Modifier = Modifier) {
    val history by remember(routines) { routines.history() }.collectAsState(initial = null)
    val archived by remember(todos) { todos.archived() }.collectAsState(initial = null)
    val heatmap = remember(history, archived, today) {
        val occurrences = history ?: return@remember null
        val ticked = archived ?: return@remember null
        Heatmap.of(
            today = today,
            routinesDone = occurrences.filter { it.status == OccurrenceStatus.Done }.map { it.date },
            todosDone = ticked.mapNotNull { it.doneAt },
            zone = todos.zone(),
        )
    } ?: return
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    Column(modifier.padding(16.dp)) {
        SectionTitle(stringResource(R.string.today_heatmap))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = (maxWidth - WEEKDAY_WIDTH) / Heatmap.WEEKS
            Column {
                MonthNames(heatmap, cell)
                Row {
                    WeekdayNames(cell)
                    heatmap.weeks.forEach { week ->
                        Column {
                            week.forEach { day ->
                                DayBox(day, cell, selected = day.date == selected, onSelect = { selected = day.date })
                            }
                        }
                    }
                }
            }
        }
        heatmap.weeks.flatten().find { it.date == selected }?.let { day ->
            val weekday = stringArrayResource(R.array.weekdays_short)[day.date.dayOfWeek.value - 1]
            Text(
                text = stringResource(R.string.today_heatmap_day, weekday, day.date.numericDayAndMonth(), day.done),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** The short names of the months over the weeks their first days fall in. */
@Composable
private fun MonthNames(heatmap: Heatmap, cell: Dp) {
    val names = stringArrayResource(R.array.months_short)
    Box(
        Modifier
            .padding(start = WEEKDAY_WIDTH)
            .fillMaxWidth()
            .height(MONTH_HEIGHT)
            .clearAndSetSemantics {},
    ) {
        heatmap.months.forEach { (week, month) ->
            Text(
                text = names[month.monthValue - 1],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = cell * week),
            )
        }
    }
}

/** Monday, Wednesday and Friday named at the side, the rows between left blank. */
@Composable
private fun WeekdayNames(cell: Dp) {
    val names = stringArrayResource(R.array.weekdays_short)
    Column(
        Modifier
            .width(WEEKDAY_WIDTH)
            .clearAndSetSemantics {},
    ) {
        names.forEachIndexed { index, name ->
            Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                if (index % 2 == 0 && index < NAMED_WEEKDAYS) {
                    Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A day's box, in the color of its level and read out with its date and count; a day to come is left blank. */
@Composable
private fun DayBox(day: HeatmapDay, cell: Dp, selected: Boolean, onSelect: () -> Unit) {
    if (day.future) {
        Box(Modifier.size(cell))
        return
    }
    val description = stringResource(R.string.today_heatmap_day_spoken, day.date.weekdayDayAndMonth(), day.done)
    val shape = RoundedCornerShape(2.dp)
    Box(
        Modifier
            .size(cell)
            .clickable(role = Role.Button, onClick = onSelect)
            .semantics { contentDescription = description }
            .padding(1.dp)
            .background(heatmapColors()[day.level], shape)
            .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier),
    )
}

/** The room at the side for the weekdays' names. */
private val WEEKDAY_WIDTH = 24.dp

/** The room on top for the months' names. */
private val MONTH_HEIGHT = 16.dp

/** Mo, Mi and Fr are named, Sa and So not: the rows are few enough to tell apart from these. */
private const val NAMED_WEEKDAYS = 5
