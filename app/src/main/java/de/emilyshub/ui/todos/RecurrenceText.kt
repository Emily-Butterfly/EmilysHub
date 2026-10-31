package de.emilyshub.ui.todos

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.todos.Recurrence
import de.emilyshub.todos.RecurrenceEnd
import de.emilyshub.ui.numericDayAndMonth

/**
 * How a to-do comes again, in a line: "Täglich", "Jede Woche Mo, Do", "Alle 14 Tage", "Monatlich am 1.", "Jährlich am
 * 31.5.", or "… ab Erledigung" for one counting from the day it is done; with how it ends, if it does: "… · noch 2
 * von 4" of the [seriesNumber]th of a series of 4 to-dos, "… · letztes Mal", "… · bis 19.12.".
 */
@Composable
fun recurrenceText(recurrence: Recurrence, end: RecurrenceEnd = RecurrenceEnd.Never, seriesNumber: Int = 1): String {
    val rule = ruleText(recurrence)
    val until = when (end) {
        RecurrenceEnd.Never -> null
        is RecurrenceEnd.AfterTimes -> {
            // Lowered below those come already, the one there is the last.
            val left = end.times - seriesNumber + 1
            if (left <= 1) stringResource(R.string.end_last_time) else stringResource(R.string.end_remaining, left, end.times)
        }
        is RecurrenceEnd.OnDate -> stringResource(R.string.end_until, end.date.numericDayAndMonth())
    }
    return until?.let { stringResource(R.string.end_joined, rule, it) } ?: rule
}

@Composable
private fun ruleText(recurrence: Recurrence): String = when (recurrence) {
    Recurrence.Daily -> stringResource(R.string.todo_recurrence_daily)
    is Recurrence.Weekdays -> {
        val names = stringArrayResource(R.array.weekdays_short)
        stringResource(R.string.todo_recurrence_weekdays, recurrence.days.sorted().joinToString(", ") { names[it.ordinal] })
    }
    is Recurrence.EveryNDays -> stringResource(
        if (recurrence.fromDone) R.string.todo_recurrence_every_n_days_from_done else R.string.todo_recurrence_every_n_days,
        recurrence.days,
    )
    is Recurrence.Monthly ->
        if (recurrence.fromDone) {
            stringResource(R.string.todo_recurrence_monthly_from_done)
        } else {
            stringResource(R.string.todo_recurrence_monthly, recurrence.day)
        }
    is Recurrence.Yearly ->
        if (recurrence.fromDone) {
            stringResource(R.string.todo_recurrence_yearly_from_done)
        } else {
            stringResource(R.string.todo_recurrence_yearly, recurrence.date.numericDayAndMonth())
        }
}
