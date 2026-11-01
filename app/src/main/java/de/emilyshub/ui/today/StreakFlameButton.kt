package de.emilyshub.ui.today

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.emilyshub.R
import de.emilyshub.stats.StreakFlame
import de.emilyshub.ui.theme.FlameOrange

/**
 * The flame in the top bar of Heute (SPEC.md §1): the days of the overall streak of the statistics ([flame]), grey
 * while today is still open, lit once it counts; tapping it opens the statistics ([onOpenStatistics]). It follows
 * what is done, undone, skipped, dropped or missed, and the change of day, as Heute does.
 */
@Composable
fun StreakFlameButton(flame: StreakFlame, onOpenStatistics: () -> Unit) {
    val look = if (flame.todayOpen) {
        FlameLook(R.plurals.today_streak_open, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlameLook(R.plurals.today_streak, FlameOrange, MaterialTheme.colorScheme.onSurface)
    }
    val description = pluralStringResource(look.description, flame.days, flame.days)
    TextButton(
        onClick = onOpenStatistics,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Icon(painterResource(R.drawable.ic_flame), contentDescription = null, tint = look.flame)
        Text(
            text = flame.days.toString(),
            color = look.number,
            modifier = Modifier
                .padding(start = 4.dp)
                .clearAndSetSemantics {},
        )
    }
}

/** How the flame looks, grey or lit: what TalkBack reads, the flame's color and the number's. */
private class FlameLook(@PluralsRes val description: Int, val flame: Color, val number: Color)
