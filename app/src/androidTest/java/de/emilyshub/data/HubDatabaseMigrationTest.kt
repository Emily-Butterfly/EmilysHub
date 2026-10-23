package de.emilyshub.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Routine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase as SQLCipherDatabase

/**
 * Each migration on an encrypted database. Opening it runs the migrations, and Room then checks every
 * table, column, key and index against the app's entities, failing if anything differs.
 *
 * Room's MigrationTestHelper would do the same, but room-testing needs a newer kotlinx-serialization
 * than the app ships, and the test APK has to use the app's.
 */
@RunWith(AndroidJUnit4::class)
class HubDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-${UUID.randomUUID()}.db"
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun `version 1 moves to version 2 keeping its settings and gaining routines`() {
        createAtVersion(1) { it.execSQL("INSERT INTO settings (name, value) VALUES ('design', 'dark')") }

        val database = HubDatabase.open(context, key, name)
        try {
            runBlocking {
                assertEquals("dark", database.settings().get("design"))

                val day = LocalDate.parse("2026-10-23")
                database.routines().insert(Routine(name = "Lesen", description = "", time = LocalTime.of(21, 0), startDate = day))
                database.routines().addOccurrences(day, OccurrenceStatus.Open)
                assertEquals(listOf("Lesen"), database.routines().observeOccurrences(day).first().map { it.name })
            }
        } finally {
            database.close()
        }
    }

    /** An encrypted database as [version] of the app created it, built from the schema Room exported for it. */
    private fun createAtVersion(version: Int, fill: (SQLCipherDatabase) -> Unit) {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("${HubDatabase::class.java.name}/$version.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
            .getJSONObject("database")

        System.loadLibrary("sqlcipher")
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        val flags = SQLCipherDatabase.OPEN_READWRITE or SQLCipherDatabase.CREATE_IF_NECESSARY
        SQLCipherDatabase.openDatabase(file.path, HubDatabase.rawKey(key), null, flags, null, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE_NAME, table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_NAME, table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            fill(db)
        }
    }

    private companion object {
        const val TABLE_NAME = "\${TABLE_NAME}"
    }
}
