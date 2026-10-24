package de.emilyshub.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.emilyshub.routines.ChecklistEntry

/** An item of the checklist of a routine or a to-do, under it: ticking off the last one ticks off what it belongs to. */
@Composable
fun ChecklistRow(entry: ChecklistEntry, tickable: Boolean, onTickedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = entry.ticked, enabled = tickable, role = Role.Checkbox, onValueChange = onTickedChange)
            .padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = entry.ticked, onCheckedChange = null, enabled = tickable)
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (entry.ticked) TextDecoration.LineThrough else null,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
