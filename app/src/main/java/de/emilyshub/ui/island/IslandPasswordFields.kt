package de.emilyshub.ui.island

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import de.emilyshub.R
import de.emilyshub.island.Island
import de.emilyshub.island.IslandPasswordProblem

/** A field for an island's password, shown as dots, with its [problem] below if any. */
@Composable
internal fun PasswordField(value: String, onChange: (String) -> Unit, label: String, problem: String?) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        isError = problem != null,
        supportingText = problem?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A new password of an island, typed twice under [label] and [repeatLabel]; its [problem], once shown, below the field it
 * concerns. As in setting it first and in changing it.
 */
@Composable
internal fun NewPasswordFields(
    password: String,
    onPasswordChange: (String) -> Unit,
    repeated: String,
    onRepeatedChange: (String) -> Unit,
    problem: IslandPasswordProblem?,
    label: String,
    repeatLabel: String,
) {
    PasswordField(
        value = password,
        onChange = onPasswordChange,
        label = label,
        problem = stringResource(R.string.doku_password_too_short, Island.MIN_PASSWORD_LENGTH)
            .takeIf { problem == IslandPasswordProblem.TooShort },
    )
    PasswordField(
        value = repeated,
        onChange = onRepeatedChange,
        label = repeatLabel,
        problem = stringResource(R.string.doku_password_not_repeated).takeIf { problem == IslandPasswordProblem.NotRepeated },
    )
}
