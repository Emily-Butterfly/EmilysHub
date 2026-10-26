package de.emilyshub.ui.routines

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
import de.emilyshub.routines.RoutineTarget
import de.emilyshub.ui.Hint
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.NumberField
import de.emilyshub.ui.fields.RadioOption

/**
 * What the routine aims at for the goal it hangs under (SPEC.md §2, §6): nothing, so that it only shows with the goal, so
 * many days done in a row, or done so many times; switching between the last two keeps the number.
 */
@Composable
fun TargetFields(target: RoutineTarget?, onChange: (RoutineTarget?) -> Unit) {
    // As typed, so that the field can be empty for a moment while the number is retyped.
    var countText by rememberSaveable { mutableStateOf((target?.count ?: DEFAULT_STREAK).toString()) }
    val count = countText.toIntOrNull() ?: 0

    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.routine_target))
        Hint(stringResource(R.string.routine_target_hint))
        RadioOption(stringResource(R.string.routine_target_none), selected = target == null) { onChange(null) }
        RadioOption(stringResource(R.string.routine_target_streak), selected = target is RoutineTarget.Streak) {
            onChange(RoutineTarget.Streak(count))
        }
        RadioOption(stringResource(R.string.routine_target_times_done), selected = target is RoutineTarget.TimesDone) {
            onChange(RoutineTarget.TimesDone(count))
        }
        if (target != null) {
            NumberField(countText, target.count, RoutineTarget.COUNTS, stringResource(R.string.routine_target_count)) { text, number ->
                countText = text
                onChange(target.withCount(number))
            }
        }
    }
}

/** As in SPEC.md §2: "30 Tage Streak". */
private const val DEFAULT_STREAK = 30
