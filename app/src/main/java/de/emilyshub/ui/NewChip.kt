package de.emilyshub.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/** The chip after those to choose in a field, to make one more right there, such as "+ Neuer Tag" or "+ Neuer Ort". */
@Composable
fun NewChip(@StringRes label: Int, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}
