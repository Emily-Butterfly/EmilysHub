package de.emilyshub.ui.fields

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.places.Places
import de.emilyshub.reminders.RemindingStyle
import de.emilyshub.reminders.Tone
import de.emilyshub.routines.Priority
import de.emilyshub.wishes.Wishes

/**
 * "Mehr Optionen" of a long editor (SPEC.md §8): what an entry seldom needs, folded away below what it always does.
 * Folded, the row names what of it differs from the default, the [set] ones, or with none what can be set there,
 * [available]. Whether it is open is the editor's to keep, across rotation and the lock.
 */
@Composable
fun MoreOptions(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    set: List<String>,
    available: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = expanded, role = Role.Switch, onValueChange = onExpandedChange)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.more_options),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (!expanded) {
                    Text(
                        set.takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: available,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (set.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                // The row says it is open or folded, as a switch does.
                contentDescription = null,
            )
        }
        if (expanded) content()
    }
}

/*
 * What of the settings under "Mehr Optionen" differs from the default, one line each for its summary; none where it is
 * as a new entry has it.
 */

/** "Hohe Priorität" or "Niedrige Priorität". */
@Composable
fun priorityOption(priority: Priority): String? = when (priority) {
    Priority.Normal -> null
    Priority.High -> stringResource(R.string.priority_high_note)
    Priority.Low -> stringResource(R.string.priority_low_note)
}

/** "2 Tags". */
@Composable
fun tagsOption(tagIds: Set<Long>): String? =
    tagIds.size.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.option_tags, it, it) }

/** "3 Unterpunkte". */
@Composable
fun checklistOption(items: Int): String? =
    items.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.option_checklist, it, it) }

/** "Ort: Gym", as a to-do's row says it; one deleted meanwhile is left out. */
@Composable
fun placesOption(places: Places, placeIds: Set<Long>): String? {
    val all by remember(places) { places.all() }.collectAsState(initial = emptyList())
    val names = all.filter { it.id in placeIds }.map { it.name }
    return LocalResources.current.placesLabel(names)
}

/** "Ziel: Fit werden"; one deleted meanwhile is left out. */
@Composable
fun goalOption(wishes: Wishes, goalId: Long?): String? {
    if (goalId == null) return null
    val choices by remember(wishes) { wishes.goalChoices() }.collectAsState(initial = emptyList())
    return choices.find { it.id == goalId }?.let { stringResource(R.string.option_goal, it.title) }
}

/** "Vollbild-Alarm", "Name auf dem Sperrbildschirm" and the tone, if not the standard. */
@Composable
fun styleOptions(style: RemindingStyle): List<String> = listOfNotNull(
    stringResource(R.string.option_full_screen).takeIf { style.fullScreen },
    stringResource(R.string.option_name_on_lock_screen).takeIf { style.nameOnLockScreen },
    when (style.tone) {
        Tone.Standard -> null
        Tone.Silent -> stringResource(R.string.option_tone_silent)
        is Tone.Of -> stringResource(R.string.option_tone_own)
    },
)
