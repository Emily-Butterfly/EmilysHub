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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * The field to type in first in an editor (SPEC.md §8): for a [new] entry focused once as it opens, so that the
 * keyboard shows there; for one saved not at all. Focused again with [show], as when saving finds a problem there.
 */
class FirstField internal constructor(val focus: FocusRequester) {
    /** Focuses the field, which scrolls it into view; nothing while it is not shown. */
    fun show() {
        // Not attached while its part of the editor does not show, as in a note's preview.
        runCatching { focus.requestFocus() }
    }
}

/** The editor's [FirstField], focused as it opens if it is [new]. */
@Composable
fun rememberFirstField(new: Boolean): FirstField {
    val field = remember { FirstField(FocusRequester()) }
    // Once only, not again after rotation or the lock.
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (new && !focused) {
            field.show()
            focused = true
        }
    }
    return field
}

/** Brings the element it is on into view as it comes up, as a problem shown once saving is tried. */
@Composable
internal fun Modifier.revealedWhenShown(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }
    return bringIntoViewRequester(requester)
}
