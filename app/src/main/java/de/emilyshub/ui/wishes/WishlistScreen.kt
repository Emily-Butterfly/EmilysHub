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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tags.Tag
import de.emilyshub.tags.Tags
import de.emilyshub.ui.PlaceholderScreen
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.fields.TagFilter
import de.emilyshub.ui.plusFabSpace
import de.emilyshub.wishes.WishStatus
import de.emilyshub.wishes.WishWithTags
import de.emilyshub.wishes.Wishes
import kotlinx.coroutines.flow.map

/**
 * The wishes not yet done (SPEC.md §6): those in progress, then the open ones, each with its period, tags and
 * description; above them, once there are tags, the choice of one to show only the wishes that have it. Nothing here
 * reminds. Tapping one opens it in the editor, "+" starts a new one. Below them, the way to the review of the wishes
 * come true, once there is one.
 */
@Composable
fun WishlistScreen(
    wishes: Wishes,
    tags: Tags,
    tagFilter: Long?,
    onTagFilterChange: (Long?) -> Unit,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onOpenReview: () -> Unit,
) {
    val current by remember(wishes) { wishes.current() }.collectAsState(initial = null)
    val fulfilledCount by remember(wishes) { wishes.fulfilled().map { it.size } }.collectAsState(initial = 0)
    val allTags by remember(tags) { tags.all() }.collectAsState(initial = emptyList())
    // One deleted meanwhile no longer filters.
    val filter = allTags.find { it.id == tagFilter }

    val list = current
    Box(Modifier.fillMaxSize()) {
        when {
            list == null -> Unit
            list.isEmpty() && fulfilledCount == 0 -> PlaceholderScreen(stringResource(R.string.wishes_empty), contentPadding)
            else -> LazyColumn(contentPadding = contentPadding.plusFabSpace()) {
                val shown = list.filter { filter == null || filter.id in it.tagIds }
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
                    items(inStatus, key = { it.wish.id }) { details ->
                        val tagNames = allTags.filter { it.id in details.tagIds }.map(Tag::name)
                        WishRow(details, tagNames, onClick = { onOpen(details.wish.id) })
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

/** A wish with when, in the user's words, its tags and description. */
@Composable
private fun WishRow(details: WishWithTags, tagNames: List<String>, onClick: () -> Unit) {
    val wish = details.wish
    ListItem(
        headlineContent = { Text(wish.title) },
        supportingContent = {
            Column {
                if (wish.period.isNotEmpty()) Text(wish.period)
                if (tagNames.isNotEmpty()) Text(tagNames.joinToString(" · "))
                if (wish.description.isNotEmpty()) Text(wish.description, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** "Offen", "In Arbeit", "Erledigt", as the editor offers them and the list heads them. */
internal val WishStatus.label: Int
    @StringRes get() = when (this) {
        WishStatus.Open -> R.string.wish_status_open
        WishStatus.InProgress -> R.string.wish_status_in_progress
        WishStatus.Done -> R.string.wish_status_done
    }
