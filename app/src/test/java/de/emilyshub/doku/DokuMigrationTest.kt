package de.emilyshub.doku

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * `doku.db` of an older version is brought up to this one by the Doku's own migrations, its data kept; Room checks the
 * outcome against the schema it exported, so that a migration that misses anything fails here.
 */
@RunWith(AndroidJUnit4::class)
class DokuMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(DokuDatabase.NAME)
    }

    @Test
    fun `version 1 gains the patients and keeps its settings`() {
        createAsExported(version = 1) { it.execSQL("INSERT INTO doku_setting (`key`, value) VALUES ('probe', 'kept')") }

        val database = testDokuDatabase(context)
        try {
            runBlocking {
                assertEquals("kept", database.settings().get("probe"))
                val id = database.patients().insert(Patient(name = "Frau M.", createdAt = Instant.parse("2026-10-23T05:00:00Z")))
                assertEquals("Frau M.", database.patients().get(id)?.name)
            }
        } finally {
            database.close()
        }
    }

    /** `doku.db` laid out as Room exported the schema of [version], filled by [fill]. */
    private fun createAsExported(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File("schemas/de.emilyshub.doku.DokuDatabase/$version.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(DokuDatabase.NAME).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            fill(db)
        }
    }
}
