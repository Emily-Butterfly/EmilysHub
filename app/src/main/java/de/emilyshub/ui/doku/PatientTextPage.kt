package de.emilyshub.ui.doku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import de.emilyshub.doku.DokuSaver
import de.emilyshub.doku.Patient
import de.emilyshub.doku.Patients
import de.emilyshub.notes.Markdown
import de.emilyshub.ui.fields.LengthHint
import de.emilyshub.ui.notes.MarkdownText

/**
 * One of a patient's four texts, to write in Markdown as in the notes, "Bearbeiten" and "Vorschau" (SPEC.md §14). What
 * is typed is saved as the page closes, by "Fertig" ([onDone]), back or a rotation, and before the Doku locks; it never
 * goes into the saved state. It takes no more than [Patient.TEXT_MAX] characters.
 */
@Composable
internal fun PatientTextPage(doku: Doku, patients: Patients, page: DokuPage.Text, contentPadding: PaddingValues, onDone: () -> Unit) {
    var loaded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page) {
        val patient = patients.get(page.id)
        if (patient == null || patient.isClosed) onDone() else loaded = patient.textOf(page.field)
    }
    val original = loaded ?: return
    var text by remember { mutableStateOf(original) }
    var previewing by remember { mutableStateOf(false) }

    val current by rememberUpdatedState(text)
    DisposableEffect(page) {
        val changed = { current != original }
        val saver = doku.addSaver(DokuSaver { if (changed()) patients.pendingText(page.id, page.field, current) else null })
        onDispose {
            saver.close()
            if (changed()) patients.setTextLater(page.id, page.field, current)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text(
                stringResource(page.field.label),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = onDone) { Text(stringResource(R.string.doku_field_done)) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to R.string.note_writing, true to R.string.note_preview).forEachIndexed { index, (preview, label) ->
                SegmentedButton(
                    selected = previewing == preview,
                    onClick = { previewing = preview },
                    shape = SegmentedButtonDefaults.itemShape(index, count = 2),
                    label = { Text(stringResource(label)) },
                )
            }
        }
        // Only the text scrolls: its name, "Fertig" and the choice of mode stay in reach.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (previewing) {
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
                    onValueChange = { typed -> if (!Patient.textTooLong(typed)) text = typed },
                    label = { Text(stringResource(page.field.label)) },
                    minLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = {
                        Column {
                            Text(stringResource(R.string.doku_field_hint))
                            LengthHint(InputText.lengthOf(InputText.multiLine(text)), Patient.TEXT_MAX)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}
