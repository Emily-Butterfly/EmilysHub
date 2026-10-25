package de.emilyshub.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

/** The backup file (SPEC.md §10): AES-256-GCM with a key from the password, opened only whole, unchanged and with it. */
class BackupFileTest {

    private val content = "Vitamine, Yoga und ein Einkauf".toByteArray()
    private val key = BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS)

    @Test
    fun `a backup opens with its password, as it was, and with the key it was made with`() {
        val opened = BackupFile.open(BackupFile.seal(content, key), PASSWORD)

        assertArrayEquals(content, opened.content)
        assertArrayEquals(key.salt, opened.key.salt)
        assertEquals(key.iterations, opened.key.iterations)
    }

    @Test
    fun `the content is not in the file in plain, and each backup is encrypted anew`() {
        val first = BackupFile.seal(content, key)
        val second = BackupFile.seal(content, key)

        assertFalse(first.containsSequence(content))
        assertFalse(first.contentEquals(second))
        assertArrayEquals(content, BackupFile.open(second, PASSWORD).content)
    }

    @Test
    fun `with a wrong password it does not open`() {
        val file = BackupFile.seal(content, key)

        assertProblem(BackupProblem.WrongPassword) { BackupFile.open(file, "falsches Passwort") }
    }

    @Test
    fun `a password typed with other but equal characters still opens it`() {
        val composed = BackupKey.derive("Müllabfuhr-Plan", iterations = FEW_ITERATIONS)
        val file = BackupFile.seal(content, composed)

        // "ü" as "u" and a combining umlaut, as some keyboards type it.
        assertArrayEquals(content, BackupFile.open(file, "Müllabfuhr-Plan").content)
    }

    @Test
    fun `a file changed in its content, its nonce or its tag does not open`() {
        val file = BackupFile.seal(content, key)

        for (index in listOf(NONCE_AT, HEADER_BYTES, HEADER_BYTES + content.size / 2, file.lastIndex)) {
            assertProblem(BackupProblem.Damaged) { BackupFile.open(file.flipped(index), PASSWORD) }
        }
    }

    @Test
    fun `a file changed in its salt or password check does not open`() {
        val file = BackupFile.seal(content, key)

        for (index in listOf(SALT_AT, CHECK_AT)) {
            assertProblem(BackupProblem.WrongPassword) { BackupFile.open(file.flipped(index), PASSWORD) }
        }
    }

    @Test
    fun `a truncated file does not open`() {
        val file = BackupFile.seal(content, key)

        for (size in listOf(file.size - 1, HEADER_BYTES + 3, HEADER_BYTES, NONCE_AT, 5)) {
            assertProblem(BackupProblem.Damaged) { BackupFile.open(file.copyOf(size), PASSWORD) }
        }
    }

    @Test
    fun `a file that is no backup is refused as such`() {
        for (file in listOf(ByteArray(0), "EMH".toByteArray(), "Einkaufsliste: Milch, Brot".toByteArray())) {
            assertProblem(BackupProblem.NotABackup) { BackupFile.open(file, PASSWORD) }
        }
    }

    @Test
    fun `a backup of a newer format is refused as such`() {
        val file = BackupFile.seal(content, key).also { it[VERSION_AT] = 2 }

        assertProblem(BackupProblem.NewerVersion) { BackupFile.open(file, PASSWORD) }
    }

    @Test
    fun `iterations out of all reason are refused before deriving anything`() {
        val file = BackupFile.seal(content, key)

        for (iterations in listOf(0, -1, 100_000_000)) {
            val changed = file.copyOf().also { ByteBuffer.wrap(it).putInt(ITERATIONS_AT, iterations) }
            assertProblem(BackupProblem.Damaged) { BackupFile.open(changed, PASSWORD) }
        }
    }

    @Test
    fun `a file is read up to the size limit, and not beyond`() {
        val largest = ByteArray(BackupFile.MAX_BYTES)

        assertEquals(BackupFile.MAX_BYTES, BackupFile.read(ByteArrayInputStream(largest)).size)
        assertProblem(BackupProblem.TooLarge) { BackupFile.read(ByteArrayInputStream(ByteArray(BackupFile.MAX_BYTES + 1))) }
    }

    @Test
    fun `content too large to be read back is not sealed`() {
        assertProblem(BackupProblem.TooLarge) { BackupFile.seal(ByteArray(BackupFile.MAX_BYTES), key) }
    }

    @Test
    fun `the key is derived with many iterations and a salt of its own each time`() {
        assertEquals(600_000, BackupKey.ITERATIONS)
        assertFalse(BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS).salt.contentEquals(key.salt))
    }

    private fun assertProblem(problem: BackupProblem, block: () -> Unit) {
        assertEquals(problem, assertThrows(BackupException::class.java, block).problem)
    }

    private fun ByteArray.flipped(index: Int) = copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }

    private fun ByteArray.containsSequence(part: ByteArray) =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    private companion object {
        const val PASSWORD = "Flieder und Lila"

        /** Enough for the tests, which would take seconds with the real count. */
        const val FEW_ITERATIONS = 1_000

        // The header: "EMHB", the format version, the iterations, the salt, the password check, the nonce.
        const val VERSION_AT = 4
        const val ITERATIONS_AT = 5
        const val SALT_AT = 9
        const val CHECK_AT = 25
        const val NONCE_AT = 41
        const val HEADER_BYTES = 53
    }
}
