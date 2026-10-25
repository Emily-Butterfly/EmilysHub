package de.emilyshub.ui.wishes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import de.emilyshub.R
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.numericDate
import de.emilyshub.wishes.Wishes

/**
 * The review (SPEC.md §6): the wishes come true, at any depth of the tree, the last first, each with the day it came
 * true and the goal it is part of. Tapping one opens it in the editor, where it can be set open or in progress again, or
 * deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishReviewScreen(wishes: Wishes, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val fulfilled by remember(wishes) { wishes.fulfilled() }.collectAsState(initial = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wishes_review_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { contentPadding ->
        val list = fulfilled
        when {
            list == null -> Unit
            list.isEmpty() -> PlaceholderScreen(stringResource(R.string.wishes_review_empty), contentPadding)
            else -> LazyColumn(contentPadding = contentPadding) {
                items(list, key = { it.wish.id }) { (wish, partOf) ->
                    ListItem(
                        headlineContent = { Text(wish.title) },
                        supportingContent = {
                            Column {
                                wish.doneOn?.let { Text(stringResource(R.string.wish_done_on_date, it.numericDate())) }
                                partOf?.let { Text(stringResource(R.string.wish_part_of, it)) }
                            }
                        },
                        modifier = Modifier.clickable { onOpen(wish.id) },
                    )
                }
            }
        }
    }
}
