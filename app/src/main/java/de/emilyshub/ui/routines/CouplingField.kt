package de.emilyshub.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.Routines
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.Problem

/**
 * "Gekoppelt mit…" (SPEC.md §2): the other routines the routine is coupled with, of which one done is enough for the
 * day. Offers those in use with one time a day, and keeps those of its group paused or archived, so that they can be
 * taken out of it. A routine with [times] other than one cannot be coupled: the field says so, and, coupled, that it
 * needs to leave its group first.
 */
@Composable
fun CouplingField(routines: Routines, routineId: Long?, times: Int, selected: Set<Long>, onChange: (Set<Long>) -> Unit) {
    val all by remember(routines) { routines.all() }.collectAsState(initial = emptyList())
    val others = all.filter { routine ->
        routine.id != routineId &&
            (routine.id in selected || routine.status != RoutineStatus.Archived && routine.times.size == 1)
    }
    if (others.isEmpty()) return
    Column(Modifier.testTag("coupling")) {
        SectionTitle(stringResource(R.string.routine_coupling))
        if (times != 1 && selected.isEmpty()) {
            Hint(stringResource(R.string.routine_coupling_one_time))
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            others.forEach { other ->
                val chosen = other.id in selected
                ChoiceChip(other.name, chosen, onClick = { onChange(if (chosen) selected - other.id else selected + other.id) })
            }
        }
        when {
            times != 1 -> Problem(stringResource(R.string.routine_coupling_needs_one_time))
            selected.isNotEmpty() -> Hint(stringResource(R.string.routine_coupling_summary))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
