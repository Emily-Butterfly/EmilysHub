package de.emilyshub.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.search.Search
import de.emilyshub.search.SearchArea
import de.emilyshub.search.SearchGroup
import de.emilyshub.search.SearchHit
import de.emilyshub.ui.SectionTitle
import kotlinx.coroutines.delay

/**
 * The search over all areas (SPEC.md §8), reached from every tab: what [query] finds in routines, to-dos, wishes and
 * notes, area by area, each with how many more there are beyond those shown, looked for anew a moment after typing
 * stops. Tapping an entry opens it in its editor ([onOpen]);
 * back from there, the search shows again, as typed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    search: Search,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (SearchArea, Long) -> Unit,
    onBack: () -> Unit,
) {
    var found by remember { mutableStateOf<List<SearchGroup>?>(null) }
    // Coming back from an entry found, the results show at once; while typing, after a pause.
    var typed by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (typed && query.isNotBlank()) delay(TYPING_PAUSE_MILLIS)
        typed = true
        found = search.find(query)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(Modifier.padding(contentPadding)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.search_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focus),
            )
            val groups = found
            when {
                groups == null -> Unit
                query.isBlank() -> Hint(stringResource(R.string.search_hint))
                groups.isEmpty() -> Hint(stringResource(R.string.search_none))
                else -> LazyColumn {
                    groups.forEach { group ->
                        item(key = group.area.name) {
                            Column(Modifier.padding(horizontal = 16.dp)) { SectionTitle(stringResource(group.area.label)) }
                        }
                        items(group.hits, key = { "${group.area.name} ${it.id}" }) { hit ->
                            HitRow(hit, onClick = { onOpen(group.area, hit.id) })
                        }
                        if (group.more > 0) {
                            item(key = "${group.area.name} more") {
                                Hint(pluralStringResource(R.plurals.search_more, group.more, group.more))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(hit.title) }, modifier = Modifier.clickable(onClick = onClick))
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

/** "Routinen", "To-dos", "Ziele", "Notizen". */
private val SearchArea.label: Int
    @StringRes get() = when (this) {
        SearchArea.Routines -> R.string.destination_routines
        SearchArea.Todos -> R.string.destination_todos
        SearchArea.Wishes -> R.string.search_wishes
        SearchArea.Notes -> R.string.destination_notes
        SearchArea.Bookings -> R.string.ledger_title
    }

/** How long typing has to pause before the search looks again. */
private const val TYPING_PAUSE_MILLIS = 250L
