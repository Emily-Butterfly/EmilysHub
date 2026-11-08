package de.emilyshub.doku

import android.app.Activity
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume

/**
 * Stands in for the Keystore key that needs a fingerprint and for its prompt: a software key, and a prompt that waits
 * for the test to [succeed], tap its second button ([chooseOther]) or [dismiss] it. Like a fresh emulator, no
 * fingerprint is set up until a test says so ([enrolled]); [enrollNew] sets up another, which makes the key invalid.
 */
class FakeFingerprint : Fingerprint {
    var enrolled = false

    /** How often the app asked for the prompt, and what the last one was for. */
    var prompts = 0
        private set
    var lastPurpose: FingerprintPurpose? = null
        private set

    private var key: SecretKey? = null
    private var invalidated = false
    private var pending: Pair<Cipher, CancellableContinuation<PromptResult>>? = null

    val isPromptOpen: Boolean get() = pending?.second?.isActive == true
    val hasKey: Boolean get() = key != null

    override fun isEnrolled() = enrolled

    override fun sealingCipher(): Cipher {
        val new = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        key = new
        invalidated = false
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, new) }
    }

    override fun openingCipher(wrapped: ByteArray): Cipher? {
        val current = key?.takeUnless { invalidated } ?: return null
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, current, GCMParameterSpec(128, wrapped, 0, 12))
        }
    }

    override fun deleteKey() {
        key = null
    }

    override suspend fun prompt(activity: Activity, cipher: Cipher, purpose: FingerprintPurpose): PromptResult {
        prompts++
        lastPurpose = purpose
        return suspendCancellableCoroutine { pending = cipher to it }
    }

    /** The user puts a finger on the sensor. */
    fun succeed() = end { PromptResult.Recognised(it) }

    /** As [succeed], with the cipher [changed] first, as though the key had changed under it. */
    fun succeedWith(changed: (Cipher) -> Unit) = end { PromptResult.Recognised(it.also(changed)) }

    /** The user taps the prompt's second button, "Passwort" or "Abbrechen". */
    fun chooseOther() = end { PromptResult.Other }

    /** The prompt goes otherwise, by back, say. */
    fun dismiss() = end { PromptResult.Dismissed }

    /** Another fingerprint is set up on the phone: the key is no longer valid. */
    fun enrollNew() {
        enrolled = true
        invalidated = true
    }

    private fun end(result: (Cipher) -> PromptResult) {
        val (cipher, continuation) = checkNotNull(pending?.takeIf { it.second.isActive }) { "No prompt is open" }
        pending = null
        continuation.resume(result(cipher))
    }
}
