package de.emilyshub.ui.fields

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.todos.RecurrenceEnd
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.numericDate
import java.time.LocalDate

/**
 * When a series ends (SPEC.md §5): never, after a number of times in all if [withTimes], as for to-dos, or on a last
 * day, not before [earliest]. A day before it, as left by moving the due date on, is told as a problem.
 */
@Composable
fun EndFields(end: RecurrenceEnd, earliest: LocalDate, withTimes: Boolean, onChange: (RecurrenceEnd) -> Unit) {
    // As typed, so that the field can be empty for a moment while the number is retyped.
    var timesText by rememberSaveable { mutableStateOf(((end as? RecurrenceEnd.AfterTimes)?.times ?: DEFAULT_TIMES).toString()) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.end_title))
        RadioOption(stringResource(R.string.end_never), selected = end == RecurrenceEnd.Never) { onChange(RecurrenceEnd.Never) }
        if (withTimes) {
            RadioOption(stringResource(R.string.end_after_times), selected = end is RecurrenceEnd.AfterTimes) {
                if (end !is RecurrenceEnd.AfterTimes) onChange(RecurrenceEnd.AfterTimes(timesText.toIntOrNull() ?: 0))
            }
            if (end is RecurrenceEnd.AfterTimes) {
                NumberField(timesText, end.times, RecurrenceEnd.TIMES, stringResource(R.string.end_times)) { text, times ->
                    timesText = text
                    onChange(RecurrenceEnd.AfterTimes(times))
                }
            }
        }
        RadioOption(stringResource(R.string.end_on_date), selected = end is RecurrenceEnd.OnDate) {
            if (end !is RecurrenceEnd.OnDate) onChange(RecurrenceEnd.OnDate(earliest.plusMonths(1)))
        }
        if (end is RecurrenceEnd.OnDate) {
            DateValue(stringResource(R.string.end_last_day), end.date, onClick = { choosingDate = true })
            if (end.date.isBefore(earliest)) Problem(stringResource(R.string.end_date_too_early, earliest.numericDate()))
        }
    }

    if (choosingDate && end is RecurrenceEnd.OnDate) {
        DateDialog(
            initial = maxOf(end.date, earliest),
            earliest = earliest,
            onConfirm = {
                onChange(RecurrenceEnd.OnDate(it))
                choosingDate = false
            },
            onDismiss = { choosingDate = false },
        )
    }
}

private const val DEFAULT_TIMES = 4
