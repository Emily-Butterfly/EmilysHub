package de.emilyshub.doku

import android.app.Activity
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stands in for the Keystore key that needs a fingerprint and for its prompt: a software key, and a prompt that waits
 * for the test to [succeed] or tap its second button ([chooseOther]). Like a fresh emulator, no fingerprint is set up
 * until a test says so ([enrolled]); [enrollNew] sets up another, which makes the key invalid.
 */
class FakeFingerprint : Fingerprint {
    var enrolled = false

    /** How often the app asked for the prompt, and whether the last one was to unlock. */
    var prompts = 0
        private set
    var lastToUnlock: Boolean? = null
        private set

    private var key: SecretKey? = null
    private var invalidated = false
    private var pending: Pair<Cipher, (Cipher) -> Unit>? = null
    private var other: (() -> Unit)? = null

    val isPromptOpen: Boolean get() = pending != null
    val hasKey: Boolean get() = key != null

    override fun isEnrolled() = enrolled

    override fun sealingCipher(): Cipher {
        val new = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        key = new
        invalidated = false
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, new) }
    }

    override fun openingCipher(iv: ByteArray): Cipher? {
        val current = key?.takeUnless { invalidated } ?: return null
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, current, GCMParameterSpec(128, iv)) }
    }

    override fun deleteKey() {
        key = null
    }

    override fun authenticate(activity: Activity, cipher: Cipher, toUnlock: Boolean, onSuccess: (Cipher) -> Unit, onOther: () -> Unit) {
        prompts++
        lastToUnlock = toUnlock
        pending = cipher to onSuccess
        other = onOther
    }

    /** The user puts a finger on the sensor. */
    fun succeed() {
        val (cipher, onSuccess) = checkNotNull(pending) { "No prompt is open" }
        pending = null
        other = null
        onSuccess(cipher)
    }

    /** The user taps the prompt's second button, "Passwort" or "Abbrechen". */
    fun chooseOther() {
        val onOther = checkNotNull(other) { "No prompt is open" }
        pending = null
        other = null
        onOther()
    }

    /** Another fingerprint is set up on the phone: the key is no longer valid. */
    fun enrollNew() {
        enrolled = true
        invalidated = true
    }
}
