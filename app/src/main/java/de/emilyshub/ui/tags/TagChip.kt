package de.emilyshub.ui.tags

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** A tag to choose, or "Alle": chosen, it shows a check mark, so that it does not stand out by its colour alone. */
@Composable
fun TagChip(label: String, chosen: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = chosen,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (chosen) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
    )
}
