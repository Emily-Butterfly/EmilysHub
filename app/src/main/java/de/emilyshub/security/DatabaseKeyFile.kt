package de.emilyshub.security

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
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
        // Synced to disk under a temporary name and only then moved into place, so neither a crash nor
        // a power cut can leave an empty or half-written key behind; that would lock the database for good.
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(KeyWrap.wrap(key, wrappingKey))
            out.fd.sync()
        }
        Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE)
        return key
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}
