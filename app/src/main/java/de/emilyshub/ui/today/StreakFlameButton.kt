package de.emilyshub.ui.today

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.routines.Routines
import de.emilyshub.stats.RoutineHistory
import de.emilyshub.stats.flame
import de.emilyshub.ui.theme.FlameOrange

/**
 * The flame in the top bar of Heute (SPEC.md §1): the days of the overall streak of the statistics, grey while today is
 * still open, lit once it counts; tapping it opens the statistics ([onOpenStatistics]). None without a streak. It follows
 * what is done, undone, skipped, dropped or missed as the occurrences change, and the change of day.
 */
@Composable
fun StreakFlameButton(routines: Routines, onOpenStatistics: () -> Unit) {
    val today = rememberToday(routines)
    val history by remember(routines) { routines.history() }.collectAsState(initial = null)
    val flame = remember(history, today) { history?.let { RoutineHistory(it).flame(today) } } ?: return
    val description = pluralStringResource(
        if (flame.todayOpen) R.plurals.today_streak_open else R.plurals.today_streak,
        flame.days,
        flame.days,
    )
    val color = if (flame.todayOpen) MaterialTheme.colorScheme.onSurfaceVariant else FlameOrange
    TextButton(
        onClick = onOpenStatistics,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_flame), contentDescription = null, tint = color)
        Text(
            text = flame.days.toString(),
            color = if (flame.todayOpen) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = 4.dp)
                .clearAndSetSemantics {},
        )
    }
}
