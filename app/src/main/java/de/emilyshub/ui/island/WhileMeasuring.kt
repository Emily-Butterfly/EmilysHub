package de.emilyshub.ui.island

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import de.emilyshub.island.Island

/**
 * The exception for the measuring assistant (SPEC.md §17), for as long as this is in the composition: the screen stays
 * on and [island]'s five minutes without a touch rest ([Island.measuring]). Leaving it, the app going to the
 * background and the screen going off lock it all the same. Used by the Stehtest and the event measurements (#127, #128).
 */
@Composable
fun WhileMeasuring(island: Island<*>) {
    val view = LocalView.current
    DisposableEffect(island, view) {
        val measuring = island.measuring()
        val wasKeptOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = wasKeptOn
            measuring.close()
        }
    }
}
