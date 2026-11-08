package de.emilyshub.security

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A secret key sealed with AES-GCM under a wrapping key, such as one of the Android Keystore, to be stored where the
 * secret itself must not be. Layout: 12-byte IV, ciphertext, tag.
 *
 * For a wrapping key that needs a fingerprint for each use, the cipher is set up first ([sealingCipher],
 * [openingCipher]), set free by the fingerprint's prompt, and only then used ([wrap], [unwrap] with a cipher).
 */
object KeyWrap {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    /** How many bytes a secret of [secretBytes] takes sealed. */
    fun wrappedSize(secretBytes: Int) = IV_BYTES + secretBytes + TAG_BITS / 8

    fun wrap(secret: ByteArray, wrappingKey: SecretKey): ByteArray = wrap(secret, sealingCipher(wrappingKey))

    /** Seals [secret] with [cipher], from [sealingCipher]. */
    fun wrap(secret: ByteArray, cipher: Cipher): ByteArray = cipher.iv + cipher.doFinal(secret)

    /** A cipher to seal with under [wrappingKey]; it picks a fresh random IV, as the Android Keystore takes none from outside. */
    fun sealingCipher(wrappingKey: SecretKey): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }

    /** A cipher to open [wrapped] under [wrappingKey]. */
    fun openingCipher(wrapped: ByteArray, wrappingKey: SecretKey): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(TAG_BITS, wrapped, 0, IV_BYTES))
        }

    /**
     * The secret of [secretBytes] bytes sealed in [wrapped]; fails if it is damaged or was sealed under another key,
     * saying what it is by its [name].
     */
    fun unwrap(wrapped: ByteArray, wrappingKey: SecretKey, secretBytes: Int, name: String): ByteArray {
        checkSize(wrapped, secretBytes, name)
        val cipher = try {
            openingCipher(wrapped, wrappingKey)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("The $name cannot be decrypted", e)
        }
        return unwrap(wrapped, cipher, secretBytes, name)
    }

    /** As [unwrap] above, with [cipher] from [openingCipher], already set up for [wrapped]. */
    fun unwrap(wrapped: ByteArray, cipher: Cipher, secretBytes: Int, name: String): ByteArray {
        checkSize(wrapped, secretBytes, name)
        return try {
            cipher.doFinal(wrapped, IV_BYTES, wrapped.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("The $name cannot be decrypted", e)
        }
    }

    private fun checkSize(wrapped: ByteArray, secretBytes: Int, name: String) =
        check(wrapped.size == wrappedSize(secretBytes)) { "The $name is damaged" }
}
