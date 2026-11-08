package de.emilyshub.doku

import de.emilyshub.backup.BackupKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * What is kept of the Doku's database key (SPEC.md §14): never the key itself, only sealed with AES-GCM, once under a
 * key derived from the Doku password ([byPassword], with its [salt] and [iterations]) and, with the fingerprint on,
 * once under the Keystore key that needs a fingerprint for each use ([byFingerprint]). Each sealed key is laid out as
 * the 12-byte IV, the ciphertext and the tag.
 */
internal class DokuKeys(
    private val salt: ByteArray,
    private val iterations: Int,
    private val byPassword: ByteArray,
    private val byFingerprint: ByteArray?,
) {
    val hasFingerprint: Boolean get() = byFingerprint != null

    /** The IV the fingerprint's key sealed with, to set its cipher up before the prompt; none with the fingerprint off. */
    val fingerprintIv: ByteArray? get() = byFingerprint?.copyOf(IV_BYTES)

    /** The database key, or null if [password] is not the Doku password. */
    fun openWithPassword(password: String): ByteArray? {
        val cipher = passwordCipher(Cipher.DECRYPT_MODE, password, salt, iterations, byPassword.copyOf(IV_BYTES))
        return try {
            cipher.doFinal(byPassword, IV_BYTES, byPassword.size - IV_BYTES).also(::checkSize)
        } catch (_: AEADBadTagException) {
            null
        }
    }

    /** The database key, opened with [cipher], which the fingerprint has unlocked for [fingerprintIv]. */
    fun openWithFingerprint(cipher: Cipher): ByteArray {
        val sealed = checkNotNull(byFingerprint) { "The fingerprint is off" }
        return try {
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES).also(::checkSize)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("The Doku key cannot be opened with the fingerprint", e)
        }
    }

    /** The same database [key], sealed anew under a new [password]; the fingerprint's stays as it is. */
    fun withPassword(key: ByteArray, password: String, iterations: Int = this.iterations): DokuKeys =
        sealedWithPassword(key, password, iterations, byFingerprint)

    /** With the database [key] also sealed by [cipher], which the fingerprint has unlocked for sealing. */
    fun withFingerprint(key: ByteArray, cipher: Cipher): DokuKeys =
        DokuKeys(salt, iterations, byPassword, cipher.iv + cipher.doFinal(key))

    fun withoutFingerprint(): DokuKeys = DokuKeys(salt, iterations, byPassword, null)

    fun toBytes(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT)
            out.writeInt(iterations)
            out.writeBlock(salt)
            out.writeBlock(byPassword)
            out.writeBoolean(byFingerprint != null)
            byFingerprint?.let { out.writeBlock(it) }
        }
    }.toByteArray()

    companion object {
        const val KEY_BYTES = 32
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val SEALED_BYTES = IV_BYTES + KEY_BYTES + TAG_BITS / 8
        private const val FORMAT = 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** A new random database key. */
        fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)

        /** The database [key] sealed under [password], without the fingerprint. */
        fun sealed(key: ByteArray, password: String, iterations: Int): DokuKeys = sealedWithPassword(key, password, iterations, null)

        fun fromBytes(bytes: ByteArray): DokuKeys = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == FORMAT) { "The Doku key file has an unknown format" }
            val iterations = input.readInt()
            val salt = input.readBlock()
            val byPassword = input.readBlock().also { check(it.size == SEALED_BYTES) { "The Doku key file is damaged" } }
            val byFingerprint = if (input.readBoolean()) input.readBlock() else null
            check(byFingerprint == null || byFingerprint.size == SEALED_BYTES) { "The Doku key file is damaged" }
            DokuKeys(salt, iterations, byPassword, byFingerprint)
        }

        private fun sealedWithPassword(key: ByteArray, password: String, iterations: Int, byFingerprint: ByteArray?): DokuKeys {
            require(key.size == KEY_BYTES) { "The Doku key must be $KEY_BYTES bytes" }
            val salt = ByteArray(BackupKey.SALT_BYTES).also(SecureRandom()::nextBytes)
            val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
            val cipher = passwordCipher(Cipher.ENCRYPT_MODE, password, salt, iterations, iv)
            return DokuKeys(salt, iterations, iv + cipher.doFinal(key), byFingerprint)
        }

        /** AES-GCM under the key PBKDF2 derives from [password], as for the backups' ([BackupKey]); the derived key is wiped once set. */
        private fun passwordCipher(mode: Int, password: String, salt: ByteArray, iterations: Int, iv: ByteArray): Cipher {
            val secret = BackupKey.derive(password, salt, iterations).secret
            try {
                return Cipher.getInstance(TRANSFORMATION).apply {
                    init(mode, SecretKeySpec(secret, "AES"), GCMParameterSpec(TAG_BITS, iv))
                }
            } finally {
                secret.fill(0)
            }
        }

        private fun checkSize(key: ByteArray) = check(key.size == KEY_BYTES) { "The Doku key is damaged" }

        private fun DataOutputStream.writeBlock(block: ByteArray) {
            writeInt(block.size)
            write(block)
        }

        private fun DataInputStream.readBlock(): ByteArray {
            val size = readInt()
            check(size in 0..MAX_BLOCK) { "The Doku key file is damaged" }
            return ByteArray(size).also(::readFully)
        }

        private const val MAX_BLOCK = 256
    }
}
