package de.emilyshub.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Why a backup cannot be opened or made. */
enum class BackupProblem { NotABackup, Damaged, WrongPassword, TooLarge, NewerVersion, OlderVersion }

class BackupException(val problem: BackupProblem, cause: Throwable? = null) : Exception("Backup: $problem", cause)

internal fun damaged(): Nothing = throw BackupException(BackupProblem.Damaged)

/** What a backup file holds, decrypted, and the key it was made with, derived from the password it was opened with. */
class DecryptedBackup(val plaintext: ByteArray, val key: BackupKey)

/**
 * A backup file (SPEC.md §10): a header, then the content encrypted with AES-256-GCM, which authenticates the header too.
 * The header: "EMHB", the format version (1 byte), the PBKDF2 iterations (4 bytes), the salt (16), the password check
 * (16), the nonce (12). A file is no larger than [MAX_BYTES].
 */
object BackupFile {
    const val MAX_BYTES = 20 * 1024 * 1024
    private const val FORMAT = 1
    private val MAGIC = "EMHB".toByteArray()
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 1 + 4 + BackupKey.SALT_BYTES + BackupKey.CHECK_BYTES + NONCE_BYTES

    /** Far beyond any the app sets, but not so far that opening a file could take minutes. */
    private val ACCEPTED_ITERATIONS = 1_000..5_000_000

    fun seal(plaintext: ByteArray, key: BackupKey): ByteArray {
        // Not to be read back, so not to be written either.
        if (HEADER_BYTES + plaintext.size + TAG_BITS / 8 > MAX_BYTES) throw BackupException(BackupProblem.TooLarge)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).put(FORMAT.toByte()).putInt(key.iterations).put(key.salt).put(key.passwordCheck()).put(nonce)
            .array()
        return header + cipher(Cipher.ENCRYPT_MODE, key, nonce, header).doFinal(plaintext)
    }

    fun open(file: ByteArray, password: String): DecryptedBackup {
        if (file.size < MAGIC.size || !file.copyOf(MAGIC.size).contentEquals(MAGIC)) throw BackupException(BackupProblem.NotABackup)
        val format = file.getOrNull(MAGIC.size)?.toUByte()?.toInt()
        if (format != null && format > FORMAT) throw BackupException(BackupProblem.NewerVersion)
        if (format != FORMAT || file.size < HEADER_BYTES + TAG_BITS / 8) damaged()
        val header = ByteBuffer.wrap(file, MAGIC.size + 1, HEADER_BYTES - MAGIC.size - 1)
        val iterations = header.int
        if (iterations !in ACCEPTED_ITERATIONS) damaged()
        val salt = ByteArray(BackupKey.SALT_BYTES).also(header::get)
        val check = ByteArray(BackupKey.CHECK_BYTES).also(header::get)
        val nonce = ByteArray(NONCE_BYTES).also(header::get)

        val key = BackupKey.derive(password, salt, iterations)
        if (!MessageDigest.isEqual(key.passwordCheck(), check)) throw BackupException(BackupProblem.WrongPassword)
        val plaintext = try {
            cipher(Cipher.DECRYPT_MODE, key, nonce, file.copyOf(HEADER_BYTES)).doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupProblem.Damaged, e)
        }
        return DecryptedBackup(plaintext, key)
    }

    /** All of [input], unless it is larger than a backup can be. */
    fun read(input: InputStream): ByteArray {
        val all = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return all.toByteArray()
            all.write(buffer, 0, read)
            if (all.size() > MAX_BYTES) throw BackupException(BackupProblem.TooLarge)
        }
    }

    private fun cipher(mode: Int, key: BackupKey, nonce: ByteArray, header: ByteArray) =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key.encryptionKey(), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
}
