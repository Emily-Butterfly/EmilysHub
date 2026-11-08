package de.emilyshub.ui.doku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.doku.DokuSaver
import de.emilyshub.doku.Patient
import de.emilyshub.notes.Markdown
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.notes.MarkdownText

/**
 * A text of the Doku in Markdown, as in the notes (SPEC.md §14): [title] with [actions] beside it, [header] below, then
 * "Bearbeiten" and "Vorschau" and the text, [hint] below it, and [footer] below that; only these scroll. Only to read without [onTextChange]:
 * then it shows only as it reads. It takes no more than [Patient.TEXT_MAX] characters.
 */
@Composable
internal fun DokuTextEditor(
    title: String,
    label: String,
    text: String,
    onTextChange: ((String) -> Unit)?,
    hint: String,
    contentPadding: PaddingValues,
    actions: @Composable RowScope.() -> Unit = {},
    header: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    var previewing by remember { mutableStateOf(onTextChange == null) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            actions()
        }
        header()
        if (onTextChange != null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to R.string.note_writing, true to R.string.note_preview).forEachIndexed { index, (preview, mode) ->
                    SegmentedButton(
                        selected = previewing == preview,
                        onClick = { previewing = preview },
                        shape = SegmentedButtonDefaults.itemShape(index, count = 2),
                        label = { Text(stringResource(mode)) },
                    )
                }
            }
        }
        // Only the text scrolls: its name, what is beside it and the choice of mode stay in reach.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (previewing || onTextChange == null) {
                val blocks = remember(text) { Markdown.parse(text) }
                if (blocks.isEmpty()) {
                    Text(stringResource(R.string.doku_field_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    MarkdownText(blocks)
                }
            } else {
                OutlinedTextField(
                    value = text,
                    // No more than it takes, so that what is typed can always be saved.
                    onValueChange = { typed -> if (!Patient.textTooLong(typed)) onTextChange(typed) },
                    label = { Text(label) },
                    minLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = {
                        Column {
                            Text(hint)
                            LengthHint(InputText.lengthOf(InputText.multiLine(text)), Patient.TEXT_MAX)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }
            footer()
        }
    }
}

/**
 * Saves [current], once it is not [original] any more, as the editor showing it under [key] closes, by [saveLater], and
 * before the Doku locks, by [pending]; never into the saved state. Nothing while [skip] says so, as once deleted.
 */
@Composable
internal fun SaveOnClose(
    doku: Doku,
    key: Any,
    original: String,
    current: String,
    pending: (String) -> suspend (DokuDatabase) -> Unit,
    saveLater: (String) -> Unit,
    skip: () -> Boolean = { false },
) {
    val text by rememberUpdatedState(current)
    DisposableEffect(key) {
        val changed = { !skip() && text != original }
        val saver = doku.addSaver(DokuSaver { if (changed()) pending(text) else null })
        onDispose {
            saver.close()
            if (changed()) saveLater(text)
        }
    }
}
