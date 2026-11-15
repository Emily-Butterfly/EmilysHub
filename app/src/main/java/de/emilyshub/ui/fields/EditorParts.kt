package de.emilyshub.ui.fields

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.emilyshub.R

/**
 * "Speichern" of an editor (SPEC.md §8): a filled button across the width at the bottom, right above the keyboard while
 * it shows, in the same place in every editor. As the editor's bottom bar, the content scrolls up past it.
 */
@Composable
fun SaveBar(onSave: () -> Unit, enabled: Boolean = true) {
    Surface(tonalElevation = 3.dp) {
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
        ) {
            // One line even at a large font size.
            Text(stringResource(R.string.save), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Brings what keeps an editor from being saved into view (SPEC.md §8): once saving fails, the first [Problem] shown, by
 * the order they come in, scrolls itself to where it is seen, unless the editor's first field took it already. Problems
 * shown as the editor opens stay where they are. One for each screen, by [LocalProblemReveal].
 */
class ProblemReveal {
    /** How often saving failed; each time, one problem comes into view. */
    internal var tries by mutableIntStateOf(0)
        private set

    // The try whose problem came into view already.
    private var revealed = 0

    /** Saving failed; [elsewhere], its problem is shown already, as by focusing the field it is at. */
    fun saveFailed(elsewhere: Boolean = false) {
        tries++
        if (elsewhere) revealed = tries
    }

    /** Whether the problem asking is the first to come into view for this try, which it then is. */
    internal fun claim(): Boolean = (tries > revealed).also { if (it) revealed = tries }
}

/** The [ProblemReveal] of the screen shown, none where problems stay where they are, as on the Doku's pages. */
val LocalProblemReveal = staticCompositionLocalOf<ProblemReveal?> { null }

/** The problem it is on comes into view as saving fails, if it is the first to; see [ProblemReveal]. */
@Composable
internal fun Modifier.revealedOnFailedSave(): Modifier {
    val reveal = LocalProblemReveal.current ?: return this
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(reveal.tries) { if (reveal.claim()) requester.bringIntoView() }
    return bringIntoViewRequester(requester)
}

/**
 * The field to type in first in an editor (SPEC.md §8): for a new entry focused once as it opens, so that the keyboard
 * shows there; for one saved not at all. Focused again when saving fails at it ([saveFailed]).
 */
class FirstField internal constructor(val requester: FocusRequester, private val reveal: ProblemReveal?) {
    /** Focuses the field, which scrolls it into view; whether it could, which it cannot before it is shown. */
    fun focus(): Boolean = runCatching { requester.requestFocus() }.isSuccess

    /** Saving failed: [atField], it focuses the field; otherwise the first problem shown comes into view. */
    fun saveFailed(atField: Boolean) {
        val focused = atField && focus()
        reveal?.saveFailed(elsewhere = focused)
    }
}

/** The editor's [FirstField], focused as it opens if it is [new]. */
@Composable
fun rememberFirstField(new: Boolean): FirstField {
    val reveal = LocalProblemReveal.current
    val field = remember(reveal) { FirstField(FocusRequester(), reveal) }
    // Once only, not again after rotation or the lock.
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!new || focused) return@LaunchedEffect
        // The field comes in a frame or two later, as the screen around it is laid out.
        repeat(FRAMES_TO_WAIT) {
            if (field.focus()) {
                focused = true
                return@LaunchedEffect
            }
            withFrameNanos {}
        }
    }
    return field
}

/** How many frames the first field may take to come in before it is given up. */
private const val FRAMES_TO_WAIT = 10
