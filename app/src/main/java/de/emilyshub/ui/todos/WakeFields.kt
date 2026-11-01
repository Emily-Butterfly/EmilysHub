package de.emilyshub.ui.todos

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import de.emilyshub.R
import de.emilyshub.todos.Due
import de.emilyshub.todos.WakeTime
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.LabelledValue
import de.emilyshub.ui.fields.NumberField
import de.emilyshub.ui.fields.Problem
import de.emilyshub.ui.fields.RadioOption
import de.emilyshub.ui.fields.TimeDialog
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.numericDayAndMonth
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When a to-do wakes besides when it is due (SPEC.md §5): not at all, so many days before it is due at a time, only with
 * a [due] day, or at a fixed moment, which starts out at [start]. Saying when, as on [today], and if that is [past]
 * already, as it then does not wake after all.
 */
@Composable
fun WakeFields(wake: WakeTime?, due: Due, today: LocalDate, start: LocalDateTime, past: Boolean, onChange: (WakeTime?) -> Unit) {
    // As typed, so that the field can be empty for a moment while the number is retyped.
    var daysText by rememberSaveable { mutableStateOf(((wake as? WakeTime.Before)?.days ?: DEFAULT_DAYS).toString()) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    var choosingTime by rememberSaveable { mutableStateOf(false) }
    val time = wake?.time ?: DEFAULT_TIME

    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.todo_wake_title))
        RadioOption(stringResource(R.string.todo_wake_none), selected = wake == null) { onChange(null) }
        if (due.date != null) {
            RadioOption(stringResource(R.string.todo_wake_before), selected = wake is WakeTime.Before) {
                if (wake !is WakeTime.Before) onChange(WakeTime.Before(daysText.toIntOrNull() ?: DEFAULT_DAYS, time))
            }
        }
        RadioOption(stringResource(R.string.todo_wake_at), selected = wake is WakeTime.At) {
            if (wake !is WakeTime.At) onChange(WakeTime.At(start.toLocalDate(), wake?.time ?: start.toLocalTime()))
        }
        when (wake) {
            null -> Unit
            is WakeTime.Before -> {
                val label = stringResource(R.string.todo_wake_days)
                NumberField(daysText, wake.days, WakeTime.DAYS_BEFORE, label) { text, days ->
                    daysText = text
                    onChange(wake.copy(days = days))
                }
            }
            is WakeTime.At -> DateValue(stringResource(R.string.todo_due_date), wake.date, onClick = { choosingDate = true })
        }
        if (wake != null) {
            LabelledValue(
                label = stringResource(R.string.todo_due_time),
                value = wake.time.clockTime(),
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(R.string.field_time_change),
                    role = Role.Button,
                    onClick = { choosingTime = true },
                ),
            )
            wake.localMoment(due)?.let { Text(stringResource(R.string.todo_wake_label, LocalResources.current.wakeText(it, today))) }
            Text(
                stringResource(R.string.todo_wake_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (past) Problem(stringResource(R.string.todo_wake_past))
        }
    }

    if (choosingDate && wake is WakeTime.At) {
        DateDialog(
            initial = wake.date,
            onConfirm = { picked ->
                choosingDate = false
                onChange(wake.copy(date = picked))
            },
            onDismiss = { choosingDate = false },
        )
    }
    if (choosingTime && wake != null) {
        TimeDialog(
            initial = wake.time,
            onConfirm = { picked ->
                choosingTime = false
                onChange(
                    when (wake) {
                        is WakeTime.Before -> wake.copy(time = picked)
                        is WakeTime.At -> wake.copy(time = picked)
                    },
                )
            },
            onDismiss = { choosingTime = false },
        )
    }
}

/**
 * When a to-do wakes, as its list and editor say it, on [today]: "heute, 18:00", "morgen, 07:30", "Fr, 13.11., 18:00", and
 * with the year if it is not this one.
 */
fun Resources.wakeText(moment: LocalDateTime, today: LocalDate): String {
    val date = moment.toLocalDate()
    val day = when (date) {
        today -> getString(R.string.todo_day_today)
        today.plusDays(1) -> getString(R.string.todo_day_tomorrow)
        today.minusDays(1) -> getString(R.string.todo_day_yesterday)
        else -> {
            val weekday = getStringArray(R.array.weekdays_short)[date.dayOfWeek.ordinal]
            getString(R.string.todo_weekday_date, weekday, if (date.year == today.year) date.numericDayAndMonth() else date.numericDate())
        }
    }
    return getString(R.string.todo_day_time, day, moment.clockTime())
}

private const val DEFAULT_DAYS = 1

/** The evening before, as for a to-do due the next morning. */
private val DEFAULT_TIME: LocalTime = LocalTime.of(18, 0)
