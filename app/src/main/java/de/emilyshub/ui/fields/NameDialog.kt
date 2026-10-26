package de.emilyshub.ui.fields

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import de.emilyshub.R
import de.emilyshub.data.NameProblem

/**
 * Asks for a name, such as a tag's, in a field [label]led so, starting out with [initial]; one with a problem, as
 * [problemOf] finds it, cannot be confirmed: one longer than [max] or [taken] says so.
 */
@Composable
fun NameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    @StringRes label: Int,
    @StringRes taken: Int,
    max: Int,
    initial: String,
    problemOf: (String) -> NameProblem?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val problem = problemOf(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                // A missing name only keeps the button off: the empty field says enough.
                isError = problem == NameProblem.TooLong || problem == NameProblem.Taken,
                supportingText = when (problem) {
                    NameProblem.TooLong -> {
                        { Text(stringResource(R.string.text_too_long, max)) }
                    }
                    NameProblem.Taken -> {
                        { Text(stringResource(taken)) }
                    }
                    NameProblem.Missing, null -> null
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = problem == null) { Text(stringResource(confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
