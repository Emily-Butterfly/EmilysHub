package de.emilyshub.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class DatabaseKeyFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val wrappingKey = softwareAesKey()

    @Test
    fun `first use creates a 256-bit key`() {
        val key = DatabaseKeyFile(keyFile(), wrappingKey).readOrCreate()

        assertEquals(32, key.size)
    }

    @Test
    fun `the key stays the same across app restarts`() {
        val first = DatabaseKeyFile(keyFile(), wrappingKey).readOrCreate()

        val afterRestart = DatabaseKeyFile(keyFile(), wrappingKey).readOrCreate()

        assertArrayEquals(first, afterRestart)
    }

    @Test
    fun `every installation gets its own key`() {
        val one = DatabaseKeyFile(keyFile(folder.newFolder()), wrappingKey).readOrCreate()
        val other = DatabaseKeyFile(keyFile(folder.newFolder()), wrappingKey).readOrCreate()

        assertFalse(one.contentEquals(other))
    }

    @Test
    fun `the key is never written to disk in plain`() {
        val key = DatabaseKeyFile(keyFile(), wrappingKey).readOrCreate()

        val stored = folder.root.walk().filter(File::isFile).map(File::readBytes).toList()

        assertTrue(stored.isNotEmpty())
        assertTrue(stored.none { it.containsSequence(key) })
    }

    @Test
    fun `a key file from another wrapping key is rejected and kept`() {
        DatabaseKeyFile(keyFile(), wrappingKey).readOrCreate()
        val stored = keyFile().readBytes()

        assertThrows(IllegalStateException::class.java) {
            DatabaseKeyFile(keyFile(), softwareAesKey()).readOrCreate()
        }
        // Starting over with a new key would lock the existing database for good.
        assertArrayEquals(stored, keyFile().readBytes())
    }

    @Test
    fun `a damaged key file is rejected`() {
        DatabaseKeyFile(keyFile(), wrappingKey).readOrCreate()
        val stored = keyFile().readBytes()
        val damaged = listOf(
            stored.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() },
            stored.copyOf(stored.size - 1),
            stored.copyOf(4),
            ByteArray(0),
        )

        for (content in damaged) {
            keyFile().writeBytes(content)
            assertThrows(IllegalStateException::class.java) {
                DatabaseKeyFile(keyFile(), wrappingKey).readOrCreate()
            }
        }
    }

    private fun keyFile(dir: File = folder.root) = File(dir, "database.key")

    private fun ByteArray.containsSequence(part: ByteArray) =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    /** Stands in for the Android Keystore key, which only exists on a device. */
    private fun softwareAesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
