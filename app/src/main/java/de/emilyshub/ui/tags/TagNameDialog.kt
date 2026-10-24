package de.emilyshub.ui.tags

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
import de.emilyshub.tags.TAG_NAME_MAX
import de.emilyshub.tags.Tag
import de.emilyshub.tags.TagProblem
import de.emilyshub.tags.tagNameProblem

/** Asks for a tag's name, starting out with [initial]; one with a problem, also next to the [others], cannot be confirmed. */
@Composable
fun TagNameDialog(
    @StringRes title: Int,
    @StringRes confirm: Int,
    initial: String,
    others: List<Tag>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val problem = tagNameProblem(name, others)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.tag_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                // A missing name only keeps the button off: the empty field says enough.
                isError = problem == TagProblem.NameTooLong || problem == TagProblem.NameTaken,
                supportingText = when (problem) {
                    TagProblem.NameTooLong -> {
                        { Text(stringResource(R.string.text_too_long, TAG_NAME_MAX)) }
                    }
                    TagProblem.NameTaken -> {
                        { Text(stringResource(R.string.tag_name_taken)) }
                    }
                    TagProblem.NameMissing, null -> null
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
