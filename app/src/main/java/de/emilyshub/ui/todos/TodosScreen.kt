package de.emilyshub.ui.todos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tags.Tag
import de.emilyshub.tags.Tags
import de.emilyshub.todos.TodoOrder
import de.emilyshub.todos.TodoWithDetails
import de.emilyshub.todos.Todos
import de.emilyshub.ui.ChecklistRow
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.fields.PriorityNote
import de.emilyshub.ui.fields.TagFilter
import de.emilyshub.ui.fields.priorityNameStyle
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.plusFabSpace
import de.emilyshub.ui.rememberNow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * The open to-dos (SPEC.md §5) in the [order] chosen, each with when it is due, its priority, tags and checklist; above
 * them, the choice of the order and, once there are tags, of one to show only the to-dos that have it. Ticked off, or
 * all of its checklist, a to-do goes to the archive, which can be undone right away. Tapping one opens it in the
 * editor, "+" starts a new one. Below them, the way to the archive, if anything is in it.
 */
@Composable
fun TodosScreen(
    todos: Todos,
    tags: Tags,
    order: TodoOrder,
    onOrderChange: (TodoOrder) -> Unit,
    tagFilter: Long?,
    onTagFilterChange: (Long?) -> Unit,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onOpenArchive: () -> Unit,
) {
    val open by remember(todos) { todos.open() }.collectAsState(initial = null)
    val archivedCount by remember(todos) { todos.archived().map { it.size } }.collectAsState(initial = 0)
    val allTags by remember(tags) { tags.all() }.collectAsState(initial = emptyList())
    // One deleted meanwhile no longer filters.
    val filter = allTags.find { it.id == tagFilter }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Looked at anew each minute, so that a to-do turning overdue says so.
    val now = rememberNow(todos::now)
    val today = todos.dayOf(now)
    val doneMessage = stringResource(R.string.todo_done)
    val undoLabel = stringResource(R.string.undo)

    /** Says that [title] is ticked off and gone to the archive, with the way to [undo] it. */
    suspend fun offerUndo(title: String, undo: suspend () -> Unit) {
        val result = snackbar.showSnackbar(doneMessage.format(title), actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) undo()
    }

    val list = open
    Box(Modifier.fillMaxSize()) {
        when {
            list == null -> Unit
            list.isEmpty() && archivedCount == 0 -> PlaceholderScreen(stringResource(R.string.todos_empty), contentPadding)
            else -> LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
                val shown = list.filter { filter == null || filter.id in it.tagIds }.sortedWith(order.comparator)
                item(key = "order") { OrderChoice(order, onOrderChange) }
                if (allTags.isNotEmpty()) {
                    item(key = "filter") { TagFilter(allTags, filter, onChoose = { onTagFilterChange(it?.id) }) }
                }
                if (filter != null && shown.isEmpty()) {
                    item(key = "none") {
                        Text(
                            text = stringResource(R.string.todos_filter_empty, filter.name),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(shown, key = { it.todo.id }) { details ->
                    val todo = details.todo
                    Column {
                        TodoRow(
                            details = details,
                            tagNames = allTags.filter { it.id in details.tagIds }.map(Tag::name),
                            today = today,
                            overdue = todos.isOverdue(todo, now),
                            onTick = {
                                scope.launchNow {
                                    todos.markDone(todo.id)
                                    offerUndo(todo.title) { todos.reopen(todo.id) }
                                }
                            },
                            onClick = { onOpen(todo.id) },
                        )
                        details.checklist().forEach { entry ->
                            ChecklistRow(entry, tickable = true) { ticked ->
                                scope.launchNow {
                                    if (todos.tick(todo.id, entry.itemId, ticked)) {
                                        // Unticking the item opens the to-do again, the rest of its checklist still ticked off.
                                        offerUndo(todo.title) { todos.tick(todo.id, entry.itemId, ticked = false) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (archivedCount > 0) {
                    item(key = "archive") {
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
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.todo_create))
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(contentPadding),
        )
    }
}

/** By due or by priority (SPEC.md §5). */
@Composable
private fun OrderChoice(order: TodoOrder, onChange: (TodoOrder) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TodoOrder.entries.forEachIndexed { index, choice ->
            SegmentedButton(
                selected = order == choice,
                onClick = { onChange(choice) },
                shape = SegmentedButtonDefaults.itemShape(index, TodoOrder.entries.size),
                label = {
                    Text(
                        stringResource(
                            when (choice) {
                                TodoOrder.ByDue -> R.string.todos_order_due
                                TodoOrder.ByPriority -> R.string.todos_order_priority
                            },
                        ),
                    )
                },
            )
        }
    }
}

/**
 * A to-do with a box to tick it off, when it is due, said in red once it is overdue, its priority, the places it reminds
 * at, its tags and description.
 */
@Composable
private fun TodoRow(
    details: TodoWithDetails,
    tagNames: List<String>,
    today: LocalDate,
    overdue: Boolean,
    onTick: () -> Unit,
    onClick: () -> Unit,
) {
    val todo = details.todo
    val tick = stringResource(R.string.todo_tick, todo.title)
    ListItem(
        leadingContent = {
            Checkbox(checked = false, onCheckedChange = { onTick() }, modifier = Modifier.semantics { contentDescription = tick })
        },
        headlineContent = { Text(todo.title, style = priorityNameStyle(todo.priority)) },
        supportingContent = {
            Column {
                dueText(todo.due, today)?.let { due ->
                    if (overdue) {
                        Text(stringResource(R.string.todo_overdue_label, due), color = MaterialTheme.colorScheme.error)
                    } else {
                        Text(stringResource(R.string.todo_due_label, due))
                    }
                }
                PriorityNote(todo.priority)
                val places = details.placeNames.joinToString(stringResource(R.string.today_or))
                if (places.isNotEmpty()) Text(stringResource(R.string.todo_reminder_places, places))
                if (tagNames.isNotEmpty()) Text(tagNames.joinToString(" · "))
                if (todo.description.isNotEmpty()) Text(todo.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
