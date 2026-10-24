package de.emilyshub.places

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The WLAN the test says the device is connected to; none to begin with. */
class FakeWifi : CurrentWifi {
    private val wifi = MutableStateFlow<Wifi>(Wifi.None)

    var connected: Wifi
        get() = wifi.value
        set(value) {
            wifi.value = value
        }

    /** Connected to the WLAN named so, or to none. */
    var ssid: String?
        get() = (connected as? Wifi.Named)?.ssid
        set(value) {
            connected = value?.let(Wifi::Named) ?: Wifi.None
        }

    override fun current(): Flow<Wifi> = wifi
}
