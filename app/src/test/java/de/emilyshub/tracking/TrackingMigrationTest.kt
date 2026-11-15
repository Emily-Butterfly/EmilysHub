package de.emilyshub.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * `tracking.db` of an older version is brought up to this one by its own migrations, its data kept; Room checks the
 * outcome against the schema it exported, so that a migration that misses anything fails here.
 */
@RunWith(AndroidJUnit4::class)
class TrackingMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun deleteDatabase() {
        context.deleteDatabase(TrackingDatabase.NAME)
    }

    @Test
    fun `version 1 gains the entries and medications and keeps its settings`() {
        createTrackingAsExported(context, version = 1) { it.execSQL("INSERT INTO tracking_setting (`key`, value) VALUES ('probe', 'kept')") }

        val database = testTrackingDatabase(context)
        try {
            runBlocking {
                assertEquals("kept", database.settings().get("probe"))
                val day = LocalDate.parse("2026-11-15")
                database.entries().insertMorning(MorningEntry(date = day, restingPulse = 64, recordedOn = day))
                assertEquals(64, database.entries().morning(day)?.restingPulse)
                val id = database.medications().insert(Medication(name = "Medikament A", times = listOf(LocalTime.of(8, 0)), start = day))
                assertEquals("Medikament A", database.medications().get(id)?.name)
            }
        } finally {
            database.close()
        }
    }
}
