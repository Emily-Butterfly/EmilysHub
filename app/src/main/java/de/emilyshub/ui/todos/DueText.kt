package de.emilyshub.ui.todos

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import de.emilyshub.R
import de.emilyshub.todos.Due
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.numericDate
import java.time.LocalDate

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
