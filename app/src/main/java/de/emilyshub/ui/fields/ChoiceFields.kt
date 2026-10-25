package de.emilyshub.ui.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import java.time.DayOfWeek

/**
 * A whole number from [range]: [text] is kept as typed, so that the field can be empty for a moment while
 * the [number] is retyped; empty counts as 0. Red while [number] is out of [range].
 */
@Composable
internal fun NumberField(text: String, number: Int, range: IntRange, label: String, onChange: (String, Int) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val digits = typed.filter(Char::isDigit).take(NUMBER_DIGITS)
            onChange(digits, digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = number !in range,
        supportingText = { Text(stringResource(R.string.routine_interval_range, range.first, range.last)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

/** One of a choice, with its radio button. */
@Composable
internal fun RadioOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

/** One chip per weekday, Monday first; a hint while none is chosen. */
@Composable
internal fun WeekdayChips(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val names = stringArrayResource(R.array.weekdays_short)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in days,
                onClick = { onChange(if (day in days) days - day else days + day) },
                label = { Text(names[day.ordinal]) },
            )
        }
    }
    if (days.isEmpty()) Problem(stringResource(R.string.routine_weekdays_missing))
}

/** What keeps the routine or to-do from being saved, under the field it is about. */
@Composable
internal fun Problem(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

private const val NUMBER_DIGITS = 3
