package de.emilyshub.security

import android.content.Context
import android.security.keystore.KeyInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.SecretKeyFactory

@RunWith(AndroidJUnit4::class)
class KeystoreKeysTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alias = "test-${UUID.randomUUID()}"
    private val keyFile = File(context.cacheDir, "$alias.key")

    @After
    fun cleanUp() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        keyFile.delete()
    }

    @Test
    fun `the wrapping key is a 256-bit AES key that never leaves the keystore`() {
        val key = KeystoreKeys.aesGcmKey(alias)

        assertNull("key material must not be exportable", key.encoded)
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertEquals(256, info.keySize)
    }

    @Test
    fun `a database key wrapped by the keystore can be read again after a restart`() {
        val created = DatabaseKeyFile(keyFile, KeystoreKeys.aesGcmKey(alias)).readOrCreate()

        val afterRestart = DatabaseKeyFile(keyFile, KeystoreKeys.aesGcmKey(alias)).readOrCreate()

        assertArrayEquals(created, afterRestart)
    }
}
