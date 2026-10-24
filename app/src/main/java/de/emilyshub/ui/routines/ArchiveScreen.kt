package de.emilyshub.ui.routines

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.emilyshub.R
import de.emilyshub.routines.Routines
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.launchNow

/** The archived routines (SPEC.md §2): out of the way, with what they did; each can be brought back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(routines: Routines, onBack: () -> Unit) {
    val archived by remember(routines) { routines.archived() }.collectAsState(initial = null)
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
                items(list, key = { it.id }) { routine ->
                    ListItem(
                        headlineContent = { Text(routine.name) },
                        supportingContent = { Text(scheduleText(routine)) },
                        trailingContent = {
                            TextButton(onClick = { scope.launchNow { routines.restore(routine.id) } }) {
                                Text(stringResource(R.string.archive_restore))
                            }
                        },
                    )
                }
            }
        }
    }
}
