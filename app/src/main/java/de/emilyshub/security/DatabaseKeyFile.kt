package de.emilyshub.security

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The random 256-bit database key, stored in [file] only after AES-GCM encryption with
 * [wrappingKey] (the Android Keystore key in the app). File layout: 12-byte IV, ciphertext, tag.
 */
class DatabaseKeyFile(private val file: File, private val wrappingKey: SecretKey) {

    /** Fails when an existing file cannot be decrypted: a new key would lock the database for good. */
    fun readOrCreate(): ByteArray = if (file.exists()) unwrap(file.readBytes()) else create()

    private fun create(): ByteArray {
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        // Written to a temporary file and moved into place, so a crash never leaves half a key behind.
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(wrap(key))
        Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE)
        return key
    }

    private fun wrap(key: ByteArray): ByteArray {
        // The cipher picks a fresh random IV; the Android Keystore does not accept one from outside.
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
        return cipher.iv + cipher.doFinal(key)
    }

    private fun unwrap(stored: ByteArray): ByteArray {
        check(stored.size == IV_BYTES + KEY_BYTES + TAG_BITS / 8) { "The database key file is damaged" }
        val iv = stored.copyOfRange(0, IV_BYTES)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(TAG_BITS, iv))
            }
            cipher.doFinal(stored, IV_BYTES, stored.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("The database key file cannot be decrypted", e)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
