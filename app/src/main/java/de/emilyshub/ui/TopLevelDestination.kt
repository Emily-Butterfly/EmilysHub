package de.emilyshub.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import de.emilyshub.R

/** The areas in the bottom bar, in display order (SPEC.md §1, Navigation). */
enum class TopLevelDestination(@param:StringRes val label: Int, val icon: ImageVector) {
    Today(R.string.destination_today, Icons.Filled.DateRange),
    Routines(R.string.destination_routines, Icons.Filled.Refresh),
    Todos(R.string.destination_todos, Icons.Filled.CheckCircle),
    Notes(R.string.destination_notes, Icons.Filled.Edit),

    /** "Sonstiges": a list of what has no tab of its own, opened in it ([MoreEntry]). */
    More(R.string.destination_more, ThreeDots),
}

/** Three dots side by side, as Material's "more horizontal", which the core icons lack. */
private val ThreeDots: ImageVector
    get() = materialIcon(name = "Filled.MoreHoriz") {
        materialPath {
            for (x in listOf(6f, 12f, 18f)) {
                moveTo(x, 10f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
            }
        }
    }

/**
 * The most the bottom bar's names grow with the system's type (#114): the most, in tenths, at which the longest,
 * "Sonstiges", still fits whole on one line on the A52's width, a fifth of it per tab; at 1.4 it is cut short.
 */
private const val TAB_LABEL_MAX_SCALE = 1.3f

/**
 * A tab's name in the bottom bar (SPEC.md §1; #114): on one line, its type growing with the system's only up to
 * [TAB_LABEL_MAX_SCALE], so that it does not break within the word; should it not fit even so, cut short with "…".
 * The rest of the app grows on as the system's type does.
 */
@Composable
fun TabLabel(text: String) {
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelMedium
    // The size as at the usual type, times the scale up to the most.
    val scale = minOf(density.fontScale, TAB_LABEL_MAX_SCALE) / density.fontScale
    Text(
        text = text,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}
