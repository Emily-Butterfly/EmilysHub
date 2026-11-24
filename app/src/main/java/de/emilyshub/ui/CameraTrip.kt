package de.emilyshub.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * What a screen does as the app sends the user out to another app and back: Tracking stays open meanwhile (ADR 0003,
 * [de.emilyshub.ui.island.IslandTrip]); elsewhere nothing ([NoTrip]).
 */
interface TripOut {
    /** Just before the other app is opened. */
    fun start()

    /** Its result is here, the user back the way they were sent. */
    fun returned()

    /** It is over: what came back is kept, or nothing came. */
    fun end()
}

/** A trip out that asks nothing of the screen. */
object NoTrip : TripOut {
    override fun start() = Unit

    override fun returned() = Unit

    override fun end() = Unit
}

/** The photo the camera app took, still in the one file lent to it ([CameraShot]); readable only within the trip. */
class TakenPhoto internal constructor(private val context: Context) {
    /** Its bytes; none if there are none, or more than [maxBytes]. */
    suspend fun read(maxBytes: Int): ByteArray? = withContext(Dispatchers.IO) { CameraShot.read(context, maxBytes) }
}

/** The way to the camera app for a screen ([rememberCameraTrip]). */
class CameraTrip internal constructor(private val launch: () -> Boolean) {
    /** Opens the camera app; false, with nothing left behind, if the phone has none. */
    fun take(): Boolean = launch()
}

/**
 * "Foto aufnehmen" (SPEC.md §15, §17, §18), the same in the Sessions, Tracking and Mein Warum: the camera app writes the
 * photo to the one file the app lends it, so that the app needs no camera permission. Taken, [onTaken] reads it; then
 * [trip] ends, and the file, the only plain-text copy, is deleted whatever came of it. Back without a photo, the file
 * is deleted and [onCancelled] told. A phone without a camera app leaves nothing behind, and [CameraTrip.take] says so.
 */
@Composable
fun rememberCameraTrip(trip: TripOut = NoTrip, onCancelled: () -> Unit = {}, onTaken: suspend (TakenPhoto) -> Unit): CameraTrip {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val taken by rememberUpdatedState(onTaken)
    val cancelled by rememberUpdatedState(onCancelled)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { done ->
        trip.returned()
        if (!done) {
            trip.end()
            CameraShot.clear(context)
            cancelled()
            return@rememberLauncherForActivityResult
        }
        scope.launchNow {
            try {
                taken(TakenPhoto(context))
            } finally {
                withContext(NonCancellable) {
                    // Kept first, then the trip ends, which locks Tracking if it took too long.
                    trip.end()
                    // It lay there in plain text only until read.
                    withContext(Dispatchers.IO) { CameraShot.clear(context) }
                }
            }
        }
    }
    return remember(launcher, trip) {
        CameraTrip {
            val uri = CameraShot.prepare(context)
            trip.start()
            try {
                launcher.launch(uri)
                true
            } catch (e: ActivityNotFoundException) {
                trip.end()
                CameraShot.clear(context)
                false
            }
        }
    }
}
