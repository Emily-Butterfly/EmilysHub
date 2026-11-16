package de.emilyshub.island

import de.emilyshub.backup.BackupKey
import java.io.IOException

/**
 * The island as a backup holds it (SPEC.md §10): its database as it lies on disk, encrypted by SQLCipher, with its
 * write-ahead log if there is one, its own [files] as they lie, encrypted ([IslandFileStore]), and the database key
 * sealed under the island password ([keys]), never under the fingerprint; nothing of it decrypted. [schema] is the version of its database the app that made it knew: the file itself
 * may be older, as only opening the island brings it up to date, which its migrations then do once it is put back.
 */
class IslandSnapshot internal constructor(
    val schema: Int,
    internal val keys: IslandKeys,
    val database: ByteArray,
    val wal: ByteArray?,
    val files: List<IslandFile> = emptyList(),
) {
    /** The keys as the key file holds them. */
    val sealedKeys: ByteArray get() = keys.toBytes()

    /** Whether [password] is the island password the key is sealed under; as slow as unlocking. */
    fun opensWith(password: String): Boolean = keys.openWithPassword(password)?.also { it.fill(0) } != null

    companion object {
        /**
         * The largest database and log of an island a backup takes along: 64 MB, little enough that a backup, which
         * holds them in memory, is still made. Its own files, the photos of Tracking's events (#133), count apart: a
         * backup reads them one by one.
         */
        const val MAX_BYTES = 64 * 1024 * 1024

        /**
         * The snapshot of these parts, as a backup holds them; null if they are not one: the keys damaged, sealed under
         * the fingerprint, or with more iterations than the app would ever set.
         */
        fun of(schema: Int, sealedKeys: ByteArray, database: ByteArray, wal: ByteArray?, files: List<IslandFile> = emptyList()): IslandSnapshot? {
            val keys = try {
                IslandKeys.fromBytes(sealedKeys)
            } catch (_: IllegalStateException) {
                return null
            } catch (_: IOException) {
                return null
            }
            if (keys.hasFingerprint || keys.fingerprintLost || keys.iterations !in BackupKey.ACCEPTED_ITERATIONS || schema < 1) return null
            if (files.map { it.name }.toSet().size != files.size) return null
            return IslandSnapshot(schema, keys, database, wal, files)
        }
    }
}
