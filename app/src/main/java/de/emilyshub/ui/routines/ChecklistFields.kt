package de.emilyshub.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.ui.SectionTitle

/** The checklist's sub-items in their order (SPEC.md §2): each can be changed or taken away, and more added. */
@Composable
fun ChecklistFields(items: List<ChecklistDraftItem>, onChange: (List<ChecklistDraftItem>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.routine_checklist))
        items.forEachIndexed { index, item ->
            val text = item.cleaned().text
            val tooLong = InputText.lengthOf(text) > RoutineDraft.CHECKLIST_ITEM_MAX
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.text,
                    onValueChange = { text -> onChange(items.toMutableList().also { it[index] = item.copy(text = text) }) },
                    label = { Text(stringResource(R.string.checklist_item)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    isError = tooLong,
                    supportingText = if (tooLong) {
                        { Text(stringResource(R.string.text_too_long, RoutineDraft.CHECKLIST_ITEM_MAX)) }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChange(items.toMutableList().also { it.removeAt(index) }) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(
                            R.string.checklist_item_remove,
                            text.ifEmpty { stringResource(R.string.checklist_item) },
                        ),
                    )
                }
            }
        }
        if (items.size < RoutineDraft.CHECKLIST_MAX) {
            TextButton(onClick = { onChange(items + ChecklistDraftItem(text = "")) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.checklist_item_add))
            }
        }
    }
}
