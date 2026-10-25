package de.emilyshub.ui.todos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.todos.Recurrence
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.numericDayAndMonth
import de.emilyshub.ui.routines.NumberField
import de.emilyshub.ui.routines.RepetitionOption
import de.emilyshub.ui.routines.WeekdayChips
import java.time.LocalDate
import java.time.MonthDay

/**
 * Whether and how the to-do comes again once ticked off (SPEC.md §5): not at all, daily, on certain weekdays, every X
 * days, monthly or yearly; the last two on the day of the month or of the year it is [due] when chosen. Those with an
 * interval come next by plan, or counting from the day the to-do is ticked off.
 */
@Composable
fun RecurrenceFields(recurrence: Recurrence?, due: LocalDate, onChange: (Recurrence?) -> Unit) {
    // As typed, so that the field can be empty for a moment while the number is retyped.
    var intervalText by rememberSaveable {
        mutableStateOf(((recurrence as? Recurrence.EveryNDays)?.days ?: DEFAULT_INTERVAL).toString())
    }
    // Kept on switching between those with an interval.
    val fromDone = recurrence?.fromDone ?: false

    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.routine_repeat))
        RepetitionOption(stringResource(R.string.todo_repeat_none), selected = recurrence == null) { onChange(null) }
        RepetitionOption(stringResource(R.string.todo_recurrence_daily), selected = recurrence == Recurrence.Daily) {
            onChange(Recurrence.Daily)
        }
        RepetitionOption(stringResource(R.string.routine_repeat_weekdays), selected = recurrence is Recurrence.Weekdays) {
            if (recurrence !is Recurrence.Weekdays) onChange(Recurrence.Weekdays(setOf(due.dayOfWeek)))
        }
        if (recurrence is Recurrence.Weekdays) {
            WeekdayChips(recurrence.days, onChange = { onChange(Recurrence.Weekdays(it)) })
        }
        RepetitionOption(stringResource(R.string.routine_repeat_every_n_days), selected = recurrence is Recurrence.EveryNDays) {
            if (recurrence !is Recurrence.EveryNDays) onChange(Recurrence.EveryNDays(intervalText.toIntOrNull() ?: 0, fromDone))
        }
        if (recurrence is Recurrence.EveryNDays) {
            val label = stringResource(R.string.routine_interval)
            NumberField(intervalText, recurrence.days, RoutineDraft.INTERVAL_DAYS, label) { text, days ->
                intervalText = text
                onChange(recurrence.copy(days = days))
            }
        }
        val monthly = recurrence as? Recurrence.Monthly ?: Recurrence.Monthly(due.dayOfMonth, fromDone)
        RepetitionOption(stringResource(R.string.todo_recurrence_monthly, monthly.day), selected = recurrence is Recurrence.Monthly) {
            onChange(monthly)
        }
        if (recurrence is Recurrence.Monthly && !fromDone && recurrence.day > SHORTEST_MONTH) {
            Note(stringResource(R.string.todo_repeat_month_end))
        }
        val yearly = recurrence as? Recurrence.Yearly ?: Recurrence.Yearly(MonthDay.from(due), fromDone)
        val yearlyLabel = stringResource(R.string.todo_recurrence_yearly, yearly.date.numericDayAndMonth())
        RepetitionOption(yearlyLabel, selected = recurrence is Recurrence.Yearly) { onChange(yearly) }
        if (recurrence is Recurrence.Yearly && !fromDone && recurrence.date == LEAP_DAY) {
            Note(stringResource(R.string.todo_repeat_leap_day))
        }
        if (recurrence is Recurrence.EveryNDays || recurrence is Recurrence.Monthly || recurrence is Recurrence.Yearly) {
            FromDoneField(fromDone, onChange = { onChange(recurrence.countingFromDone(it)) })
        }
    }
}

/** Whether the next one comes counting from the day the to-do is ticked off, rather than by plan. */
@Composable
private fun FromDoneField(fromDone: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = fromDone, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.todo_repeat_from_done), style = MaterialTheme.typography.bodyLarge)
            Note(stringResource(R.string.todo_repeat_from_done_summary))
        }
        Switch(checked = fromDone, onCheckedChange = null)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val DEFAULT_INTERVAL = 7
private const val SHORTEST_MONTH = 28
private val LEAP_DAY: MonthDay = MonthDay.of(2, 29)
