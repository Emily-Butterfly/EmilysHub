package de.emilyshub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import de.emilyshub.data.InputText

/**
 * [content] laid out right to left if [text] starts so, as Arabic does, and as the app is otherwise: as the notes show
 * each such block, at the right with its start on the right.
 */
@Composable
fun DirectedBy(text: String, content: @Composable () -> Unit) {
    val direction = if (InputText.startsRightToLeft(text)) LayoutDirection.Rtl else LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
}
