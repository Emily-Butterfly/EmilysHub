package de.emilyshub.ui.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tags.Tags
import de.emilyshub.ui.ChoiceChip
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.launchNow
import de.emilyshub.ui.tags.TagNameDialog

/** The routine's tags (SPEC.md §8): any of the shared ones, chosen with a tap, and a new one made right here. */
@Composable
fun TagsField(tags: Tags, selected: Set<Long>, onChange: (Set<Long>) -> Unit) {
    val all by remember(tags) { tags.all() }.collectAsState(initial = emptyList())
    var creating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // The tags chosen once a new one is saved, which may be after a change.
    val currentSelected by rememberUpdatedState(selected)
    val currentOnChange by rememberUpdatedState(onChange)

    Column {
        SectionTitle(stringResource(R.string.field_tags))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            all.forEach { tag ->
                val chosen = tag.id in selected
                ChoiceChip(tag.name, chosen, onClick = { onChange(if (chosen) selected - tag.id else selected + tag.id) })
            }
            AssistChip(
                onClick = { creating = true },
                label = { Text(stringResource(R.string.tag_new)) },
                leadingIcon = {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                },
            )
        }
    }

    if (creating) {
        TagNameDialog(
            title = R.string.tag_new,
            confirm = R.string.tag_create_confirm,
            initial = "",
            others = all,
            onConfirm = { name ->
                creating = false
                scope.launchNow {
                    val id = tags.create(name)
                    currentOnChange(currentSelected + id)
                }
            },
            onDismiss = { creating = false },
        )
    }
}
