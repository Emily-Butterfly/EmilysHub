package de.emilyshub.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.security.DatabaseKeyFile
import de.emilyshub.security.KeystoreKeys
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherDatabase
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Opening the app's database on the phone (#100), with the Keystore and SQLCipher: what cannot be opened says why, in one
 * of three groups, and is left as it is, never deleted or changed.
 */
@RunWith(AndroidJUnit4::class)
class OpenOnPhoneTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "test-${UUID.randomUUID()}.db"
    private val keyFile = File(context.cacheDir, "$name.key")
    private val databaseFile = HubDatabase.file(context, name)

    @After
    fun cleanUp() {
        context.deleteDatabase(name)
        keyFile.delete()
    }

    @Test
    fun `new data open, and open again with their key`() {
        openOnPhone(context, keyFile, name).close()

        openOnPhone(context, keyFile, name).close()
    }

    @Test
    fun `a damaged key file is the key, and the database stays as it was`() {
        openOnPhone(context, keyFile, name).close()
        val before = databaseFile.readBytes()
        keyFile.writeBytes(keyFile.readBytes().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() })

        assertFailsFor(Unopenable.KeyBroken)

        assertArrayEquals(before, databaseFile.readBytes())
    }

    @Test
    fun `a key file missing beside a database is the key, and none is made anew`() {
        openOnPhone(context, keyFile, name).close()
        keyFile.delete()

        assertFailsFor(Unopenable.KeyBroken)

        assertFalse(keyFile.exists())
    }

    @Test
    fun `a damaged database file is the file, and it is not deleted`() {
        openOnPhone(context, keyFile, name).close()
        val damaged = ByteArray(databaseFile.length().toInt()) { it.toByte() }
        databaseFile.writeBytes(damaged)

        assertFailsFor(Unopenable.FileBroken)

        assertArrayEquals(damaged, databaseFile.readBytes())
    }

    @Test
    fun `data of a newer version say so, and their version stays as it was`() {
        openOnPhone(context, keyFile, name).apply { openHelper.writableDatabase.version = HubDatabase.VERSION + 1 }.close()

        assertFailsFor(Unopenable.NewerVersion)

        assertEquals(HubDatabase.VERSION + 1, versionAsItLies())
    }

    private fun assertFailsFor(reason: Unopenable) {
        try {
            openOnPhone(context, keyFile, name).close()
            fail("opened")
        } catch (e: DataUnavailable) {
            assertEquals(reason, e.reason)
        }
    }

    /** The version of the database as it lies, read with SQLCipher alone. */
    private fun versionAsItLies(): Int {
        val key = DatabaseKeyFile(keyFile, KeystoreKeys.getOrCreateAesGcmKey(KEYSTORE_ALIAS)).readOrCreate()
        System.loadLibrary("sqlcipher")
        val db = SQLCipherDatabase.openDatabase(databaseFile.path, HubDatabase.rawKey(key), null, SQLCipherDatabase.OPEN_READONLY, null, null)
        return db.use { it.version }
    }
}
