package de.emilyshub.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One of several to choose, such as a tag or a place: chosen, it shows a check mark, not standing out by colour alone. */
@Composable
fun ChoiceChip(label: String, chosen: Boolean, onClick: () -> Unit) {
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
