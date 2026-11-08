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
import java.time.LocalDate

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

    @Test
    fun `version 2 gains the appointments and keeps its patients`() {
        createAsExported(version = 2) {
            it.execSQL(
                "INSERT INTO patient (id, name, diagnosis, anamnesis, goals, other, created_at) " +
                    "VALUES (7, 'Frau M.', 'Schulter', '', '', '', '2026-10-23T05:00:00Z')",
            )
        }

        val database = testDokuDatabase(context)
        try {
            runBlocking {
                assertEquals("Schulter", database.patients().get(7)?.diagnosis)
                val id = database.appointments().insert(Appointment(patientId = 7, date = LocalDate.parse("2026-10-23"), text = "Befund"))
                assertEquals("Befund", database.appointments().get(id)?.text)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 3 gains the tests and measurements and keeps its appointments`() {
        createAsExported(version = 3) {
            it.execSQL(
                "INSERT INTO patient (id, name, diagnosis, anamnesis, goals, other, created_at) " +
                    "VALUES (7, 'Frau M.', '', '', '', '', '2026-10-23T05:00:00Z')",
            )
            it.execSQL("INSERT INTO appointment (id, patient_id, date, text) VALUES (3, 7, '2026-10-23', 'Befund')")
        }

        val database = testDokuDatabase(context)
        try {
            runBlocking {
                assertEquals("Befund", database.appointments().get(3)?.text)
                val test = database.tests().save(ClinicalTest(name = "Handkraft"), listOf(TestValue(testId = 0, position = 0, name = "rechts")), emptyList())
                val value = database.tests().values(test).single().id
                val measurement = database.measurements().save(Measurement(appointmentId = 3, testId = test), listOf(MeasurementValue(0, value, 3000)))
                assertEquals(listOf(3000L), database.measurements().values(measurement).map(MeasurementValue::amount))
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
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            fill(db)
        }
    }
}
