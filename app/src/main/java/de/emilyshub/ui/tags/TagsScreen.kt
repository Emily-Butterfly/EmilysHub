package de.emilyshub.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.emilyshub.R
import de.emilyshub.tags.Tags
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.launchNow

/** The tags shared by all areas (SPEC.md §8): "+" adds one, tapping one renames it, deleting one asks first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(tags: Tags, onBack: () -> Unit) {
    val all by remember(tags) { tags.all() }.collectAsState(initial = null)
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tag_create))
            }
        },
    ) { contentPadding ->
        val list = all
        when {
            list == null -> Unit
            list.isEmpty() -> PlaceholderScreen(stringResource(R.string.tags_empty), contentPadding)
            else -> LazyColumn(contentPadding = contentPadding) {
                items(list, key = { it.id }) { tag ->
                    ListItem(
                        headlineContent = { Text(tag.name) },
                        trailingContent = {
                            IconButton(onClick = { deleting = tag.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.tag_delete, tag.name))
                            }
                        },
                        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.tag_rename)) { renaming = tag.id },
                    )
                }
            }
        }
    }

    val list = all.orEmpty()
    if (creating) {
        TagNameDialog(
            title = R.string.tag_create,
            confirm = R.string.tag_create_confirm,
            initial = "",
            others = list,
            onConfirm = { name ->
                creating = false
                scope.launchNow { tags.create(name) }
            },
            onDismiss = { creating = false },
        )
    }
    list.find { it.id == renaming }?.let { tag ->
        TagNameDialog(
            title = R.string.tag_rename,
            confirm = R.string.tag_rename_confirm,
            initial = tag.name,
            others = list - tag,
            onConfirm = { name ->
                renaming = null
                scope.launchNow { tags.rename(tag.id, name) }
            },
            onDismiss = { renaming = null },
        )
    }
    list.find { it.id == deleting }?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.tag_delete_title)) },
            text = { Text(stringResource(R.string.tag_delete_text, tag.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        scope.launchNow { tags.delete(tag.id) }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
