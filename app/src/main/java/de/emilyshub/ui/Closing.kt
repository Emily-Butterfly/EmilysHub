package de.emilyshub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * How an editor saves or deletes and leaves: the work runs to its end, though the editor is gone meanwhile, and only
 * the first time, so that a second tap in the same frame neither does it again nor leaves twice.
 */
class Closing internal constructor(private val scope: CoroutineScope, private val onClose: () -> Unit) {
    private var started = false

    /** Does [work] and closes the editor at once. */
    operator fun invoke(work: suspend () -> Unit) {
        if (start(work)) onClose()
    }

    /** Does [work], which leaves the editor itself once it is done, another way than closing it. */
    fun leaveThrough(work: suspend () -> Unit) {
        start(work)
    }

    private fun start(work: suspend () -> Unit): Boolean {
        if (started) return false
        started = true
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
