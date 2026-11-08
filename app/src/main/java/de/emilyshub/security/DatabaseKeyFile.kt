package de.emilyshub.security

import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKey

/**
 * The random 256-bit database key, stored in [file] only sealed with [wrappingKey] (the Android Keystore key in the app)
 * by [KeyWrap].
 */
class DatabaseKeyFile(private val file: File, private val wrappingKey: SecretKey) {

    /** Fails when an existing file cannot be decrypted: a new key would lock the database for good. */
    fun readOrCreate(): ByteArray =
        if (file.exists()) KeyWrap.unwrap(file.readBytes(), wrappingKey, KEY_BYTES, name = "database key file") else create()

    private fun create(): ByteArray {
        val key = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        writeDurably(file, KeyWrap.wrap(key, wrappingKey))
        return key
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}
