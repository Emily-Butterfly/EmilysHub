package de.emilyshub.ui.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.Routine
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.routines.Routines
import de.emilyshub.ui.PlaceholderScreen
import kotlinx.coroutines.flow.map

/**
 * The routines in use, by their first time; tapping one opens it in the editor, "+" starts a new one. Below
 * them, the way to the archived ones, if there are any.
 */
@Composable
fun RoutinesScreen(
    routines: Routines,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    val inUse by remember(routines) { routines.inUse() }.collectAsState(initial = null)
    val archivedCount by remember(routines) { routines.archived().map { it.size } }.collectAsState(initial = 0)
    val list = inUse
    Box(Modifier.fillMaxSize()) {
        when {
            list == null -> Unit
            list.isEmpty() && archivedCount == 0 -> PlaceholderScreen(stringResource(R.string.routines_empty), contentPadding)
            else -> LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
                items(list, key = { it.id }) { routine ->
                    RoutineRow(routine, onClick = { onOpen(routine.id) })
                }
                if (archivedCount > 0) {
                    item {
                        TextButton(onClick = onOpenArchive, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(stringResource(R.string.archive_open, archivedCount))
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onCreate,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(contentPadding)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.routine_create))
        }
    }
}

@Composable
private fun RoutineRow(routine: Routine, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(routine.name, style = priorityNameStyle(routine.priority)) },
        supportingContent = {
            Column {
                Text(scheduleText(routine))
                if (routine.status == RoutineStatus.Paused) Text(stringResource(R.string.routine_paused))
                PriorityNote(routine.priority)
                if (routine.description.isNotEmpty()) {
                    Text(routine.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Room below the last routine, so that the "+" button never covers it. */
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
