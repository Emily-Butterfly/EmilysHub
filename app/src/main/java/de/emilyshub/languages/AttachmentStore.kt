package de.emilyshub.languages

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the attachments of the lessons lie (SPEC.md §9, §15): each in a file of its own in [folder], only ever encrypted
 * with AES-GCM under [key], bound to its name so that one cannot stand in for another. Layout: 12-byte IV, ciphertext,
 * tag. Nothing is written in plain text, not even for a moment. Blocking: call it off the main thread.
 */
class AttachmentStore(private val folder: () -> File, private val key: () -> SecretKey) {

    /** Keeps [plain] encrypted under a new name, which it returns. */
    fun write(plain: ByteArray): String {
        val name = UUID.randomUUID().toString() + SUFFIX
        write(name, plain)
        return name
    }

    /** Keeps [plain] encrypted under [name], in place of what was there. */
    fun write(name: String, plain: ByteArray) {
        require(isName(name)) { "Not an attachment's name: $name" }
        val dir = folder().apply { mkdirs() }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, ByteArray(IV_BYTES).also(random::nextBytes)))
            updateAAD(name.toByteArray())
        }
        val sealed = cipher.iv + cipher.doFinal(plain)
        val temp = File(dir, name + TEMP)
        FileOutputStream(temp).use { out ->
            out.write(sealed)
            out.fd.sync()
        }
        Files.move(temp.toPath(), File(dir, name).toPath(), ATOMIC_MOVE)
    }

    /** What is kept under [name], decrypted; none if there is nothing, or it is damaged or not of this key. */
    fun read(name: String): ByteArray? {
        if (!isName(name)) return null
        val file = File(folder(), name)
        if (!file.isFile) return null
        val sealed = file.readBytes()
        if (sealed.size < IV_BYTES + TAG_BITS / 8) return null
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
                updateAAD(name.toByteArray())
                doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
            }
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    /** Whether something is kept under [name]. */
    fun has(name: String) = isName(name) && File(folder(), name).isFile

    /** When what is kept under [name] was written, in milliseconds since 1970; 0 if there is nothing. */
    fun modified(name: String): Long = if (isName(name)) File(folder(), name).lastModified() else 0

    /** Deletes what is kept under [name], if anything. */
    fun delete(name: String) {
        if (isName(name)) File(folder(), name).delete()
    }

    /** The names of all that is kept, and deletes what a write broke off left behind: call it only while none is written. */
    fun names(): Set<String> {
        val files = folder().listFiles().orEmpty()
        files.filter { it.name.endsWith(TEMP) }.forEach(File::delete)
        return files.map { it.name }.filter(::isName).toSet()
    }

    /** Takes what [other], under the same key, keeps under [name] in place of what is kept here under it. */
    fun moveFrom(other: AttachmentStore, name: String) {
        require(isName(name)) { "Not an attachment's name: $name" }
        val dir = folder().apply { mkdirs() }
        Files.move(File(other.folder(), name).toPath(), File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING, ATOMIC_MOVE)
    }

    /** Deletes all that is kept but under [kept]. */
    fun keepOnly(kept: Set<String>) {
        (names() - kept).forEach(::delete)
    }

    /** A store under the same key in a folder of its own beside this one's, for what is kept apart from it. */
    fun besideIt(): AttachmentStore = AttachmentStore({ File(folder().path + "-aside") }, key)

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val SUFFIX = ".att"
        private const val TEMP = ".tmp"
        private val NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.att")
        private val random = SecureRandom()

        /** Whether [name] is one the store gives, and no path elsewhere. */
        fun isName(name: String) = NAME.matches(name)
    }
}
