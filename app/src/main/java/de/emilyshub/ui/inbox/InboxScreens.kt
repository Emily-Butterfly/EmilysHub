package de.emilyshub.ui.inbox

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.Day
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.inbox.Inbox
import de.emilyshub.inbox.InboxEntry
import de.emilyshub.ui.dateAndTime
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.launchNow

/** What an entry of the inbox can be sorted in as (SPEC.md §16). */
enum class SortInto { Todo, Note, Routine }

/**
 * The line a thought is captured in (SPEC.md §16): one line, kept in the inbox by "Fertig" on the keyboard, or by the
 * [button] "Festhalten" where there is one; then the line is empty again and the keyboard stays for the next one. Close
 * to the 1 000 characters, it counts them. [focused], it takes the keyboard as it shows. What is typed is kept in
 * [typed], which a screen whose layout changes around the line keeps above it.
 */
@Composable
fun CaptureField(
    inbox: Inbox,
    modifier: Modifier = Modifier,
    button: Boolean = false,
    focused: Boolean = false,
    typed: MutableState<String> = rememberSaveable { mutableStateOf("") },
) {
    var text by typed
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext
    val focus = remember { FocusRequester() }
    val length = InputText.lengthOf(text)
    val keep = {
        // Nothing to see, or too long, stays in the line as it is.
        if (Inbox.checked(text) != null) {
            val kept = text
            text = ""
            scope.launchNow {
                if (inbox.capture(kept)) Toast.makeText(context, R.string.inbox_captured, Toast.LENGTH_SHORT).show()
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.inbox_capture)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            // Handled here, the keyboard stays open for the next thought.
            keyboardActions = KeyboardActions(onDone = { keep() }),
            isError = length > InboxEntry.TEXT_MAX,
            supportingText = if (length > InboxEntry.TEXT_MAX - COUNT_FROM) {
                { LengthHint(length, InboxEntry.TEXT_MAX) }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        if (button) {
            TextButton(onClick = keep, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.inbox_keep)) }
        }
    }
    if (focused) {
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

/**
 * The line for a new thought on its own, from the shortcut on the app's icon (SPEC.md §16): one field with the keyboard
 * open and "Festhalten", over what was open, which stays as it was; back leaves it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(inbox: Inbox, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_capture_short), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        CaptureField(
            inbox,
            Modifier
                .padding(contentPadding)
                .padding(16.dp),
            button = true,
            focused = true,
        )
    }
}

/** "Eingang (3)" under the line in Heute, which opens the inbox ([onOpen]); nothing while it is empty. */
@Composable
fun InboxLine(inbox: Inbox, onOpen: () -> Unit) {
    val count by remember(inbox) { inbox.count() }.collectAsState(initial = 0)
    if (count == 0) return
    TextButton(onClick = onOpen, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(stringResource(R.string.inbox_open, count))
    }
}

/**
 * The inbox (SPEC.md §16), the newest first, each entry with its text and when it was captured. Its text changes with a
 * tap, on as many lines as it takes; each is sorted in as a to-do, a note or a routine ([onSort]), which leaves the
 * inbox once that is saved, or deleted at once, with the way to undo it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(inbox: Inbox, day: Day, onSort: (SortInto, InboxEntry) -> Unit, onBack: () -> Unit) {
    val entries by remember(inbox) { inbox.entries() }.collectAsState(initial = null)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var changing by rememberSaveable { mutableStateOf<Long?>(null) }
    val deletedMessage = stringResource(R.string.inbox_deleted)
    val undoLabel = stringResource(R.string.undo)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { contentPadding ->
        val list = entries ?: return@Scaffold
        if (list.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(16.dp),
            ) {
                Text(
                    stringResource(R.string.inbox_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = contentPadding) {
            items(list, key = { it.id }) { entry ->
                InboxRow(
                    entry = entry,
                    day = day,
                    onChange = { changing = entry.id },
                    onSort = { onSort(it, entry) },
                    onDelete = {
                        scope.launchNow {
                            val deleted = inbox.delete(entry.id) ?: return@launchNow
                            val result = snackbar.showSnackbar(deletedMessage, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                            if (result == SnackbarResult.ActionPerformed) inbox.restore(deleted)
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
    entries?.find { it.id == changing }?.let { entry ->
        ChangeDialog(
            entry = entry,
            onSave = { text ->
                changing = null
                scope.launchNow { inbox.change(entry.id, text) }
            },
            onDismiss = { changing = null },
        )
    }
}

@Composable
private fun InboxRow(entry: InboxEntry, day: Day, onChange: () -> Unit, onSort: (SortInto) -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.inbox_change), role = Role.Button, onClick = onChange),
        ) {
            Text(entry.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                entry.capturedAt.atZone(day.zone()).dateAndTime(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onSort(SortInto.Todo) }) { Text(stringResource(R.string.inbox_as_todo)) }
            OutlinedButton(onClick = { onSort(SortInto.Note) }) { Text(stringResource(R.string.inbox_as_note)) }
            OutlinedButton(onClick = { onSort(SortInto.Routine) }) { Text(stringResource(R.string.inbox_as_routine)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
    }
}

/** The text of [entry] to change, on as many lines as it takes; nothing to see, or too long, is not saved. */
@Composable
private fun ChangeDialog(entry: InboxEntry, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(entry.id) { mutableStateOf(entry.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inbox_change_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = InputText.lengthOf(text) > InboxEntry.TEXT_MAX,
                supportingText = { LengthHint(InputText.lengthOf(text), InboxEntry.TEXT_MAX) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = Inbox.checked(text) != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** From how many characters short of the limit the line counts them. */
private const val COUNT_FROM = 100
