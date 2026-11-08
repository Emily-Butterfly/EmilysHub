package de.emilyshub.doku

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
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
        createDokuAsExported(context, version = 1) { it.execSQL("INSERT INTO doku_setting (`key`, value) VALUES ('probe', 'kept')") }

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
        createDokuAsExported(context, version = 2) {
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
        createDokuAsExported(context, version = 3) {
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
}
