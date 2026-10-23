package de.emilyshub.ui.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.Routine
import de.emilyshub.routines.Routines
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.clockTime

/** All routines, by time; tapping one opens it in the editor. */
@Composable
fun RoutinesScreen(routines: Routines, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    val all by remember(routines) { routines.all() }.collectAsState(initial = null)
    val list = all
    when {
        list == null -> Unit
        list.isEmpty() -> PlaceholderScreen(stringResource(R.string.routines_empty), contentPadding)
        else -> LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
            items(list, key = { it.id }) { routine ->
                RoutineRow(routine, onClick = { onOpen(routine.id) })
            }
        }
    }
}

@Composable
private fun RoutineRow(routine: Routine, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(routine.name) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.routine_schedule_daily, routine.time.clockTime()))
                if (routine.description.isNotEmpty()) {
                    Text(routine.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Room below the last routine, so the "+" button never covers it. */
@Composable
private fun PaddingValues.plusFabSpace(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + 88.dp,
    )
}
