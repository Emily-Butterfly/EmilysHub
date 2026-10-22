package de.emilyshub.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import de.emilyshub.R

/** Unlocks the app with what the phone offers: a fingerprint, or else the screen lock's PIN, pattern or password. */
interface DeviceAuthenticator {
    /** Whether a fingerprint or a screen lock is set up, so that [authenticate] has something to ask for. */
    fun canAuthenticate(): Boolean

    /** Shows the system's prompt over [activity]; [onSuccess] runs only once the user has been recognised. */
    fun authenticate(activity: Activity, onSuccess: () -> Unit)
}

/**
 * The platform's BiometricPrompt. From Android 11 (the app's minSdk) it offers a strong fingerprint
 * and the screen lock in one prompt, without the androidx library.
 */
class SystemAuthenticator(private val context: Context) : DeviceAuthenticator {

    // A screen lock is all the prompt needs, and a fingerprint can only be set up on top of one.
    // Asking for exactly this keeps other states, such as a busy fingerprint sensor, from opening the app.
    override fun canAuthenticate() = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    override fun authenticate(activity: Activity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.app_lock_prompt_title))
            .setAllowedAuthenticators(ALLOWED)
            .build()
        // A dismissed or failed prompt leaves the app locked; the lock screen offers to try again.
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        }
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, callback)
    }

    private companion object {
        const val ALLOWED = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }
}
