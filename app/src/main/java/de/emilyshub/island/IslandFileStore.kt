package de.emilyshub.island

import de.emilyshub.security.writeDurably
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * An island's own files beside its database, such as the photos of Tracking's events (#133): each encrypted under a key
 * derived from the island's database key, so readable only while it is open, and named by chance. Read and written only
 * within [IslandStore.withFiles] or [IslandStore.changingWithFiles], so that a backup sees them as the database does.
 */
interface IslandFileStore {
    /** A name no file has yet. */
    fun newName(): String

    /** Keeps [bytes] encrypted as the file [name], in place of one of that name. */
    fun write(name: String, bytes: ByteArray)

    /** The file [name], decrypted; none if there is none, or it does not decrypt. */
    fun read(name: String): ByteArray?

    /** Deletes the file [name]; while a backup reads the island's files, once it is done. */
    fun delete(name: String)
}

/** A file of an island as a backup carries it, encrypted as it lies: [name], [bytes] long, read from [at]. */
class IslandFile internal constructor(val name: String, val bytes: Long, internal val at: File) {
    /** Its bytes as they lie, encrypted. */
    fun read(): ByteArray = at.readBytes()
}

/**
 * The folder [dir] of an island's own files ([IslandFileStore]). While a backup reads them ([reading]), deleting one waits
 * until it is done, so that each file the backup listed is still there to be read.
 */
internal class IslandOwnFiles(val dir: File) {
    private var readers = 0
    private val deferred = mutableSetOf<String>()

    /** The files as they lie, by name. */
    @Synchronized
    fun list(): List<IslandFile> =
        dir.listFiles().orEmpty().filter { isName(it.name) }.sortedBy { it.name }.map { IslandFile(it.name, it.length(), it) }

    /** Runs [block] while the files listed stay; those deleted meanwhile are deleted after. */
    suspend fun <T> reading(block: suspend () -> T): T {
        synchronized(this) { readers++ }
        try {
            return block()
        } finally {
            val now = synchronized(this) {
                readers--
                if (readers > 0) emptyList() else deferred.toList().also { deferred.clear() }
            }
            now.forEach { File(dir, it).delete() }
        }
    }

    /** Deletes the file [name], or once no backup reads them. */
    @Synchronized
    fun delete(name: String) {
        if (readers > 0) deferred += name else File(dir, name).delete()
    }

    /** Deletes all files, or once no backup reads them. */
    @Synchronized
    fun deleteAll() {
        dir.listFiles().orEmpty().forEach { if (readers > 0 && isName(it.name)) deferred += it.name else it.delete() }
    }

    /** The store of these files under [databaseKey], the island's open database key. */
    fun store(databaseKey: ByteArray): IslandFileStore = Store(fileKey(databaseKey))

    private inner class Store(private val key: SecretKeySpec) : IslandFileStore {
        override fun newName(): String = ByteArray(NAME_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }

        override fun write(name: String, bytes: ByteArray) {
            require(isName(name)) { "Not a name of an island's file" }
            dir.mkdirs()
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            writeDurably(File(dir, name), nonce + cipher(Cipher.ENCRYPT_MODE, nonce, name).doFinal(bytes))
        }

        override fun read(name: String): ByteArray? {
            if (!isName(name)) return null
            val file = File(dir, name).takeIf { it.isFile } ?: return null
            val sealed = file.readBytes()
            if (sealed.size < NONCE_BYTES + TAG_BYTES) return null
            return try {
                cipher(Cipher.DECRYPT_MODE, sealed.copyOf(NONCE_BYTES), name).doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
            } catch (_: GeneralSecurityException) {
                null
            }
        }

        override fun delete(name: String) {
            if (isName(name)) this@IslandOwnFiles.delete(name)
        }

        /** Bound to [name], so that no file can stand in for another. */
        private fun cipher(mode: Int, nonce: ByteArray, name: String) = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(name.toByteArray())
        }
    }

    companion object {
        private const val NAME_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BYTES = 16
        private val random = SecureRandom()

        /** Whether [name] is one an island's file can have: 32 hexadecimal digits, as [IslandFileStore.newName] makes. */
        fun isName(name: String): Boolean = name.length == NAME_BYTES * 2 && name.all { it in '0'..'9' || it in 'a'..'f' }

        /** The files' key, derived from the database key, which is not used for anything else this way. */
        private fun fileKey(databaseKey: ByteArray): SecretKeySpec {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(databaseKey, "HmacSHA256")) }
            return SecretKeySpec(mac.doFinal("EmilysHub island files 1".toByteArray()), "AES")
        }
    }
}
