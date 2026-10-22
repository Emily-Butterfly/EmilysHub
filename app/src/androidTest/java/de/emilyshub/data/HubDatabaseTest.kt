package de.emilyshub.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherDatabase

@RunWith(AndroidJUnit4::class)
class HubDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test-${UUID.randomUUID()}.db"
    private val file = context.getDatabasePath(name)
    private val key = randomKey()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun `settings are still there after reopening`() {
        withDatabase(key) { settings().put(Setting("design", "dunkel")) }

        val design = withDatabase(key) { settings().get("design") }

        assertEquals("dunkel", design)
    }

    @Test
    fun `the file on disk is encrypted`() {
        withDatabase(key) { settings().put(Setting("tagebuch", "Heute war ein guter Tag")) }

        val bytes = file.readBytes()

        assertFalse(bytes.startsWith("SQLite format 3".toByteArray()))
        assertFalse(bytes.contains("Heute war ein guter Tag".toByteArray()))
    }

    @Test
    fun `the file cannot be opened without a key`() {
        withDatabase(key) { settings().put(Setting("design", "dunkel")) }

        assertThrows(SQLiteException::class.java) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { plain ->
                plain.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            }
        }
    }

    @Test
    fun `a wrong key cannot open the file and leaves the data intact`() {
        withDatabase(key) { settings().put(Setting("design", "dunkel")) }

        assertThrows(SQLiteException::class.java) {
            withDatabase(randomKey()) { settings().get("design") }
        }

        assertEquals("dunkel", withDatabase(key) { settings().get("design") })
    }

    @Test
    fun `a schema version without a migration fails instead of wiping the data`() {
        withDatabase(key) { settings().put(Setting("design", "dunkel")) }
        setSchemaVersion(99)

        assertThrows(IllegalStateException::class.java) {
            withDatabase(key) { settings().get("design") }
        }

        setSchemaVersion(1)
        assertEquals("dunkel", withDatabase(key) { settings().get("design") })
    }

    private fun <T> withDatabase(key: ByteArray, block: suspend HubDatabase.() -> T): T {
        val database = HubDatabase.open(context, key, name)
        try {
            return runBlocking { database.block() }
        } finally {
            database.close()
        }
    }

    /** Changes the stored schema version behind Room's back, using the raw-key form SQLCipher expects. */
    private fun setSchemaVersion(version: Int) {
        val rawKey = "x'${key.toHexString()}'".toByteArray()
        SQLCipherDatabase.openDatabase(file.path, rawKey, null, SQLCipherDatabase.OPEN_READWRITE, null, null)
            .use { it.version = version }
    }

    private fun randomKey() = ByteArray(32).also(SecureRandom()::nextBytes)

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.contains(part: ByteArray) =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }
}
