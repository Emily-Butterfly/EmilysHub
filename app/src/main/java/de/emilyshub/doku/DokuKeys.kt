package de.emilyshub.doku

import de.emilyshub.backup.BackupKey
import de.emilyshub.security.KeyWrap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * What is kept of the Doku's database key (SPEC.md §14): never the key itself, only sealed by [KeyWrap], once under a
 * key derived from the Doku password ([byPassword], with its [salt] and [iterations]) and, with the fingerprint on,
 * once under the Keystore key that needs a fingerprint for each use ([byFingerprint]). Once a new fingerprint on the
 * phone made that key invalid, the fingerprint is off and [fingerprintLost] says so, until it is offered again.
 */
internal class DokuKeys private constructor(
    private val salt: ByteArray,
    private val iterations: Int,
    private val byPassword: ByteArray,
    val byFingerprint: ByteArray?,
    val fingerprintLost: Boolean,
) {
    val hasFingerprint: Boolean get() = byFingerprint != null

    /** The database key, or null if [password] is not the Doku password. */
    fun openWithPassword(password: String): ByteArray? = try {
        KeyWrap.unwrap(byPassword, passwordKey(password, salt, iterations), KEY_BYTES, NAME)
    } catch (_: IllegalStateException) {
        null
    }

    /** The database key, opened with [cipher], which the fingerprint has set free for [byFingerprint]. */
    fun openWithFingerprint(cipher: Cipher): ByteArray =
        KeyWrap.unwrap(checkNotNull(byFingerprint) { "The fingerprint is off" }, cipher, KEY_BYTES, NAME)

    /** The same database [key], sealed anew under a new [password]; the fingerprint's stays as it is. */
    fun withPassword(key: ByteArray, password: String, iterations: Int): DokuKeys =
        sealedWithPassword(key, password, iterations, byFingerprint, fingerprintLost)

    /** With the database [key] also sealed by [cipher], which the fingerprint has set free for sealing. */
    fun withFingerprint(key: ByteArray, cipher: Cipher): DokuKeys =
        DokuKeys(salt, iterations, byPassword, KeyWrap.wrap(key, cipher), fingerprintLost = false)

    /** With the fingerprint off; [lost] when a new fingerprint on the phone turned it off, to offer it again. */
    fun withoutFingerprint(lost: Boolean = false): DokuKeys = DokuKeys(salt, iterations, byPassword, null, lost)

    /** As written to the key file ([fromBytes] reads it back). */
    fun toBytes(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(FORMAT)
            out.writeInt(iterations)
            out.writeBlock(salt)
            out.writeBlock(byPassword)
            out.writeByte(
                when {
                    byFingerprint != null -> FINGERPRINT_ON
                    fingerprintLost -> FINGERPRINT_LOST
                    else -> FINGERPRINT_OFF
                },
            )
            byFingerprint?.let { out.writeBlock(it) }
        }
    }.toByteArray()

    companion object {
        const val KEY_BYTES = 32
        private const val NAME = "Doku key"
        private const val FORMAT = 1
        private const val FINGERPRINT_OFF = 0
        private const val FINGERPRINT_ON = 1
        private const val FINGERPRINT_LOST = 2
        private const val MAX_SALT = 64

        /** A new random database key. */
        fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)

        /** The database [key] sealed under [password], without the fingerprint. */
        fun sealed(key: ByteArray, password: String, iterations: Int): DokuKeys =
            sealedWithPassword(key, password, iterations, byFingerprint = null, fingerprintLost = false)

        /** What [toBytes] wrote; fails on a file that is damaged or of an unknown format. */
        fun fromBytes(bytes: ByteArray): DokuKeys = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == FORMAT) { "The Doku key file has an unknown format" }
            val iterations = input.readInt()
            val salt = input.readBlock(MAX_SALT)
            val sealedSize = KeyWrap.wrappedSize(KEY_BYTES)
            val byPassword = input.readBlock(sealedSize).also { check(it.size == sealedSize) { "The Doku key file is damaged" } }
            val fingerprint = input.readUnsignedByte()
            check(fingerprint in FINGERPRINT_OFF..FINGERPRINT_LOST) { "The Doku key file is damaged" }
            val byFingerprint = if (fingerprint == FINGERPRINT_ON) input.readBlock(sealedSize) else null
            check(byFingerprint == null || byFingerprint.size == sealedSize) { "The Doku key file is damaged" }
            DokuKeys(salt, iterations, byPassword, byFingerprint, fingerprintLost = fingerprint == FINGERPRINT_LOST)
        }

        private fun sealedWithPassword(
            key: ByteArray,
            password: String,
            iterations: Int,
            byFingerprint: ByteArray?,
            fingerprintLost: Boolean,
        ): DokuKeys {
            require(key.size == KEY_BYTES) { "The Doku key must be $KEY_BYTES bytes" }
            val salt = ByteArray(BackupKey.SALT_BYTES).also(SecureRandom()::nextBytes)
            val sealed = KeyWrap.wrap(key, passwordKey(password, salt, iterations))
            return DokuKeys(salt, iterations, sealed, byFingerprint, fingerprintLost)
        }

        /** The AES key PBKDF2 derives from [password], as for the backups' ([BackupKey]); the derived bytes are wiped once set. */
        private fun passwordKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
            val secret = BackupKey.derive(password, salt, iterations).secret
            try {
                return SecretKeySpec(secret, "AES")
            } finally {
                secret.fill(0)
            }
        }

        private fun DataOutputStream.writeBlock(block: ByteArray) {
            writeInt(block.size)
            write(block)
        }

        private fun DataInputStream.readBlock(max: Int): ByteArray {
            val size = readInt()
            check(size in 0..max) { "The Doku key file is damaged" }
            return ByteArray(size).also(::readFully)
        }
    }
}
