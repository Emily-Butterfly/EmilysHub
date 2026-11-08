package de.emilyshub.doku

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import de.emilyshub.R
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The fingerprint as the Doku uses it (SPEC.md §14): a Keystore key that needs a strong fingerprint for every use, set
 * free by the system's prompt. The phone's PIN, pattern or password never set it free.
 */
interface Fingerprint {
    /** Whether a strong fingerprint is set up on the phone. */
    fun isEnrolled(): Boolean

    /** A cipher to seal with, under a new key in place of any old one, for [authenticate] to set free. */
    fun sealingCipher(): Cipher

    /**
     * A cipher to open what was sealed with [iv], for [authenticate] to set free; null once the key is no longer valid,
     * as when a new fingerprint was set up on the phone, or gone.
     */
    fun openingCipher(iv: ByteArray): Cipher?

    fun deleteKey()

    /**
     * Shows the prompt over [activity]; [onSuccess] with [cipher] usable once a finger was recognised. Its second
     * button says "Passwort" when [toUnlock], else "Abbrechen", and leads to [onOther]. Dismissed otherwise, by back or as
     * the app leaves the screen, it leads nowhere: the lock screen asks again on return, or offers to.
     */
    fun authenticate(activity: Activity, cipher: Cipher, toUnlock: Boolean, onSuccess: (Cipher) -> Unit, onOther: () -> Unit)
}

/** The platform's: from Android 11 (the app's minSdk) BiometricPrompt takes a cipher to set free, without the androidx library. */
class SystemFingerprint(private val context: Context) : Fingerprint {

    override fun isEnrolled() =
        context.getSystemService(BiometricManager::class.java).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    override fun sealingCipher(): Cipher {
        deleteKey()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            // A strong fingerprint for every single use; never the screen lock.
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(spec) }.generateKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    override fun openingCipher(iv: ByteArray): Cipher? {
        val key = keyStore().getKey(ALIAS, null) as SecretKey? ?: return null
        return try {
            Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        }
    }

    override fun deleteKey() {
        keyStore().deleteEntry(ALIAS)
    }

    override fun authenticate(activity: Activity, cipher: Cipher, toUnlock: Boolean, onSuccess: (Cipher) -> Unit, onOther: () -> Unit) {
        val other = activity.getString(if (toUnlock) R.string.doku_password else R.string.cancel)
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(if (toUnlock) R.string.doku_fingerprint_unlock_title else R.string.doku_fingerprint_enable_title))
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButton(other, activity.mainExecutor) { _, _ -> onOther() }
            .build()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                onSuccess(checkNotNull(result.cryptoObject?.cipher))

        }
        prompt.authenticate(BiometricPrompt.CryptoObject(cipher), CancellationSignal(), activity.mainExecutor, callback)
    }

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "doku-fingerprint-key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
