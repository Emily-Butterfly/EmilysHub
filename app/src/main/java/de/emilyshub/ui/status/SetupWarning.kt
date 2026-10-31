package de.emilyshub.ui.status

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.routines.Routines
import de.emilyshub.status.Standing
import de.emilyshub.todos.Todos
import de.emilyshub.ui.WarningCard
import de.emilyshub.ui.enumeration
import de.emilyshub.ui.rememberChecked

/**
 * The warning on Heute (SPEC.md §11) while something the reminders of the active [routines] and open [todos] need from
 * Android is lacking, read again on every return to the app; [onCheck] opens "Funktioniert alles?". Nothing while all
 * is there.
 */
@Composable
fun SetupWarning(routines: Routines, todos: Todos, onCheck: () -> Unit) {
    val readiness = rememberChecked { readiness() }
    val use = rememberUse(routines, todos) ?: return
    val lacking = readiness.current.lacking(use)
    if (lacking.isEmpty()) return
    val names = lacking.map { need ->
        val texts = need.texts
        stringResource(if (readiness.current.standing(need) == Standing.Off) texts.offTitle else texts.title)
    }
    WarningCard(
        title = stringResource(R.string.setup_warning_title),
        text = stringResource(R.string.setup_warning_lacking, enumeration(names)),
        action = stringResource(R.string.setup_warning_check),
        onAction = onCheck,
    )
}
