package de.emilyshub.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/** Says [message] in the bar, with [undoLabel] to [undo] what it says, which runs if tapped before the bar goes. */
suspend fun SnackbarHostState.offerUndo(message: String, undoLabel: String, undo: suspend () -> Unit) {
    if (showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo()
}
