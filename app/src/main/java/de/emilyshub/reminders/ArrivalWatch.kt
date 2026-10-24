package de.emilyshub.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Has Android wake the app whenever the device joins a WLAN, also while the app is closed (SPEC.md §4), so that a
 * reminder waiting for a place pops up on arrival. Android keeps the request until it is taken back or the device
 * restarts; every update of the reminders, as after a restart, asks anew.
 */
class ArrivalWatch(private val context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** Watches for arrivals while [on], as long as a routine is bound to a place; stops otherwise. */
    fun watch(on: Boolean) {
        val operation = operation()
        // Taken back first, so that asking again does not add up.
        try {
            connectivity.unregisterNetworkCallback(operation)
        } catch (_: IllegalArgumentException) {
            // Not watched yet.
        }
        if (on) connectivity.registerNetworkCallback(WIFI, operation)
    }

    /** What Android sends on joining a WLAN: the arrival, to the reminders. */
    private fun operation(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_ARRIVE)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        val WIFI: NetworkRequest = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
    }
}
