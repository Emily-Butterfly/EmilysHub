package de.emilyshub.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector
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
