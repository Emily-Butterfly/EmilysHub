package de.emilyshub.doku

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyPermanentlyInvalidatedException
import de.emilyshub.R
import de.emilyshub.security.KeyWrap
import de.emilyshub.security.KeystoreKeys
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

/** What the fingerprint's prompt is shown for: its title and second button differ. */
enum class FingerprintPurpose {
    /** To open the Doku; its second button says "Passwort". */
    Unlock,

    /** To turn the fingerprint on; its second button says "Abbrechen". */
    TurnOn,
}

/** How the fingerprint's prompt ended. */
sealed interface PromptResult {
    /** A finger was recognised, and [cipher] is now free to use. */
    class Recognised(val cipher: Cipher) : PromptResult

    /** Its second button was tapped: "Passwort" or "Abbrechen". */
    data object Other : PromptResult

    /** Gone otherwise, by back, too many tries, or as the app left the screen. */
    data object Dismissed : PromptResult
}

/**
 * The fingerprint as the Doku uses it (SPEC.md §14): a Keystore key that needs a strong fingerprint for every use, set
 * free by the system's prompt. The phone's PIN, pattern or password never set it free.
 */
interface Fingerprint {
    /** Whether a strong fingerprint is set up on the phone. */
    fun isEnrolled(): Boolean

    /** A cipher to seal with, under a new key in place of any old one, for [prompt] to set free. */
    fun sealingCipher(): Cipher

    /**
     * A cipher to open [wrapped], sealed by one from [sealingCipher], for [prompt] to set free; null once the key is no
     * longer valid, as when a new fingerprint was set up on the phone, or gone.
     */
    fun openingCipher(wrapped: ByteArray): Cipher?

    /** Removes the key, as when the fingerprint is turned off. */
    fun deleteKey()

    /** Shows the prompt over [activity] to set [cipher] free for [purpose]; cancelled, it closes the prompt. */
    suspend fun prompt(activity: Activity, cipher: Cipher, purpose: FingerprintPurpose): PromptResult
}

/** The platform's: from Android 11 (the app's minSdk) BiometricPrompt takes a cipher to set free, without the androidx library. */
class SystemFingerprint(private val context: Context) : Fingerprint {

    override fun isEnrolled() =
        context.getSystemService(BiometricManager::class.java).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    override fun sealingCipher(): Cipher = KeyWrap.sealingCipher(KeystoreKeys.createFingerprintKey(ALIAS))

    override fun openingCipher(wrapped: ByteArray): Cipher? {
        val key = KeystoreKeys.get(ALIAS) ?: return null
        return try {
            KeyWrap.openingCipher(wrapped, key)
        } catch (_: KeyPermanentlyInvalidatedException) {
            null
        }
    }

    override fun deleteKey() = KeystoreKeys.delete(ALIAS)

    override suspend fun prompt(activity: Activity, cipher: Cipher, purpose: FingerprintPurpose): PromptResult =
        suspendCancellableCoroutine { continuation ->
            val (title, other) = when (purpose) {
                FingerprintPurpose.Unlock -> R.string.doku_fingerprint_unlock_title to R.string.doku_password
                FingerprintPurpose.TurnOn -> R.string.doku_fingerprint_enable_title to R.string.cancel
            }
            // The first of the ways it ends counts; the others, as the error that follows a cancelled prompt, are ignored.
            val end = { result: PromptResult -> if (continuation.isActive) continuation.resume(result) }
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(activity.getString(title))
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButton(activity.getString(other), activity.mainExecutor) { _, _ -> end(PromptResult.Other) }
                .build()
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    end(PromptResult.Recognised(checkNotNull(result.cryptoObject?.cipher)))

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = end(PromptResult.Dismissed)
            }
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { activity.mainExecutor.execute(cancellation::cancel) }
            prompt.authenticate(BiometricPrompt.CryptoObject(cipher), cancellation, activity.mainExecutor, callback)
        }

    private companion object {
        const val ALIAS = "doku-fingerprint-key"
    }
}
