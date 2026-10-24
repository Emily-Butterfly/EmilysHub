package de.emilyshub.places

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The WLAN the device is connected to (SPEC.md §4). */
interface CurrentWifi {
    /**
     * Its name (SSID) as it changes; none without a WLAN, or while Android keeps the name back: it tells it only to
     * apps allowed to see the precise location, and only while the location is on.
     */
    fun ssid(): Flow<String?>
}

/** Asks Android's own connectivity service, without Google Play Services (SPEC.md §4). */
class SystemWifi(private val context: Context) : CurrentWifi {
    override fun ssid(): Flow<String?> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        // Every WLAN network Android reports, with its name; usually one.
        val networks = mutableMapOf<Network, String?>()
        fun report() {
            trySend(networks.values.firstOrNull { it != null })
        }
        val onChange = { network: Network, capabilities: NetworkCapabilities ->
            networks[network] = ssidOf(reportedSsid(capabilities))
            report()
        }
        val onLost = { network: Network ->
            networks.remove(network)
            report()
        }
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // From Android 12 on, the name comes only to a callback that asks for it.
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                    onChange(network, capabilities)

                override fun onLost(network: Network) = onLost(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                    onChange(network, capabilities)

                override fun onLost(network: Network) = onLost(network)
            }
        }
        report()
        val wifi = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(wifi, callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun reportedSsid(capabilities: NetworkCapabilities): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (capabilities.transportInfo as? WifiInfo)?.ssid
        } else {
            // Android 11 leaves the name out of the network's capabilities, but still tells it here.
            @Suppress("DEPRECATION")
            context.getSystemService(WifiManager::class.java).connectionInfo?.ssid
        }
}

/**
 * The WLAN name as Android reports it, without the quotes around it; none while Android keeps it back. A name that
 * is no UTF-8 comes as hex digits without quotes, and is taken as it is.
 */
fun ssidOf(reported: String?): String? = when {
    reported == null || reported == UNKNOWN_SSID -> null
    reported.length >= 2 && reported.startsWith('"') && reported.endsWith('"') ->
        reported.substring(1, reported.length - 1).ifEmpty { null }
    else -> reported.ifEmpty { null }
}

/** What Android says for a name it keeps back, as `WifiManager.UNKNOWN_SSID`. */
private const val UNKNOWN_SSID = "<unknown ssid>"
