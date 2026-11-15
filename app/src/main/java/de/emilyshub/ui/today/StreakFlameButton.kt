package de.emilyshub.ui.today

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.stats.StreakFlame
import de.emilyshub.ui.theme.FlameOrange

/**
 * The flame in the top bar of Heute (SPEC.md §1): the days of the overall streak of the statistics ([flame]), grey
 * while today is still open, lit once it counts; without a streak a grey 0, which TalkBack tells with the last streak, if
 * there was one (#99). Tapping it opens the statistics ([onOpenStatistics]). It follows what is done, undone, skipped,
 * dropped or missed, and the change of day, as Heute does.
 */
@Composable
fun StreakFlameButton(flame: StreakFlame, onOpenStatistics: () -> Unit) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val (flameColor, numberColor) = if (flame.lit) FlameOrange to MaterialTheme.colorScheme.onSurface else grey to grey
    val description = when {
        flame.days > 0 && flame.todayOpen -> pluralStringResource(R.plurals.today_streak_open, flame.days, flame.days)
        flame.days > 0 -> pluralStringResource(R.plurals.today_streak, flame.days, flame.days)
        flame.last != null -> pluralStringResource(R.plurals.today_streak_none_last, flame.last.days, flame.last.days)
        else -> stringResource(R.string.today_streak_none)
    }
    TextButton(
        onClick = onOpenStatistics,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_flame), contentDescription = null, tint = flameColor)
        Text(
            text = flame.days.toString(),
            color = numberColor,
            modifier = Modifier
                .padding(start = 4.dp)
                .clearAndSetSemantics {},
        )
    }
}
