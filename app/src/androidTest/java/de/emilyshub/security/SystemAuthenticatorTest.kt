package de.emilyshub.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemAuthenticatorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the app counts as unlockable exactly when the system prompt has something to ask for`() {
        // Also fails with a SecurityException if the manifest lacks USE_BIOMETRIC.
        val promptReady = context.getSystemService(BiometricManager::class.java)
            .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

        assertEquals(promptReady, SystemAuthenticator(context).canAuthenticate())
    }
}
