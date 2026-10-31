package de.emilyshub.ui.todos

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
import de.emilyshub.todos.TodoDraft
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.NumberField
import de.emilyshub.ui.fields.RadioOption

/**
 * When a to-do with a due day shows on Heute (SPEC.md §1): on its day only, or already [daysBefore] days before, under
 * "Demnächst".
 */
@Composable
fun ShowBeforeFields(daysBefore: Int?, onChange: (Int?) -> Unit) {
    // As typed, so that the field can be empty for a moment while the number is retyped.
    var daysText by rememberSaveable { mutableStateOf((daysBefore ?: DEFAULT_DAYS).toString()) }

    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.todo_show_title))
        RadioOption(stringResource(R.string.todo_show_on_day), selected = daysBefore == null) { onChange(null) }
        RadioOption(stringResource(R.string.todo_show_before), selected = daysBefore != null) {
            if (daysBefore == null) onChange(daysText.toIntOrNull() ?: DEFAULT_DAYS)
        }
        if (daysBefore != null) {
            NumberField(daysText, daysBefore, TodoDraft.SHOWS_DAYS_BEFORE, stringResource(R.string.todo_show_days)) { text, days ->
                daysText = text
                onChange(days)
            }
        }
    }
}

private const val DEFAULT_DAYS = 3
