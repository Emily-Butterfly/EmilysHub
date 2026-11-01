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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.stats.Heatmap
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.numericDayAndMonth
import de.emilyshub.ui.theme.heatmapColors
import de.emilyshub.ui.weekdayDayAndMonth
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Under the entries of Heute, what was done each day of the last six months ([heatmap], SPEC.md §1): the occurrences
 * of routines done, archived ones' too, and the to-dos ticked off, those in the archive too; one deleted for good no
 * more. A box a day, in four levels of lilac, the weeks as columns from Monday to Sunday, the current one on the right;
 * as wide as there is room, so that it never scrolls sideways. Tapping a day says when and how much.
 */
@Composable
fun TodayHeatmap(heatmap: Heatmap, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    // Worked out once for all the boxes, not in each of them as one is tapped.
    val colors = heatmapColors()
    val spoken = stringResource(R.string.today_heatmap_day_spoken)
    val descriptions = remember(heatmap, spoken) {
        heatmap.weeks.flatten().associate { it.date to spoken.format(it.date.weekdayDayAndMonth(), it.done) }
    }
    val select = remember { { date: LocalDate -> selected = date } }
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
                                if (day.future) {
                                    Box(Modifier.size(cell))
                                } else {
                                    DayBox(
                                        date = day.date,
                                        color = colors[day.level],
                                        description = descriptions.getValue(day.date),
                                        cell = cell,
                                        selected = day.date == selected,
                                        onSelect = select,
                                    )
                                }
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
                // TalkBack says it as it appears on a tap.
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * The short names of the months over the weeks their first days fall in; not for the last weeks, where the name
 * would run past the right edge.
 */
@Composable
private fun MonthNames(heatmap: Heatmap, cell: Dp) {
    val names = stringArrayResource(R.array.months_short)
    Box(
        Modifier
            .padding(start = WEEKDAY_WIDTH)
            .fillMaxWidth()
            .clearAndSetSemantics {},
    ) {
        heatmap.months.filter { it.week <= Heatmap.WEEKS - MONTH_NAME_WEEKS }.forEach { (week, month) ->
            Text(
                text = names[month.monthValue - 1],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(x = cell * week),
            )
        }
    }
}

/** Monday, Wednesday and Friday named at the side, beside their rows, even where the name is taller than a row. */
@Composable
private fun WeekdayNames(cell: Dp) {
    val names = stringArrayResource(R.array.weekdays_short)
    Column(
        Modifier
            .width(WEEKDAY_WIDTH)
            .clearAndSetSemantics {},
    ) {
        DayOfWeek.entries.forEach { weekday ->
            Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                if (weekday in NAMED_WEEKDAYS) {
                    Text(
                        text = names[weekday.value - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.wrapContentHeight(unbounded = true),
                    )
                }
            }
        }
    }
}

/**
 * A day's box, in the [color] of its level, read out with its date and count, and as chosen once tapped; a button, not
 * a selectable item, as it only shows what it holds below.
 */
@Composable
private fun DayBox(date: LocalDate, color: Color, description: String, cell: Dp, selected: Boolean, onSelect: (LocalDate) -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    val selectedState = stringResource(R.string.today_heatmap_selected)
    Box(
        Modifier
            .size(cell)
            .clickable(role = Role.Button, onClick = { onSelect(date) })
            .semantics {
                contentDescription = description
                if (selected) stateDescription = selectedState
            }
            .padding(1.dp)
            .background(color, shape)
            .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier),
    )
}

/** The room at the side for the weekdays' names. */
private val WEEKDAY_WIDTH = 24.dp

/** The weeks a month's name takes at the most, kept clear of the right edge. */
private const val MONTH_NAME_WEEKS = 2

/** The weekdays named at the side: every other row, enough to tell them apart. */
private val NAMED_WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
