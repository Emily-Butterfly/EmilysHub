package de.emilyshub.security

import android.app.Activity

/** Stands in for the system's fingerprint/PIN prompt. Like a fresh emulator, the phone has no screen lock until a test sets one. */
class FakeAuthenticator : DeviceAuthenticator {
    var deviceSecure = false

    /** How often the app asked for the prompt. */
    var prompts = 0
        private set

    private var onSuccess: (() -> Unit)? = null

    override fun canAuthenticate() = deviceSecure

    override fun authenticate(activity: Activity, onSuccess: () -> Unit) {
        prompts++
        this.onSuccess = onSuccess
    }

    /** The user puts a finger on the sensor or enters the PIN. */
    fun succeed() {
        checkNotNull(onSuccess) { "No prompt is open" }.invoke()
        onSuccess = null
    }
}
