package de.emilyshub.backup

import android.content.Context
import android.database.Cursor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.HubDatabase
import de.emilyshub.places.Coordinates
import de.emilyshub.places.PlaceDraft
import de.emilyshub.places.Places
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import de.emilyshub.tags.Tags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalTime
import java.util.UUID
import javax.crypto.KeyGenerator

/**
 * Backups (SPEC.md §10) between two databases encrypted with SQLCipher, on Android's own crypto, with the real number
 * of key derivation rounds: what the Robolectric tests cannot show.
 */
@RunWith(AndroidJUnit4::class)
class BackupsOnDeviceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = List(2) { "backup-test-${UUID.randomUUID()}.db" }
    private val wrappingKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @After
    fun deleteDatabases() {
        names.forEach(context::deleteDatabase)
    }

    @Test
    fun `a backup of one encrypted database opens with its password only and restores into another as it was`() {
        val old = HubDatabase.open(context, randomKey(), names[0])
        val new = HubDatabase.open(context, randomKey(), names[1])
        try {
            runBlocking {
                val tag = Tags({ old }).create("Gesundheit")
                val shop = PlaceDraft("Supermarkt", listOf("Markt-WLAN")).withCenter(Coordinates(52.52, 13.405))
                val place = Places({ old }).create(shop)
                Routines({ old }, Clock.systemUTC()).create(
                    RoutineDraft(
                        "Vitamine", description = "", listOf(LocalTime.of(7, 30)), Repetition.Daily,
                        tagIds = setOf(tag), placeIds = setOf(place),
                    ),
                )
            }
            val file = runBlocking {
                val backups = Backups({ old }, { old.settings() }, { wrappingKey }, Clock.systemUTC())
                backups.setPassword(PASSWORD)
                ByteArrayOutputStream().also { out -> backups.export { out } }.toByteArray()
            }

            val backups = Backups({ new }, { new.settings() }, { wrappingKey }, Clock.systemUTC())
            val wrongPassword = assertThrows(BackupException::class.java) {
                runBlocking { backups.open({ ByteArrayInputStream(file) }, "Lila und Flieder") }
            }
            assertEquals(BackupProblem.WrongPassword, wrongPassword.problem)
            runBlocking { backups.restore(backups.open({ ByteArrayInputStream(file) }, PASSWORD)) }

            assertTrue(everything(old).getValue("routines").isNotEmpty())
            assertEquals(everything(old), everything(new))
        } finally {
            old.close()
            new.close()
        }
    }

    /** Every row of every table but the settings, read here on its own, without the backups. */
    private fun everything(database: HubDatabase): Map<String, List<List<Any?>>> {
        val db = database.openHelper.readableDatabase
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table', 'settings') ORDER BY name",
        ).use { cursor -> List(cursor.count) { cursor.moveToNext(); cursor.getString(0) } }
        return tables.associateWith { table ->
            db.query("SELECT * FROM `$table` ORDER BY 1, 2").use { cursor ->
                List(cursor.count) {
                    cursor.moveToNext()
                    List(cursor.columnCount) { column ->
                        when (cursor.getType(column)) {
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(column)
                            Cursor.FIELD_TYPE_NULL -> null
                            else -> cursor.getString(column)
                        }
                    }
                }
            }
        }
    }

    private fun randomKey() = ByteArray(32).also(SecureRandom()::nextBytes)

    private companion object {
        const val PASSWORD = "Flieder und Lila"
    }
}
