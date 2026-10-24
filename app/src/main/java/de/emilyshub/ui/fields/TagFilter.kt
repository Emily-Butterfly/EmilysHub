package de.emilyshub.ui.fields

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.tags.Tag
import de.emilyshub.ui.ChoiceChip

/** "Alle", then each tag, by name: the one chosen shows only the entries that have it (SPEC.md §8). */
@Composable
fun TagFilter(tags: List<Tag>, chosen: Tag?, onChoose: (Tag?) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChoiceChip(stringResource(R.string.filter_all), chosen = chosen == null, onClick = { onChoose(null) })
        tags.forEach { tag ->
            // Tapped again, it shows all of them again.
            ChoiceChip(tag.name, chosen = tag == chosen, onClick = { onChoose(if (tag == chosen) null else tag) })
        }
    }
}
