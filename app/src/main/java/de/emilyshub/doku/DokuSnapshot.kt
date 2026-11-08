package de.emilyshub.doku

import java.io.IOException

/**
 * The Doku as a backup holds it (SPEC.md §10): `doku.db` as it lies on disk, encrypted by SQLCipher, with its
 * write-ahead log if there is one, and the database key sealed under the Doku password ([keys]), never under the
 * fingerprint; nothing of it decrypted. [schema] is the version of `doku.db` the app that made it knew: the file itself
 * may be older, as only opening the Doku brings it up to date, which its migrations then do once it is put back.
 */
class DokuSnapshot internal constructor(
    val schema: Int,
    internal val keys: DokuKeys,
    val database: ByteArray,
    val wal: ByteArray?,
) {
    /** The keys as the key file holds them. */
    val sealedKeys: ByteArray get() = keys.toBytes()

    /** Whether [password] is the Doku password the key is sealed under; as slow as unlocking. */
    fun opensWith(password: String): Boolean = keys.openWithPassword(password)?.also { it.fill(0) } != null

    companion object {
        /** Not so many that checking the password could take minutes, as with the backups. */
        private val ACCEPTED_ITERATIONS = 1_000..5_000_000

        /**
         * The snapshot of these parts, as a backup holds them; null if they are not one: the keys damaged, sealed under
         * the fingerprint, or with more iterations than the app would ever set.
         */
        fun of(schema: Int, sealedKeys: ByteArray, database: ByteArray, wal: ByteArray?): DokuSnapshot? {
            val keys = try {
                DokuKeys.fromBytes(sealedKeys)
            } catch (_: IllegalStateException) {
                return null
            } catch (_: IOException) {
                return null
            }
            if (keys.hasFingerprint || keys.fingerprintLost || keys.iterations !in ACCEPTED_ITERATIONS || schema < 1) return null
            return DokuSnapshot(schema, keys, database, wal)
        }
    }
}
