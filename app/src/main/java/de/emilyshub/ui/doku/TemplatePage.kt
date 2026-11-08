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
import de.emilyshub.doku.Appointments
import de.emilyshub.doku.Doku

/**
 * The appointment template (SPEC.md §14), in Markdown, which each new appointment starts with; empty for none. Saved as
 * the page closes, by "Fertig" ([onDone]), back or a rotation, and before the Doku locks. Appointments there already
 * stay as they are.
 */
@Composable
internal fun TemplatePage(doku: Doku, appointments: Appointments, contentPadding: PaddingValues, onDone: () -> Unit) {
    var loaded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(appointments) { loaded = appointments.template() }
    val original = loaded ?: return
    var text by remember { mutableStateOf(original) }
    SaveOnClose(
        doku = doku,
        key = DokuPage.Template,
        original = original,
        current = text,
        pending = appointments::pendingTemplate,
        saveLater = appointments::setTemplateLater,
    )
    val title = stringResource(R.string.doku_menu_template)
    DokuTextEditor(
        title = title,
        label = title,
        text = text,
        onTextChange = { text = it },
        hint = stringResource(R.string.doku_template_hint),
        contentPadding = contentPadding,
        actions = { TextButton(onClick = onDone) { Text(stringResource(R.string.doku_field_done)) } },
    )
}
