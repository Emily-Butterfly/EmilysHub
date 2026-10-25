package de.emilyshub.ui.stats

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.stats.DayOutcome
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A month of a routine's [days] as a calendar (SPEC.md §8), starting with the month of [today]: each day it was due on
 * marked by how it ended, done, skipped or missed, in color and with a sign, so that it does not rest on color alone,
 * and read out as such; other days plain. The arrows go to the month before and after. Below, what the colors mean.
 */
@Composable
fun HistoryCalendar(days: Map<LocalDate, DayOutcome>, today: LocalDate) {
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.statistics_month_previous))
            }
            Text(
                text = MONTH.format(month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(onClick = { month = month.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.statistics_month_next))
            }
        }
        Row {
            stringArrayResource(R.array.weekdays_short).forEach { weekday ->
                Text(
                    text = weekday,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Weeks start on Monday; the days before the first of the month are left blank.
        val blanks = month.atDay(1).dayOfWeek.value - 1
        val cells: List<LocalDate?> = List(blanks) { null } + (1..month.lengthOfMonth()).map(month::atDay)
        cells.chunked(DAYS_A_WEEK).forEach { week ->
            Row {
                (week + List(DAYS_A_WEEK - week.size) { null }).forEach { date ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                    ) {
                        if (date != null) DayCell(date, days[date])
                    }
                }
            }
        }
        Legend()
    }
}

@Composable
private fun DayCell(date: LocalDate, outcome: DayOutcome?) {
    val marking = outcome?.marking()
    val description = marking?.let { stringResource(R.string.statistics_day, DAY.format(date), stringResource(it.label)) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(marking?.color ?: Color.Transparent, CircleShape)
            .then(if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = marking?.onColor ?: MaterialTheme.colorScheme.onSurface,
            )
            marking?.let { Text(it.sign, style = MaterialTheme.typography.labelSmall, color = it.onColor) }
        }
    }
}

/** What the colors and signs of the calendar mean. */
@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
        listOf(DayOutcome.Done, DayOutcome.Skipped, DayOutcome.Missed).forEach { outcome ->
            val marking = outcome.marking()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .background(marking.color, CircleShape),
                )
                Text(
                    text = "${marking.sign} ${stringResource(marking.label)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** How a day is marked: its color, the color on it, its sign, and how it is named. */
private class Marking(val color: Color, val onColor: Color, val sign: String, @param:StringRes val label: Int)

@Composable
private fun DayOutcome.marking(): Marking {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        DayOutcome.Done -> Marking(colors.primary, colors.onPrimary, "✓", R.string.statistics_done)
        DayOutcome.Skipped -> Marking(colors.secondaryContainer, colors.onSecondaryContainer, "–", R.string.statistics_skipped)
        DayOutcome.Missed -> Marking(colors.error, colors.onError, "✗", R.string.statistics_missed)
        DayOutcome.Open -> Marking(colors.surfaceVariant, colors.onSurfaceVariant, "·", R.string.statistics_open_day)
    }
}

private val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)
private const val DAYS_A_WEEK = 7
