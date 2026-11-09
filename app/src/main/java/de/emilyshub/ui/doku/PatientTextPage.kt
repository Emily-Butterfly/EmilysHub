package de.emilyshub.ui.doku

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.doku.Doku
import de.emilyshub.doku.Patient
import de.emilyshub.doku.Patients

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
    KeepDraft(doku, page) { patients.textDraft(page.id, page.field, original, text) }
    val label = stringResource(page.field.label)
    DokuTextEditor(
        title = label,
        label = label,
        text = text,
        onTextChange = { text = it },
        hint = stringResource(R.string.doku_field_hint),
        contentPadding = contentPadding,
        actions = { TextButton(onClick = onDone) { Text(stringResource(R.string.doku_field_done)) } },
    )
}
