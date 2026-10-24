package de.emilyshub.ui.todos

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.emilyshub.R
import de.emilyshub.todos.Todo
import de.emilyshub.todos.Todos
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.numericDate

/**
 * The to-dos ticked off (SPEC.md §5), the one done last first, with the day each was done: each can be brought back,
 * or deleted for good after asking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoArchiveScreen(todos: Todos, onBack: () -> Unit) {
    val archived by remember(todos) { todos.archived() }.collectAsState(initial = null)
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archive_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        val list = archived
        when {
            list == null -> Unit
            list.isEmpty() -> PlaceholderScreen(stringResource(R.string.archive_empty), contentPadding)
            else -> LazyColumn(contentPadding = contentPadding) {
                items(list, key = { it.id }) { todo ->
                    ArchivedRow(
                        todo = todo,
                        doneOn = todo.doneAt?.let { todos.dayOf(it).numericDate() }.orEmpty(),
                        onRestore = { scope.launchNow { todos.reopen(todo.id) } },
                        onDelete = { deleting = todo.id },
                    )
                }
            }
        }
    }

    val doomed = archived.orEmpty().find { it.id == deleting } ?: return
    AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.todo_delete_for_good_title)) },
        text = { Text(stringResource(R.string.todo_delete_for_good_text, doomed.title)) },
        confirmButton = {
            TextButton(
                onClick = {
                    deleting = null
                    scope.launchNow { todos.delete(doomed.id) }
                },
            ) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ArchivedRow(todo: Todo, doneOn: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(todo.title) },
        supportingContent = { Text(stringResource(R.string.todo_done_on, doneOn)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRestore) { Text(stringResource(R.string.archive_restore)) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.todo_delete_for_good, todo.title))
                }
            }
        },
    )
}
