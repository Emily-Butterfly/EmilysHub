package de.emilyshub.reminders

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import de.emilyshub.places.Area

/**
 * Has Android tell the app on entering or leaving the GPS areas of places (SPEC.md §4), also while it is closed, so
 * that a reminder waiting for one pops up on arrival.
 */
interface AreaWatch {
    /** Watches [areas], and no others; none stops watching. */
    fun watch(areas: Set<Area>)
}

/**
 * Asks Android's own location service, without Google Play Services: a proximity alert for each area, all sent the
 * same way, as an arrival to the reminders, which then look where the device is. Android keeps them until they are
 * taken back or the device restarts; the app asks anew once it starts, taking back all those asked for before.
 */
class SystemAreaWatch(private val context: Context) : AreaWatch {
    private val manager = context.getSystemService(LocationManager::class.java)

    // What Android watches, as far as the app knows since it started. Asked anew only when that changes.
    private var watching: Set<Area>? = null

    @SuppressLint("MissingPermission")
    override fun watch(areas: Set<Area>) {
        if (areas == watching) return
        val operation = operation(context)
        watching = try {
            manager.removeProximityAlert(operation)
            areas.forEach { manager.addProximityAlert(it.latitude, it.longitude, it.radius.toFloat(), NO_EXPIRY, operation) }
            areas
        } catch (_: SecurityException) {
            // Without the location allowed Android watches none; what waits looks again at its follow-ups.
            null
        }
    }

    companion object {
        /** What Android sends on entering or leaving one of the areas. */
        fun operation(context: Context): PendingIntent {
            val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_ARRIVE)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getBroadcast(context, REQUEST, intent, flags)
        }

        /** Apart from the WLAN's ([ArrivalWatch]), so that taking back one leaves the other. */
        private const val REQUEST = 1

        private const val NO_EXPIRY = -1L
    }
}
