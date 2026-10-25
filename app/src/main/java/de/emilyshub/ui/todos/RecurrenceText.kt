package de.emilyshub.ui.todos

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.todos.Recurrence
import de.emilyshub.ui.numericDayAndMonth

/**
 * How a to-do comes again, in a line: "Täglich", "Jede Woche Mo, Do", "Alle 14 Tage", "Monatlich am 1.", "Jährlich am
 * 31.5.", or "… ab Erledigung" for one counting from the day it is done.
 */
@Composable
fun recurrenceText(recurrence: Recurrence): String = when (recurrence) {
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
