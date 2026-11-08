package de.emilyshub.doku

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.security.DatabaseKeyFile
import de.emilyshub.security.KeystoreKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/**
 * `doku.db` as SQLCipher keeps it on the device (SPEC.md §14): nothing opens it but its own key, which only the Doku
 * password sets free; not Android's SQLite without a key, not a wrong key, and not the key of the rest of the app.
 */
@RunWith(AndroidJUnit4::class)
class DokuDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "doku-test-${UUID.randomUUID()}.db"
    private val file = context.getDatabasePath(name)
    private val keyFile = File(context.noBackupFilesDir, "$name.key")

    /** The real Doku over a database file of its own, with a quick key derivation, closing on the spot when it locks. */
    private val doku = Doku(
        keyFile = keyFile,
        databaseFile = file,
        fingerprint = SystemFingerprint(context),
        openDatabase = { rawKey -> DokuDatabase.open(context, rawKey, name) },
        clipboard = DokuClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
    )

    @After
    fun deleteDatabase() {
        doku.lock()
        context.deleteDatabase(name)
        keyFile.delete()
    }

    /** The Doku set up with a patient's note in it, then locked. */
    private fun setUpWithData() = runBlocking {
        doku.load()
        doku.setUp(PASSWORD)
        doku.database.settings().put(DokuSetting("probe", "Frau M., Schulter links"))
        doku.lock()
    }

    @Test
    fun `the file on disk is encrypted`() {
        setUpWithData()

        val bytes = file.readBytes()

        assertFalse(bytes.startsWith("SQLite format 3".toByteArray()))
        assertFalse(bytes.contains("Schulter links".toByteArray()))
    }

    @Test
    fun `without a key the file cannot be opened`() {
        setUpWithData()

        // Android's own SQLite sees only noise; the handler keeps it from deleting the file.
        assertThrows(SQLiteDatabaseCorruptException::class.java) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) {}.use { plain ->
                plain.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            }
        }
        assertEquals("Frau M., Schulter links", readWithPassword())
    }

    @Test
    fun `a wrong key cannot open the file`() {
        setUpWithData()

        assertThrows(SQLiteException::class.java) { readWithKey(ByteArray(32).also(SecureRandom()::nextBytes)) }
        assertEquals("Frau M., Schulter links", readWithPassword())
    }

    @Test
    fun `the key of the rest of the app cannot open the file`() {
        setUpWithData()
        val appKey = DatabaseKeyFile(File(context.noBackupFilesDir, "database.key"), KeystoreKeys.getOrCreateAesGcmKey("database-key"))
            .readOrCreate()

        assertThrows(SQLiteException::class.java) { readWithKey(appKey) }
        assertEquals("Frau M., Schulter links", readWithPassword())
    }

    @Test
    fun `a backup takes the Doku along encrypted also while open and puts it back to open with the password`() {
        setUpWithData()
        runBlocking { doku.unlockWithPassword(PASSWORD) }
        // While open, the write-ahead log may still hold this.
        runBlocking { doku.database.settings().put(DokuSetting("later", "Herr K., Knie")) }
        val snapshot = runBlocking { checkNotNull(doku.snapshot()) }
        runBlocking { doku.database.settings().put(DokuSetting("probe", "überschrieben")) }

        assertFalse(snapshot.database.contains("Schulter links".toByteArray()))
        assertFalse(snapshot.wal?.contains("Knie".toByteArray()) ?: false)
        runBlocking { doku.replacedBy(snapshot) {} }

        assertEquals("Frau M., Schulter links", readWithPassword())
        runBlocking { doku.unlockWithPassword(PASSWORD) }
        assertEquals("Herr K., Knie", runBlocking { doku.database.settings().get("later") })
    }

    @Test
    fun `a wrong password sets no key free`() {
        setUpWithData()

        assertFalse(runBlocking { doku.unlockWithPassword("falsches Passwort") })
    }

    private fun readWithKey(key: ByteArray): String? {
        val database = DokuDatabase.open(context, DokuDatabase.rawKey(key), name)
        try {
            return runBlocking { database.settings().get("probe") }
        } finally {
            database.close()
        }
    }

    private fun readWithPassword(): String? = runBlocking {
        doku.unlockWithPassword(PASSWORD)
        doku.database.settings().get("probe").also { doku.lock() }
    }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.contains(part: ByteArray) =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    private companion object {
        const val PASSWORD = "geheim-geheim"
    }
}
