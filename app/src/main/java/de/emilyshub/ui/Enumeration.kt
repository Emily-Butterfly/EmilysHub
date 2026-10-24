package de.emilyshub.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.emilyshub.R

/** "07:30", "07:30 und 20:00", "07:30, 12:00 und 20:00" */
@Composable
fun enumeration(items: List<String>): String = when (items.size) {
    1 -> items.single()
    else -> stringResource(R.string.enumeration, items.dropLast(1).joinToString(", "), items.last())
}
