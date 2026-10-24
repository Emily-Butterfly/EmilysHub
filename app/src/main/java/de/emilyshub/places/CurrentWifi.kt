package de.emilyshub.places

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The WLAN the device is connected to, as far as Android tells. */
sealed interface Wifi {
    /** No WLAN connected. */
    data object None : Wifi

    /**
     * A WLAN connected whose name Android keeps back: it tells it only to apps allowed to see the precise location,
     * and only while the location is on.
     */
    data object NameHidden : Wifi

    data class Named(val ssid: String) : Wifi
}

/** The WLAN the device is connected to (SPEC.md §4). */
interface CurrentWifi {
    /** The WLAN connected to, as it changes. */
    fun current(): Flow<Wifi>
}

/** Asks Android's own connectivity service, without Google Play Services (SPEC.md §4). */
class SystemWifi(private val context: Context) : CurrentWifi {
    override fun current(): Flow<Wifi> = callbackFlow {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        // Every WLAN network Android reports; usually one.
        val networks = mutableMapOf<Network, Wifi>()
        fun report() {
            trySend(networks.values.firstOrNull { it is Wifi.Named } ?: networks.values.firstOrNull() ?: Wifi.None)
        }
        val callback = wifiCallback(
            changed = { network, capabilities ->
                networks[network] = Ssid.of(reportedSsid(capabilities))?.let(Wifi::Named) ?: Wifi.NameHidden
                report()
            },
            lost = { network ->
                networks.remove(network)
                report()
            },
        )
        // A WLAN connected already is reported to the callback right away; only without one is there nothing to wait for.
        val active = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        if (active?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) report()
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

/** Passes on what Android reports of WLAN networks. */
private class WifiCallback : ConnectivityManager.NetworkCallback {
    private val changed: (Network, NetworkCapabilities) -> Unit
    private val lost: (Network) -> Unit

    /** From Android 12 on, WLAN names come only to a callback that asks for them with [flags]. */
    @RequiresApi(Build.VERSION_CODES.S)
    constructor(flags: Int, changed: (Network, NetworkCapabilities) -> Unit, lost: (Network) -> Unit) : super(flags) {
        this.changed = changed
        this.lost = lost
    }

    constructor(changed: (Network, NetworkCapabilities) -> Unit, lost: (Network) -> Unit) : super() {
        this.changed = changed
        this.lost = lost
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = changed(network, capabilities)

    override fun onLost(network: Network) = lost(network)
}

private fun wifiCallback(changed: (Network, NetworkCapabilities) -> Unit, lost: (Network) -> Unit): WifiCallback =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        WifiCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO, changed, lost)
    } else {
        WifiCallback(changed, lost)
    }
