package de.emilyshub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * The time now as [now] tells it, kept current while the screen shows: just after each full minute, and on return to
 * the app, as the waits stand still while the phone sleeps. For what changes as time goes by, such as a to-do turning
 * overdue.
 */
@Composable
fun rememberNow(now: () -> Instant): Instant {
    val currentNow by rememberUpdatedState(now)
    var time by remember { mutableStateOf(currentNow()) }
    LifecycleResumeEffect(Unit) {
        time = currentNow()
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MINUTE_MILLIS - Math.floorMod(currentNow().toEpochMilli(), MINUTE_MILLIS) + MARGIN_MILLIS)
            time = currentNow()
        }
    }
    return time
}

private const val MINUTE_MILLIS = 60_000L

/** The wait may end a little early, so it waits a little longer to be sure the minute has turned. */
private const val MARGIN_MILLIS = 1_000L
