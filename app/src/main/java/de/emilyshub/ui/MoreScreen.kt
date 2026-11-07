package de.emilyshub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** The list of "Sonstiges" (SPEC.md §1): an entry each of what it holds, large to tap, opening it in the tab ([onOpen]). */
@Composable
fun MoreScreen(contentPadding: PaddingValues, onOpen: (MoreEntry) -> Unit) {
    LazyColumn(contentPadding = contentPadding) {
        items(MoreEntry.entries) { entry ->
            ListItem(
                leadingContent = {
                    // The name says what it is; the emoji only shows it.
                    Text(entry.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics {})
                },
                headlineContent = { Text(stringResource(entry.label), style = MaterialTheme.typography.titleLarge) },
                modifier = Modifier
                    .clickable { onOpen(entry) }
                    .padding(vertical = 8.dp),
            )
            HorizontalDivider()
        }
    }
}
