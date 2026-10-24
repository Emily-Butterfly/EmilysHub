package de.emilyshub.places

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** The WLAN the test says the device is connected to; none to begin with. */
class FakeWifi : CurrentWifi {
    private val current = MutableStateFlow<String?>(null)

    var ssid: String?
        get() = current.value
        set(value) {
            current.value = value
        }

    override fun ssid(): Flow<String?> = current
}
