package de.emilyshub.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Writing a file durably: whole or not at all, nothing half-written left behind. */
class DurableFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a file is written whole, in place of the one there`() {
        val file = File(folder.root, "key")
        writeDurably(file, byteArrayOf(1))
        writeDurably(file, byteArrayOf(2, 3))

        assertArrayEquals(byteArrayOf(2, 3), file.readBytes())
        assertEquals(listOf("key"), folder.root.list()!!.toList())
    }

    @Test
    fun `a write that fails, as with the storage full, leaves nothing half-written behind`() {
        // A folder with something in it cannot be replaced by a file: the move fails, after the bytes were written.
        val file = File(folder.root, "key").apply { mkdirs() }
        File(file, "inside").writeText("x")

        assertThrows(IOException::class.java) { writeDurably(file, byteArrayOf(1, 2, 3)) }

        assertEquals(listOf("key"), folder.root.list()!!.toList())
    }
}
