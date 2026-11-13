package de.emilyshub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * How an editor saves or deletes and leaves: the work runs to its end, though the editor is gone meanwhile, and only
 * the first time, so that a second tap in the same frame neither does it again nor leaves twice.
 */
class Closing internal constructor(private val scope: CoroutineScope, private val onClose: () -> Unit) {
    /** Whether the work has begun: the editor is on its way out and is not left another way meanwhile. */
    var begun = false
        private set

    /** Does [work] and closes the editor at once. */
    operator fun invoke(work: suspend () -> Unit) {
        if (start(work)) onClose()
    }

    /** Does [work], which leaves the editor itself once it is done, another way than closing it. */
    fun leaveThrough(work: suspend () -> Unit) {
        start(work)
    }

    private fun start(work: suspend () -> Unit): Boolean {
        if (begun) return false
        begun = true
        scope.launchNow { withContext(NonCancellable) { work() } }
        return true
    }
}

/** The editor's [Closing], which closes it with [onClose]. */
@Composable
fun rememberClosing(onClose: () -> Unit): Closing {
    val scope = rememberCoroutineScope()
    val currentOnClose by rememberUpdatedState(onClose)
    return remember(scope) { Closing(scope) { currentOnClose() } }
}

/** Whether [draft] differs from [saved] as both would be saved ([cleaned]): spaces at the edges are no change. */
fun <D> differsAsSaved(draft: D, saved: D, cleaned: (D) -> D): Boolean = cleaned(draft) != cleaned(saved)

/**
 * How an editor is left by its back arrow, the back key or the back gesture (SPEC.md §8): with its draft [changed] at that
 * moment, which may be before the editor shows the last keystroke, it asks "Änderungen speichern?" first, else it leaves
 * at once by [onDiscard]. "Speichern" does [onSave], as the editor's button does, which leaves it if the draft can be
 * saved and else shows why at its field; "Verwerfen" does [onDiscard]; "Weiter bearbeiten" and a tap beside the question
 * leave all as it is. An editor that saves itself (SPEC.md §8: notes and sessions) leaves a changed draft by
 * [leavesItself] instead, which saves it if it can and then says true; only if it says false is there the question.
 * Once the editor's [closing] has begun, as while a save is still under way, back does nothing: the
 * editor is on its way out already. The question stays through rotation and the lock. Returns what the back arrow does;
 * back and the gesture do the same by themselves. An editor covered by another is not shown, so it does not ask.
 */
@Composable
fun rememberLeaving(
    closing: Closing,
    changed: () -> Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    leavesItself: () -> Boolean = { false },
): () -> Unit {
    var asking by rememberSaveable { mutableStateOf(false) }
    val hasChanges by rememberUpdatedState(changed)
    val save by rememberUpdatedState(onSave)
    val discard by rememberUpdatedState(onDiscard)
    val leaveItself by rememberUpdatedState(leavesItself)
    val leave = remember(closing) {
        {
            when {
                closing.begun -> Unit
                hasChanges() -> if (!leaveItself()) asking = true
                else -> discard()
            }
        }
    }
    BackHandler(onBack = leave)
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.leaving_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        save()
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        asking = false
                        if (!closing.begun) discard()
                    },
                ) { Text(stringResource(R.string.leaving_discard)) }
                TextButton(onClick = { asking = false }) { Text(stringResource(R.string.leaving_keep_editing)) }
            },
        )
    }
    return leave
}
