package de.emilyshub.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Why a backup cannot be opened or made. */
enum class BackupProblem { NotABackup, Damaged, WrongPassword, TooLarge, NewerVersion, OlderVersion, WrongDokuPassword }

class BackupException(val problem: BackupProblem, cause: Throwable? = null) : Exception("Backup: $problem", cause)

internal fun damaged(): Nothing = throw BackupException(BackupProblem.Damaged)

/** What a backup file holds, decrypted: its content, its [attachments] in order, and the key it was made with. */
class DecryptedBackup(val plaintext: ByteArray, val key: BackupKey, val attachments: List<ByteArray> = emptyList())

/**
 * A backup file (SPEC.md §10), encrypted with AES-256-GCM. It begins with a header: "EMHB", the format version (1
 * byte), the PBKDF2 iterations (4 bytes), the salt (16) and the password check (16).
 *
 * Format 2, which the app writes, follows it with records, each whether it is the last (1 byte), its length (4), its
 * nonce (12) and its sealed content, which authenticates the header, its place in the file and whether it is the last: so that none can be left
 * out, swapped or added. The first holds the content, at most [MAX_BYTES]; then come the attachments, one each, at most
 * [MAX_RECORD_BYTES]; the last is empty. Written and read as a stream, so that the attachments need not all fit in
 * memory at once.
 *
 * Format 1, of older backups, has the nonce in the header, then the content sealed as one, the whole file no larger than
 * [MAX_BYTES].
 */
object BackupFile {
    /** The largest content of a backup, and the largest backup of format 1. */
    const val MAX_BYTES = 20 * 1024 * 1024

    /** The largest attachment a backup carries: more than the largest the app takes. */
    const val MAX_RECORD_BYTES = 16 * 1024 * 1024

    private const val MORE = 0
    private const val LAST = 1
    private const val FORMAT_1 = 1
    private const val FORMAT = 2
    private val MAGIC = "EMHB".toByteArray()
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val KEY_HEADER_BYTES = 4 + 1 + 4 + BackupKey.SALT_BYTES + BackupKey.CHECK_BYTES
    private const val HEADER_1_BYTES = KEY_HEADER_BYTES + NONCE_BYTES
    private val random = SecureRandom()

    /** Writes a backup of format 2 to [output]: its [content] first, then each attachment in order, then the end. */
    class Writer internal constructor(private val output: DataOutputStream, private val key: BackupKey) {
        private val header = keyHeader(FORMAT, key)
        private var index = 0L
        private var finished = false

        init {
            output.write(header)
        }

        /** Adds the next attachment, after the content. */
        fun attachment(plaintext: ByteArray) {
            if (plaintext.size > MAX_RECORD_BYTES) throw BackupException(BackupProblem.TooLarge)
            record(plaintext, last = false)
        }

        /** Ends the backup: without this, it does not open. */
        fun finish() {
            record(ByteArray(0), last = true)
            output.flush()
            finished = true
        }

        internal fun record(plaintext: ByteArray, last: Boolean) {
            check(!finished) { "The backup is finished" }
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, nonce, aad(header, index++, last)).doFinal(plaintext)
            output.writeByte(if (last) LAST else MORE)
            output.writeInt(sealed.size)
            output.write(nonce)
            output.write(sealed)
        }
    }

    /** Reads a backup, of either format: its content first, then its attachments one by one, up to its end. */
    class Reader internal constructor(
        private val input: DataInputStream?,
        private val header: ByteArray,
        val key: BackupKey,
        private val content: ByteArray?,
    ) {
        private var index = 0L
        private var ended = false

        /** The content; read once, before any attachment. */
        fun content(): ByteArray {
            check(index == 0L) { "The content comes first" }
            if (content != null) {
                index++
                return content
            }
            if (readMarker() != MORE) damaged()
            return openRecord(MAX_BYTES, last = false)
        }

        /** The next attachment; none once the backup has ended, as it must, with nothing after. Fails as damaged else. */
        fun next(): ByteArray? {
            check(index > 0) { "The content comes first" }
            if (ended || input == null) return null
            return when (readMarker()) {
                MORE -> openRecord(MAX_RECORD_BYTES, last = false)
                LAST -> {
                    ended = true
                    if (openRecord(0, last = true).isNotEmpty() || input.read() != -1) damaged()
                    null
                }
                else -> damaged()
            }
        }

        private fun readMarker(): Int = checkNotNull(input).read().also { if (it < 0) damaged() }

        private fun openRecord(max: Int, last: Boolean): ByteArray {
            val stream = checkNotNull(input)
            try {
                val size = stream.readInt()
                if (size > MAX_BYTES + TAG_BYTES) throw BackupException(BackupProblem.TooLarge)
                if (size < TAG_BYTES || size > max + TAG_BYTES) damaged()
                val nonce = ByteArray(NONCE_BYTES).also(stream::readFully)
                val sealed = ByteArray(size).also(stream::readFully)
                return cipher(Cipher.DECRYPT_MODE, key, nonce, aad(header, index++, last)).doFinal(sealed)
            } catch (e: EOFException) {
                throw BackupException(BackupProblem.Damaged, e)
            } catch (e: GeneralSecurityException) {
                throw BackupException(BackupProblem.Damaged, e)
            }
        }
    }

    /** A backup of format 2 to be written to [output] with [key], its [content] first; finish it with [Writer.finish]. */
    fun writer(output: OutputStream, key: BackupKey, content: ByteArray): Writer {
        if (content.size > MAX_BYTES) throw BackupException(BackupProblem.TooLarge)
        return Writer(DataOutputStream(output), key).apply { record(content, last = false) }
    }

    /** The backup in [input], opened with [password]: the key derived and checked, ready to be read. */
    fun reader(input: InputStream, password: String): Reader {
        val stream = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        val read = readUpTo(stream, magic)
        if (read < MAGIC.size || !magic.contentEquals(MAGIC)) throw BackupException(BackupProblem.NotABackup)
        val format = stream.read()
        if (format > FORMAT) throw BackupException(BackupProblem.NewerVersion)
        return when (format) {
            FORMAT -> {
                val rest = ByteArray(KEY_HEADER_BYTES - MAGIC.size - 1)
                if (readUpTo(stream, rest) < rest.size) damaged()
                val header = MAGIC + byteArrayOf(FORMAT.toByte()) + rest
                Reader(stream, header, keyOf(header, password), content = null)
            }
            FORMAT_1 -> {
                val file = MAGIC + byteArrayOf(FORMAT_1.toByte()) + read(stream, MAX_BYTES - MAGIC.size - 1)
                val opened = open1(file, password)
                Reader(null, ByteArray(0), opened.key, opened.plaintext)
            }
            else -> damaged()
        }
    }

    /** A backup of format 2 in memory: [content], then [attachments]. */
    fun seal(content: ByteArray, key: BackupKey, attachments: List<ByteArray> = emptyList()): ByteArray {
        val out = ByteArrayOutputStream()
        writer(out, key, content).apply {
            attachments.forEach(::attachment)
            finish()
        }
        return out.toByteArray()
    }

    /** A backup in memory, of either format, opened with [password] and read to its end. */
    fun open(file: ByteArray, password: String): DecryptedBackup {
        val reader = reader(ByteArrayInputStream(file), password)
        val content = reader.content()
        val attachments = generateSequence { reader.next() }.toList()
        return DecryptedBackup(content, reader.key, attachments)
    }

    /** A backup of format 1, as older versions of the app made them: for the tests of what they still open. */
    internal fun seal1(plaintext: ByteArray, key: BackupKey): ByteArray {
        if (HEADER_1_BYTES + plaintext.size + TAG_BYTES > MAX_BYTES) throw BackupException(BackupProblem.TooLarge)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = keyHeader(FORMAT_1, key) + nonce
        return header + cipher(Cipher.ENCRYPT_MODE, key, nonce, header).doFinal(plaintext)
    }

    private fun open1(file: ByteArray, password: String): DecryptedBackup {
        if (file.size < HEADER_1_BYTES + TAG_BYTES) damaged()
        val header = ByteBuffer.wrap(file, 0, HEADER_1_BYTES)
        header.position(KEY_HEADER_BYTES)
        val nonce = ByteArray(NONCE_BYTES).also(header::get)
        val key = keyOf(file.copyOf(KEY_HEADER_BYTES), password)
        val plaintext = try {
            cipher(Cipher.DECRYPT_MODE, key, nonce, file.copyOf(HEADER_1_BYTES)).doFinal(file, HEADER_1_BYTES, file.size - HEADER_1_BYTES)
        } catch (e: GeneralSecurityException) {
            throw BackupException(BackupProblem.Damaged, e)
        }
        return DecryptedBackup(plaintext, key)
    }

    /** All of [input], unless it is more than [max] bytes. */
    fun read(input: InputStream, max: Int = MAX_BYTES): ByteArray {
        val all = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return all.toByteArray()
            all.write(buffer, 0, read)
            if (all.size() > max) throw BackupException(BackupProblem.TooLarge)
        }
    }

    /** Fills [into] from [input] as far as it goes; how much it filled. */
    private fun readUpTo(input: InputStream, into: ByteArray): Int {
        var filled = 0
        while (filled < into.size) {
            val read = input.read(into, filled, into.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }

    private fun keyHeader(format: Int, key: BackupKey): ByteArray = ByteBuffer.allocate(KEY_HEADER_BYTES)
        .put(MAGIC).put(format.toByte()).putInt(key.iterations).put(key.salt).put(key.passwordCheck())
        .array()

    /** The key of [password] for the [header] given, which must have its iterations, salt and check. */
    private fun keyOf(header: ByteArray, password: String): BackupKey {
        val buffer = ByteBuffer.wrap(header, MAGIC.size + 1, KEY_HEADER_BYTES - MAGIC.size - 1)
        val iterations = buffer.int
        if (iterations !in BackupKey.ACCEPTED_ITERATIONS) damaged()
        val salt = ByteArray(BackupKey.SALT_BYTES).also(buffer::get)
        val check = ByteArray(BackupKey.CHECK_BYTES).also(buffer::get)
        val key = BackupKey.derive(password, salt, iterations)
        if (!MessageDigest.isEqual(key.passwordCheck(), check)) throw BackupException(BackupProblem.WrongPassword)
        return key
    }

    /** What a record is bound to: the header, its place, and whether it is the last. */
    private fun aad(header: ByteArray, index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(header.size + 8 + 1).put(header).putLong(index).put(if (last) 1 else 0).array()

    private fun cipher(mode: Int, key: BackupKey, nonce: ByteArray, aad: ByteArray) =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key.encryptionKey(), GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(aad)
        }
}
