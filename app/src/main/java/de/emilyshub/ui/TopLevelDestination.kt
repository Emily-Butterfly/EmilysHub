package de.emilyshub.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import de.emilyshub.R

/** The areas in the bottom bar, in display order (SPEC.md §1, Navigation). */
enum class TopLevelDestination(@param:StringRes val label: Int, val icon: ImageVector) {
    Today(R.string.destination_today, Icons.Filled.DateRange),
    Routines(R.string.destination_routines, Icons.Filled.Refresh),
    Todos(R.string.destination_todos, Icons.Filled.CheckCircle),
    Wishlist(R.string.destination_wishlist, Icons.Filled.Star),
    Notes(R.string.destination_notes, Icons.Filled.Edit),
}
