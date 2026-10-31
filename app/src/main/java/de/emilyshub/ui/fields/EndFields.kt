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
import de.emilyshub.ui.Hint
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.numericDate
import de.emilyshub.ui.numericDayAndMonth
import java.time.LocalDate

/**
 * When a series ends (SPEC.md §5): never, on a last day, picked not before [earliest], and for to-dos, given which of
 * the series this is ([seriesNumber]), after a number of them in all. Without a [seriesNumber], as for routines, there
 * is no number to choose. [tooEarly] tells that the last day lies before [earliest], as when the due date moved on.
 */
@Composable
fun EndFields(
    end: RecurrenceEnd,
    earliest: LocalDate,
    tooEarly: Boolean,
    seriesNumber: Int? = null,
    onChange: (RecurrenceEnd) -> Unit,
) {
    // As typed, so that the field can be empty for a moment while the number is retyped.
    var timesText by rememberSaveable { mutableStateOf(((end as? RecurrenceEnd.AfterTimes)?.times ?: DEFAULT_TIMES).toString()) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.end_title))
        RadioOption(stringResource(R.string.end_never), selected = end == RecurrenceEnd.Never) { onChange(RecurrenceEnd.Never) }
        if (seriesNumber != null) {
            RadioOption(stringResource(R.string.end_after_times), selected = end is RecurrenceEnd.AfterTimes) {
                if (end !is RecurrenceEnd.AfterTimes) onChange(RecurrenceEnd.AfterTimes(timesText.toIntOrNull() ?: DEFAULT_TIMES))
            }
            if (end is RecurrenceEnd.AfterTimes) {
                NumberField(timesText, end.times, RecurrenceEnd.TIMES, stringResource(R.string.end_times)) { text, times ->
                    timesText = text
                    onChange(RecurrenceEnd.AfterTimes(times))
                }
                if (end.times in RecurrenceEnd.TIMES) endText(end, seriesNumber)?.let { Hint(it) }
            }
        }
        RadioOption(stringResource(R.string.end_on_date), selected = end is RecurrenceEnd.OnDate) {
            if (end !is RecurrenceEnd.OnDate) onChange(RecurrenceEnd.OnDate(earliest.plusMonths(1)))
        }
        if (end is RecurrenceEnd.OnDate) {
            DateValue(stringResource(R.string.end_last_day), end.date, onClick = { choosingDate = true })
            if (tooEarly) Problem(stringResource(R.string.end_date_too_early, earliest.numericDate()))
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

/**
 * How a series ends, of its [seriesNumber]th: "noch 2 von 4", "letztes Mal", or "bis 19.12."; nothing if it does not.
 * One whose number in all was lowered below those come already is at its last.
 */
@Composable
fun endText(end: RecurrenceEnd, seriesNumber: Int = 1): String? = when (end) {
    RecurrenceEnd.Never -> null
    is RecurrenceEnd.AfterTimes -> {
        val left = end.times - seriesNumber + 1
        if (left <= 1) stringResource(R.string.end_last_time) else stringResource(R.string.end_remaining, left, end.times)
    }
    is RecurrenceEnd.OnDate -> stringResource(R.string.end_until, end.date.numericDayAndMonth())
}

/** [text] with how its series ends, "Täglich · bis 15.11.", if it does. */
@Composable
fun withEnd(text: String, end: RecurrenceEnd, seriesNumber: Int = 1): String =
    endText(end, seriesNumber)?.let { stringResource(R.string.end_joined, text, it) } ?: text

private const val DEFAULT_TIMES = 4
