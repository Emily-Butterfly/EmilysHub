package de.emilyshub.tracking

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.R
import de.emilyshub.island.IslandClipboard
import de.emilyshub.island.RawKey
import de.emilyshub.island.SystemFingerprint
import de.emilyshub.security.DatabaseKeyFile
import de.emilyshub.security.KeystoreKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.SecureRandom
import java.util.UUID

/**
 * `tracking.db` as SQLCipher keeps it on the device (SPEC.md §17): nothing opens it but its own key, which only the
 * Tracking password sets free; not Android's SQLite without a key, not a wrong key, and not the key of the rest of the
 * app. Test values are made up.
 */
@RunWith(AndroidJUnit4::class)
class TrackingDatabaseTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "tracking-test-${UUID.randomUUID()}.db"
    private val file = context.getDatabasePath(name)
    private val keyFile = File(context.noBackupFilesDir, "$name.key")

    /** The real Tracking over a database file of its own, with a quick key derivation, closing on the spot when it locks. */
    private val tracking = Tracking(
        keyFile = keyFile,
        databaseFile = file,
        schema = TrackingDatabase.VERSION,
        fingerprint = SystemFingerprint(
            context,
            "tracking-test-fingerprint-key",
            R.string.tracking_fingerprint_unlock_title,
            R.string.tracking_fingerprint_enable_title,
        ),
        openDatabase = { rawKey -> TrackingDatabase.open(context, rawKey, name) },
        clipboard = IslandClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
    )

    @After
    fun deleteDatabase() {
        tracking.lock()
        context.deleteDatabase(name)
        keyFile.delete()
    }

    /** Tracking set up with a made-up value in it, then locked. */
    private fun setUpWithData() = runBlocking {
        tracking.load()
        tracking.setUp(PASSWORD)
        tracking.withDatabase { it.settings().put(TrackingSetting("probe", "Medikament A, 10 mg")) }
        tracking.lock()
    }

    @Test
    fun `the file on disk is encrypted`() {
        setUpWithData()

        val bytes = file.readBytes()

        assertFalse(bytes.startsWith("SQLite format 3".toByteArray()))
        assertFalse(bytes.contains("Medikament A".toByteArray()))
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
        assertEquals("Medikament A, 10 mg", readWithPassword())
    }

    @Test
    fun `a wrong key cannot open the file`() {
        setUpWithData()

        assertThrows(SQLiteException::class.java) { readWithKey(ByteArray(32).also(SecureRandom()::nextBytes)) }
        assertEquals("Medikament A, 10 mg", readWithPassword())
    }

    @Test
    fun `the key of the rest of the app cannot open the file`() {
        setUpWithData()
        val appKey = DatabaseKeyFile(File(context.noBackupFilesDir, "database.key"), KeystoreKeys.getOrCreateAesGcmKey("database-key"))
            .readOrCreate()

        assertThrows(SQLiteException::class.java) { readWithKey(appKey) }
        assertEquals("Medikament A, 10 mg", readWithPassword())
    }

    @Test
    fun `deleting all empties the encrypted database`() {
        setUpWithData()
        runBlocking { tracking.unlockWithPassword(PASSWORD) }

        assertTrue(runBlocking { tracking.deleteAll(PASSWORD) })

        assertNull(readWithPassword())
    }

    @Test
    fun `a wrong password sets no key free`() {
        setUpWithData()

        assertFalse(runBlocking { tracking.unlockWithPassword("falsches Passwort") })
    }

    private fun readWithKey(key: ByteArray): String? {
        val database = TrackingDatabase.open(context, RawKey.of(key), name)
        try {
            return runBlocking { database.settings().get("probe") }
        } finally {
            database.close()
        }
    }

    private fun readWithPassword(): String? = runBlocking {
        tracking.unlockWithPassword(PASSWORD)
        tracking.withDatabase { it.settings().get("probe") }.also { tracking.lock() }
    }

    private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.contains(part: ByteArray) =
        (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

    private companion object {
        const val PASSWORD = "tracking-geheim"
    }
}
