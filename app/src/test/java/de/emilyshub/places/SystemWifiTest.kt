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

/** The WLAN as Android's connectivity service reports it, to a callback that asks for its name (SPEC.md §4). */
@RunWith(AndroidJUnit4::class)
class SystemWifiTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @Test
    fun `the WLAN connected to is reported by its name, one with its name kept back as such, and none once it is lost`() =
        runBlocking {
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

    /** A WLAN network, with its name, or with the name kept back as Android does without the location. */
    private fun wifi(named: String?): NetworkCapabilities {
        val info = WifiInfo.Builder().apply { if (named != null) setSsid(named.toByteArray()) }.build()
        return ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            shadowOf(it).setTransportInfo(info)
        }
    }
}
