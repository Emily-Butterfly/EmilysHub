package de.emilyshub.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Keys held by the Android Keystore: usable on this device, but their material can never be read or exported. */
object KeystoreKeys {
    private const val PROVIDER = "AndroidKeyStore"

    /**
     * The AES-256-GCM key stored under [alias], created on first use. It needs no fingerprint or
     * unlocked screen, because reminders read the database while the phone is locked.
     */
    @Synchronized
    fun getOrCreateAesGcmKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as SecretKey?)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
    }
}
