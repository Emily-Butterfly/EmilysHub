package de.emilyshub.security

import android.app.KeyguardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemAuthenticatorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the app can be unlocked exactly when the phone has a screen lock`() {
        // A fingerprint can only be set up together with a screen lock, so the two always agree.
        // Also fails with a SecurityException if the manifest lacks USE_BIOMETRIC.
        val deviceSecure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

        assertEquals(deviceSecure, SystemAuthenticator(context).canAuthenticate())
    }
}
