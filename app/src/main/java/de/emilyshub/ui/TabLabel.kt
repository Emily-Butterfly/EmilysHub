package de.emilyshub.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified

/**
 * How far the bottom bar's names grow with the system's type, at most (#114). Measured on the A52's width, a fifth of
 * it per tab: at 1.3 times the longest name, "Sonstiges", still fits whole on one line; at 1.4 it is cut short.
 */
private const val TAB_LABEL_MAX_SCALE = 1.3f

/**
 * A tab's name in the bottom bar (SPEC.md §1; #114): on one line, its type growing with the system's only up to
 * [TAB_LABEL_MAX_SCALE], so that it does not break within the word; should it not fit even so, cut short with "…",
 * the full name staying for a screen reader. The rest of the app grows on as the system's type does.
 */
@Composable
internal fun TabLabel(text: String) {
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    // As the type would be at the most scale, however much larger the system's is.
    val scale = minOf(density.fontScale, TAB_LABEL_MAX_SCALE) / density.fontScale
    Text(
        text = text,
        style = style.copy(
            fontSize = style.fontSize * scale,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
