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
    fun getOrCreateAesGcmKey(alias: String): SecretKey = get(alias) ?: create(aesGcm(alias).build())

    /**
     * A new AES-256-GCM key under [alias], in place of any old one, that needs a strong fingerprint for every single use,
     * never the screen lock, and is no longer valid once another fingerprint is set up on the phone.
     */
    @Synchronized
    fun createFingerprintKey(alias: String): SecretKey {
        delete(alias)
        val spec = aesGcm(alias)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        return create(spec)
    }

    /** The key under [alias], if there is one. */
    fun get(alias: String): SecretKey? = keyStore().getKey(alias, null) as SecretKey?

    fun delete(alias: String) = keyStore().deleteEntry(alias)

    private fun aesGcm(alias: String) =
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)

    private fun create(spec: KeyGenParameterSpec): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }
}
