package de.emilyshub.ui.doku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.doku.Doku
import de.emilyshub.doku.PatientDraft
import de.emilyshub.doku.PatientProblem
import de.emilyshub.ui.fields.LengthHint
import kotlinx.coroutines.launch

/**
 * A patient's name or initials and year of birth, to add one ([confirm] "Anlegen") or change them: in a dialog, which
 * [onSave] closes once it has saved the draft, with no problems in [year], the year it is.
 */
@Composable
internal fun PatientDialog(
    doku: Doku,
    title: String,
    confirm: String,
    initial: PatientDraft,
    year: Int,
    onSave: suspend (PatientDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    // Not saveable: nothing of the Doku goes into the saved state.
    var draft by remember { mutableStateOf(initial) }
    var tried by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val problems = draft.problems(year).takeIf { tried }.orEmpty()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text(stringResource(R.string.doku_patient_name)) },
                    singleLine = true,
                    isError = PatientProblem.NameMissing in problems || PatientProblem.NameTooLong in draft.problems(year),
                    supportingText = {
                        if (PatientProblem.NameMissing in problems) {
                            Text(stringResource(R.string.doku_patient_name_missing))
                        } else {
                            LengthHint(InputText.lengthOf(draft.cleanedName()), PatientDraft.NAME_MAX)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.birthYear,
                    onValueChange = { typed -> draft = draft.copy(birthYear = typed.filter(Char::isDigit).take(4)) },
                    label = { Text(stringResource(R.string.doku_patient_birth_year)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = PatientProblem.BirthYearInvalid in problems,
                    supportingText = if (PatientProblem.BirthYearInvalid in problems) {
                        { Text(stringResource(R.string.doku_patient_birth_year_invalid, PatientDraft.FIRST_YEAR, year)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    tried = true
                    if (draft.problems(year).isEmpty()) {
                        busy = true
                        scope.launch { onSave(draft) }
                    }
                },
                enabled = !busy,
            ) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.touchesDoku(doku),
    )
}
