package de.emilyshub.backup

import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The key of the backups (SPEC.md §10): the [secret] derived from the backup password with PBKDF2-HMAC-SHA256, its
 * [salt] and [iterations]; all that is kept of the password. From the secret come the AES-256 key a backup is encrypted
 * with, and a check value that tells a wrong password from a damaged file.
 */
class BackupKey(val salt: ByteArray, val iterations: Int, internal val secret: ByteArray) {

    internal fun encryptionKey(): SecretKey = SecretKeySpec(expand("encryption"), "AES")

    internal fun passwordCheck(): ByteArray = expand("password check").copyOf(CHECK_BYTES)

    /**
     * One HMAC-SHA256 of the secret per purpose: PBKDF2 asked for more than 32 bytes would cost the attacker only a part
     * of what it costs here.
     */
    private fun expand(purpose: String): ByteArray = Mac.getInstance(MAC).run {
        init(SecretKeySpec(secret, MAC))
        doFinal("EmilysHub backup $purpose".toByteArray())
    }

    companion object {
        /** As OWASP recommends for PBKDF2-HMAC-SHA256. */
        const val ITERATIONS = 600_000
        const val SALT_BYTES = 16
        const val CHECK_BYTES = 16
        const val SECRET_BYTES = 32
        private const val MAC = "HmacSHA256"

        /**
         * The key of [password], with a new random [salt] unless one is given. The password counts in its composed form,
         * so that "ü" typed as "u" and an umlaut gives the same key.
         */
        fun derive(password: String, salt: ByteArray = randomSalt(), iterations: Int = ITERATIONS): BackupKey {
            val chars = Normalizer.normalize(password, Normalizer.Form.NFC).toCharArray()
            val spec = PBEKeySpec(chars, salt, iterations, SECRET_BYTES * Byte.SIZE_BITS)
            try {
                val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
                return BackupKey(salt, iterations, secret)
            } finally {
                spec.clearPassword()
                chars.fill(' ')
            }
        }

        private fun randomSalt() = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
    }
}
