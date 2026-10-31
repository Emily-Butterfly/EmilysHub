package de.emilyshub.ui.todos

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.todos.Due
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.numericDayAndMonth
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * When a to-do under "Demnächst" is due (SPEC.md §1), some days after [today]: "morgen", "morgen, 09:30", "Sa, 14.11. ·
 * in 3 Tagen", "Sa, 14.11., 09:30 · in 3 Tagen"; nothing for one due whenever.
 */
@Composable
fun upcomingText(due: Due, today: LocalDate): String? {
    val date = due.date ?: return null
    val days = ChronoUnit.DAYS.between(today, date)
    val day = if (days == 1L) {
        stringResource(R.string.todo_day_tomorrow)
    } else {
        val weekday = stringArrayResource(R.array.weekdays_short)[date.dayOfWeek.ordinal]
        stringResource(R.string.todo_weekday_date, weekday, date.numericDayAndMonth())
    }
    val withTime = (due as? Due.At)?.let { stringResource(R.string.todo_day_time, day, it.time.clockTime()) } ?: day
    return if (days == 1L) withTime else stringResource(R.string.todo_day_ahead, withTime, days)
}

/** When a to-do is due, as the lists say it: "heute", "morgen, 18:00", "30.10.2026"; nothing for one due whenever. */
@Composable
fun dueText(due: Due, today: LocalDate): String? = LocalResources.current.dueText(due, today)

/** When a to-do is due, as the lists and its reminder say it; nothing for one due whenever. */
fun Resources.dueText(due: Due, today: LocalDate): String? = when (due) {
    Due.None -> null
    is Due.On -> dayText(due.date, today)
    is Due.At -> getString(R.string.todo_day_time, dayText(due.date, today), due.time.clockTime())
}

/** "heute", "morgen" and "gestern"; other days by their date. */
private fun Resources.dayText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> getString(R.string.todo_day_today)
    today.plusDays(1) -> getString(R.string.todo_day_tomorrow)
    today.minusDays(1) -> getString(R.string.todo_day_yesterday)
    else -> date.numericDate()
}
