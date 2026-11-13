package de.emilyshub.ui.todos

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.todos.MoveTo
import de.emilyshub.ui.MoreMenu
import de.emilyshub.ui.MoreMenuItem
import de.emilyshub.ui.fields.DateDialog
import java.time.LocalDate

/**
 * The menu of the to-do [title], on Heute and in the To-dos tab (SPEC.md §1, Menü je Eintrag), open while [expanded]
 * ([onExpandedChange]): while it is open, to move it ([onMove]) tomorrow, to the weekend, next week, all from [today],
 * or to a day picked from today on, the one it is [due] on picked at first; and to open it ([onOpen]). One [done]
 * only opens.
 */
@Composable
fun TodoMenu(
    title: String,
    done: Boolean,
    due: LocalDate?,
    today: LocalDate,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onMove: (LocalDate) -> Unit,
    onOpen: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    MoreMenu(title, expanded, onExpandedChange) { close ->
        if (!done) {
            MoveTo.entries.forEach { choice ->
                MoreMenuItem(stringResource(choice.label), close) { onMove(choice.from(today)) }
            }
            MoreMenuItem(stringResource(R.string.move_pick), close) { picking = true }
        }
        MoreMenuItem(stringResource(R.string.entry_open), close, onOpen)
    }
    if (picking) {
        DateDialog(
            initial = due?.takeIf { !it.isBefore(today) } ?: today,
            earliest = today,
            onConfirm = { date ->
                picking = false
                onMove(date)
            },
            onDismiss = { picking = false },
        )
    }
}

/** How the menu names it. */
private val MoveTo.label: Int
    @StringRes get() = when (this) {
        MoveTo.Tomorrow -> R.string.move_tomorrow
        MoveTo.Weekend -> R.string.move_weekend
        MoveTo.NextWeek -> R.string.move_next_week
    }
