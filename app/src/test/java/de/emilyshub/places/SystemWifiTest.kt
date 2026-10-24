package de.emilyshub.places

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/** The WLAN as Android's connectivity service reports it, to a callback that asks for its name (SPEC.md §4). */
@RunWith(AndroidJUnit4::class)
class SystemWifiTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @Test
    fun `the WLAN connected to is reported by its name, one with its name kept back as such, and none once it is lost`() =
        runBlocking {
            // Robolectric starts out with a WLAN not connected, which Android itself would not list.
            shadowOf(connectivity).clearAllNetworks()
            val seen = mutableListOf<Wifi>()
            val collecting = launch(Dispatchers.Unconfined) { SystemWifi(context).current().collect { seen += it } }
            val callback = shadowOf(connectivity).networkCallbacks.single()
            val network = ShadowNetwork.newInstance(1)

            callback.onCapabilitiesChanged(network, wifi(named = "FritzBox 7590"))
            callback.onCapabilitiesChanged(network, wifi(named = null))
            callback.onLost(network)
            collecting.cancel()

            assertEquals(listOf(Wifi.None, Wifi.Named("FritzBox 7590"), Wifi.NameHidden, Wifi.None), seen)
        }

    @Test
    fun `a WLAN joined that is not yet the network in use is waited for, not taken for none`() = runBlocking {
        val network = ShadowNetwork.newInstance(2)
        shadowOf(connectivity).addNetwork(network, ShadowNetworkInfo.newInstance(null, ConnectivityManager.TYPE_WIFI, 0, true, true))
        shadowOf(connectivity).setNetworkCapabilities(network, wifi(named = "FritzBox"))
        val seen = mutableListOf<Wifi>()
        val collecting = launch(Dispatchers.Unconfined) { SystemWifi(context).current().collect { seen += it } }

        shadowOf(connectivity).networkCallbacks.single().onCapabilitiesChanged(network, wifi(named = "FritzBox"))
        collecting.cancel()

        assertEquals(listOf(Wifi.Named("FritzBox")), seen)
    }

    /** A WLAN network, with its name, or with the name kept back as Android does without the location. */
    private fun wifi(named: String?): NetworkCapabilities {
        val info = WifiInfo.Builder().apply { if (named != null) setSsid(named.toByteArray()) }.build()
        return ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(it).setTransportInfo(info)
        }
    }
}
