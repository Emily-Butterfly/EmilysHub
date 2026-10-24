package de.emilyshub.ui.routines

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.routines.Priority

/** Low, normal or high (SPEC.md §2). */
@Composable
fun PriorityField(priority: Priority, onChange: (Priority) -> Unit) {
    Column {
        SectionTitle(stringResource(R.string.routine_priority))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Priority.entries.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = priority == choice,
                    onClick = { onChange(choice) },
                    shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size),
                    label = { Text(stringResource(choice.choiceLabel)) },
                )
            }
        }
    }
}

/** What a list says under a routine of high or low priority; nothing for normal. */
@Composable
fun PriorityNote(priority: Priority) {
    val note = priority.note ?: return
    Text(
        text = stringResource(note),
        style = MaterialTheme.typography.bodyMedium,
        color = if (priority == Priority.High) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val Priority.choiceLabel: Int
    @StringRes get() = when (this) {
        Priority.Low -> R.string.priority_low
        Priority.Normal -> R.string.priority_normal
        Priority.High -> R.string.priority_high
    }

private val Priority.note: Int?
    @StringRes get() = when (this) {
        Priority.Low -> R.string.priority_low_note
        Priority.Normal -> null
        Priority.High -> R.string.priority_high_note
    }
