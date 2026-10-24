package de.emilyshub.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** Where the device is by its position, for the GPS places (SPEC.md §4). */
interface CurrentLocation {
    /** Where Android says the device is now; none while it cannot tell, as without the location allowed or turned on. */
    suspend fun position(): Position?
}

/**
 * Asks Android's own location service, without Google Play Services (SPEC.md §4): a position it found in the last
 * [RECENT] does, as when it has just told of entering an area; otherwise it is asked for one now, for up to [wait].
 */
class SystemLocation(private val context: Context, private val wait: Duration = Duration.ofSeconds(8)) : CurrentLocation {

    override suspend fun position(): Position? {
        val allowed = LOCATION.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!allowed) return null
        val manager = context.getSystemService(LocationManager::class.java)
        return try {
            (recent(manager) ?: current(manager))?.toPosition()
        } catch (_: SecurityException) {
            // Taken back just now.
            null
        }
    }

    /** The most accurate position any of Android's ways found in the last [RECENT]. */
    @SuppressLint("MissingPermission")
    private fun recent(manager: LocationManager): Location? {
        val now = SystemClock.elapsedRealtimeNanos()
        return manager.getProviders(true)
            .mapNotNull(manager::getLastKnownLocation)
            .filter { Duration.ofNanos(now - it.elapsedRealtimeNanos) <= RECENT }
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
    }

    /** The position now, by the best way Android has turned on; none if it finds none within [wait]. */
    @SuppressLint("MissingPermission")
    private suspend fun current(manager: LocationManager): Location? {
        val enabled = manager.getProviders(true)
        val provider = PREFERRED.firstOrNull { it in enabled } ?: return null
        return withTimeoutOrNull(wait.toMillis()) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, DIRECTLY) { continuation.resume(it) }
            }
        }
    }

    private fun Location.toPosition() = Position(Coordinates(latitude, longitude), if (hasAccuracy()) accuracy.toDouble() else 0.0)

    private companion object {
        val LOCATION = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        val RECENT: Duration = Duration.ofMinutes(2)

        /**
         * Android's ways to a position, the best first: combining the others where the device has it, then by the
         * networks around, which is quick, then by satellite, which can take long indoors.
         */
        val PREFERRED = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)

        /** Hands on what Android tells on the thread it tells it on: it only resumes the waiting. */
        val DIRECTLY = Executor { it.run() }
    }
}
