package de.emilyshub.ui.fields

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.ui.numericDate
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** How much of [max] is used up, or that it is exceeded. */
@Composable
internal fun LengthHint(length: Int, max: Int) {
    Text(
        if (length > max) stringResource(R.string.text_too_long, max) else stringResource(R.string.text_length, length, max),
    )
}

@Composable
internal fun LabelledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun DateValue(label: String, date: LocalDate, onClick: () -> Unit) {
    LabelledValue(
        label = label,
        value = date.numericDate(),
        modifier = Modifier.clickable(
            onClickLabel = stringResource(R.string.field_date_change),
            role = Role.Button,
            onClick = onClick,
        ),
    )
}

/** Typing the time is quicker than a clock dial: four digits, always on the 24-hour clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.field_time_title)) },
        text = { TimeInput(state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** A calendar to pick a day from, none before [earliest] and none after [latest]. The picker counts days in UTC milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(
    initial: LocalDate,
    earliest: LocalDate? = null,
    latest: LocalDate? = null,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = dateOf(utcTimeMillis)
                return (earliest == null || !date.isBefore(earliest)) && (latest == null || !date.isAfter(latest))
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(dateOf(it)) } },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        DatePicker(state)
    }
}

private fun LocalDate.utcMillis() = toEpochDay() * DAY_MILLIS

private fun dateOf(utcMillis: Long) = LocalDate.ofEpochDay(Math.floorDiv(utcMillis, DAY_MILLIS))

private val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)
