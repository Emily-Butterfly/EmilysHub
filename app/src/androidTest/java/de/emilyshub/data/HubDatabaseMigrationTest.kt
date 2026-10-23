package de.emilyshub.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Routine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Each migration on an encrypted database, checked against the schema Room exports to app/schemas. */
@RunWith(AndroidJUnit4::class)
class HubDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-${UUID.randomUUID()}.db"
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)

    init {
        System.loadLibrary("sqlcipher")
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HubDatabase::class.java,
        emptyList(),
        SupportOpenHelperFactory(HubDatabase.rawKey(key)),
    )

    @After
    fun deleteDatabase() {
        context.deleteDatabase(name)
    }

    @Test
    fun `version 1 moves to version 2 keeping its settings and gaining routines`() {
        helper.createDatabase(name, 1).use { it.execSQL("INSERT INTO settings (name, value) VALUES ('design', 'dark')") }

        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()

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
}
