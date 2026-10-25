package de.emilyshub.security

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A secret key sealed with AES-GCM under a wrapping key, such as one of the Android Keystore, to be stored where the
 * secret itself must not be. Layout: 12-byte IV, ciphertext, tag.
 */
object KeyWrap {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun wrap(secret: ByteArray, wrappingKey: SecretKey): ByteArray {
        // The cipher picks a fresh random IV; the Android Keystore does not accept one from outside.
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
        return cipher.iv + cipher.doFinal(secret)
    }

    /** The secret of [secretBytes] bytes sealed in [wrapped]; fails if it is damaged or was sealed under another key. */
    fun unwrap(wrapped: ByteArray, wrappingKey: SecretKey, secretBytes: Int): ByteArray {
        check(wrapped.size == IV_BYTES + secretBytes + TAG_BITS / 8) { "The wrapped key is damaged" }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES))
            }
            cipher.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("The wrapped key cannot be decrypted", e)
        }
    }
}
