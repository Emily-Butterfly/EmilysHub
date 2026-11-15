package de.emilyshub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.emilyshub.R

/**
 * The "⋮" of an entry named [name], opening its menu while [expanded] ([onExpandedChange]), which a long press on its row
 * may open too. Its [items] are given what closes it.
 */
@Composable
fun MoreMenu(name: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, items: @Composable (close: () -> Unit) -> Unit) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_about, name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            items { onExpandedChange(false) }
        }
    }
}

/** An item of a [MoreMenu], saying [text]: tapped, it closes the menu ([close]) and does [onClick]. */
@Composable
fun MoreMenuItem(text: String, close: () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        onClick = {
            close()
            onClick()
        },
    )
}
