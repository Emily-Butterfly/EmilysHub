package de.emilyshub.ui.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.emilyshub.island.Island

/**
 * The trips out of an island to the camera app or the folder picker ([Island.goingOut]), for the screen that sends the
 * user there: [start] just before, [end] with the result, which Android delivers before the activity is back in front.
 * Back in front without it, the island locks, as the user came back some other way; and the trip ends as the screen goes
 * away. While it is under way, the screen going off locks the island, as it would were the island shown.
 */
class IslandTrip internal constructor(private val island: Island<*>, private val context: Context) {
    private var trip: AutoCloseable? = null
    private var paused = false
    private var back = false

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            island.lock()
            end()
        }
    }

    /** A trip begins: just before the other app is launched. */
    fun start() {
        end()
        trip = island.goingOut()
        paused = false
        back = false
        ContextCompat.registerReceiver(context, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** The result is here, the user back by the way they were sent: the trip [end]s once what came back is kept. */
    fun returned() {
        back = true
    }

    /** The trip is over, if one was under way: with the result, or the user back otherwise. */
    fun end() {
        val ending = trip ?: return
        trip = null
        runCatching { context.unregisterReceiver(screenOff) }
        ending.close()
    }

    internal fun onLifecycle(event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> paused = true
            // Back in front without the other app's result, which comes before: not by the way it was sent, but by
            // the launcher or the recent apps, say, after the app was in the background meanwhile. It locks then.
            Lifecycle.Event.ON_RESUME -> if (paused && trip != null && !back) {
                island.lock()
                end()
            }
            else -> Unit
        }
    }
}

/** The trips out of [island] for this screen ([IslandTrip]). */
@Composable
fun rememberIslandTrip(island: Island<*>): IslandTrip {
    val context = LocalContext.current.applicationContext
    val trip = remember(island) { IslandTrip(island, context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(trip, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> trip.onLifecycle(event) }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            trip.end()
        }
    }
    return trip
}
