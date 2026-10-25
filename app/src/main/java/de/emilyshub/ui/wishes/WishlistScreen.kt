package de.emilyshub.ui.wishes

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import de.emilyshub.todos.Todos
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.TagFilter
import de.emilyshub.ui.fields.goalIndent
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.plusFabSpace
import de.emilyshub.wishes.Goal
import de.emilyshub.wishes.GoalTodo
import de.emilyshub.wishes.WishStatus
import de.emilyshub.wishes.Wishes
import kotlinx.coroutines.flow.map

/**
 * The wishes not yet done (SPEC.md §6): those in progress, then the open ones, each with its period, tags, description
 * and progress; above them, once there are tags, the choice of one to show only the goals that have it, at any depth. A
 * goal with parts opens up to show them, indented below it: its subgoals, which open up in turn, and its to-dos, ticked
 * off right there. Nothing here reminds. Tapping a goal or a to-do opens it in its editor, "+" starts a new wish. Below them, the
 * way to the review of the wishes come true, once there is one.
 */
@Composable
fun WishlistScreen(
    wishes: Wishes,
    todos: Todos,
    tags: Tags,
    tagFilter: Long?,
    onTagFilterChange: (Long?) -> Unit,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    onOpenTodo: (Long) -> Unit,
    onCreate: () -> Unit,
    onOpenReview: () -> Unit,
) {
    val current by remember(wishes) { wishes.current() }.collectAsState(initial = null)
    val fulfilledCount by remember(wishes) { wishes.fulfilled().map { it.size } }.collectAsState(initial = 0)
    val allTags by remember(tags) { tags.all() }.collectAsState(initial = emptyList())
    // One deleted meanwhile no longer filters.
    val filter = allTags.find { it.id == tagFilter }
    // The goals opened up, to show their parts.
    var opened by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val scope = rememberCoroutineScope()

    /** The goal and, if opened up, its parts below it, each subgoal in turn the same way. */
    fun lines(goal: Goal, depth: Int): List<TreeLine> = listOf(TreeLine.OfGoal(goal, depth)) + if (goal.wish.id in opened) {
        goal.subgoals.flatMap { lines(it, depth + 1) } + goal.todos.map { TreeLine.OfTodo(it, depth + 1) }
    } else {
        emptyList()
    }

    val list = current
    Box(Modifier.fillMaxSize()) {
        when {
            list == null -> Unit
            list.isEmpty() && fulfilledCount == 0 -> PlaceholderScreen(stringResource(R.string.wishes_empty), contentPadding)
            else -> LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
                val shown = if (filter == null) list else wishes.withTag(list, filter.id)
                if (allTags.isNotEmpty()) {
                    item(key = "filter") { TagFilter(allTags, filter, onChoose = { onTagFilterChange(it?.id) }) }
                }
                if (filter != null && shown.isEmpty()) {
                    item(key = "none") {
                        Text(
                            text = stringResource(R.string.wishes_filter_empty, filter.name),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                shown.groupBy { it.wish.status }.forEach { (status, inStatus) ->
                    item(key = status.stored) {
                        Column(Modifier.padding(horizontal = 16.dp)) { SectionTitle(stringResource(status.label)) }
                    }
                    items(inStatus.flatMap { lines(it, depth = 0) }, key = TreeLine::key) { line ->
                        when (line) {
                            is TreeLine.OfGoal -> GoalRow(
                                goal = line.goal,
                                depth = line.depth,
                                tagNames = allTags.filter { it.id in line.goal.tagIds }.map(Tag::name),
                                opened = line.goal.wish.id in opened,
                                onToggle = {
                                    val id = line.goal.wish.id
                                    opened = if (id in opened) opened - id else opened + id
                                },
                                onClick = { onOpen(line.goal.wish.id) },
                            )
                            is TreeLine.OfTodo -> TodoPartRow(
                                todo = line.todo,
                                depth = line.depth,
                                onTick = { done ->
                                    scope.launchNow { if (done) todos.markDone(line.todo.id) else todos.reopen(line.todo.id) }
                                },
                                onClick = { onOpenTodo(line.todo.id) },
                            )
                        }
                    }
                }
                if (fulfilledCount > 0) {
                    item(key = "review") {
                        TextButton(onClick = onOpenReview, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(stringResource(R.string.wishes_review_open, fulfilledCount))
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
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.wish_create))
        }
    }
}

/** A line of the tree: a goal, or a to-do that is part of one, at its depth. */
private sealed interface TreeLine {
    val key: String

    data class OfGoal(val goal: Goal, val depth: Int) : TreeLine {
        override val key get() = "goal ${goal.wish.id}"
    }

    data class OfTodo(val todo: GoalTodo, val depth: Int) : TreeLine {
        override val key get() = "todo ${todo.id}"
    }
}

/**
 * A goal with when, in the user's words, its tags, description and progress, or that it is done; one with parts opens
 * up and closes again by its arrow.
 */
@Composable
private fun GoalRow(goal: Goal, depth: Int, tagNames: List<String>, opened: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    val wish = goal.wish
    ListItem(
        headlineContent = { Text(wish.title) },
        supportingContent = {
            Column {
                if (wish.period.isNotEmpty()) Text(wish.period)
                if (tagNames.isNotEmpty()) Text(tagNames.joinToString(" · "))
                if (wish.description.isNotEmpty()) Text(wish.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when {
                    wish.status == WishStatus.Done -> Text(stringResource(R.string.wish_status_done))
                    goal.hasParts || wish.manualProgress != null -> Text(stringResource(R.string.goal_progress, goal.percent))
                }
            }
        },
        trailingContent = if (goal.hasParts) {
            {
                val action = stringResource(if (opened) R.string.goal_close else R.string.goal_open, wish.title)
                IconButton(onClick = onToggle, modifier = Modifier.semantics { contentDescription = action }) {
                    Icon(if (opened) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                }
            }
        } else {
            null
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = goalIndent(depth, INDENT)),
    )
}

/** A to-do that is part of a goal, with a box to tick it off right here, or to open it again. */
@Composable
private fun TodoPartRow(todo: GoalTodo, depth: Int, onTick: (Boolean) -> Unit, onClick: () -> Unit) {
    val action = stringResource(if (todo.done) R.string.goal_todo_reopen else R.string.todo_tick, todo.title)
    ListItem(
        leadingContent = {
            Checkbox(checked = todo.done, onCheckedChange = onTick, modifier = Modifier.semantics { contentDescription = action })
        },
        headlineContent = { Text(todo.title) },
        supportingContent = { Text(stringResource(R.string.goal_todo)) },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(start = goalIndent(depth, INDENT)),
    )
}

private val INDENT = 24.dp

/** "Offen", "In Arbeit", "Erledigt", as the editor offers them and the list heads them. */
internal val WishStatus.label: Int
    @StringRes get() = when (this) {
        WishStatus.Open -> R.string.wish_status_open
        WishStatus.InProgress -> R.string.wish_status_in_progress
        WishStatus.Done -> R.string.wish_status_done
    }
