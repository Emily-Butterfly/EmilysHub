package de.emilyshub.backup

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.data.DataUnavailable
import de.emilyshub.data.HubData
import de.emilyshub.data.SetAside
import de.emilyshub.data.Unopenable
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import javax.crypto.KeyGenerator

/**
 * The ways out when the data cannot be opened (#100), with the settings in the data, as on the phone: a backup loaded
 * into new data, or a start as on a new installation, the old data laid aside either way; and whatever fails puts
 * everything back as it was.
 */
@RunWith(AndroidJUnit4::class)
class DataRescueTest {

    @get:Rule
    val disk = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private var fails: Unopenable? = null
    private val databaseFile by lazy { disk.root.resolve("emilyshub.db").apply { writeText("old data") } }
    private val data by lazy {
        HubData(
            open = { fails?.let { throw DataUnavailable(it) } ?: inMemoryDatabase() },
            files = listOf(databaseFile),
            setAsideFolder = disk.root.resolve("set-aside"),
            clock = app.clock,
            io = Dispatchers.Unconfined,
        )
    }
    private val wrappingKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val backups by lazy {
        Backups(
            database = { data.database },
            settings = { data.database.settings() },
            wrappingKey = { wrappingKey },
            clock = app.clock,
            migration = BackupMigration(app) { FrameworkSQLiteOpenHelperFactory() },
            doku = app.doku,
            attachments = app.lessonFiles,
            io = Dispatchers.Unconfined,
            iterations = 1_000,
            onRestore = { check(!restoreFails) { "The reminders could not follow" } },
        )
    }
    private var startedOver = 0

    /** Whether restoring fails at its very end, once the data are replaced. */
    private var restoreFails = false
    private val rescue by lazy { DataRescue(data, backups) { startedOver++ } }
    private val routines by lazy { Routines({ data.database }, app.clock, Dispatchers.Unconfined) }

    /** A backup with the routine Vitamine; then the data cannot be opened, for want of their key. */
    private fun aBackupThenBroken(): ByteArray = runBlocking {
        databaseFile
        routines.create(RoutineDraft("Vitamine", "", listOf(LocalTime.of(7, 30)), Repetition.Daily))
        backups.setPassword(PASSWORD)
        val file = ByteArrayOutputStream()
        backups.export { file }
        fails = Unopenable.KeyBroken
        assertEquals(Unopenable.KeyBroken, data.retry())
        fails = null
        file.toByteArray()
    }

    @Test
    fun `starting over, the data are empty, without a backup password, and the old ones lie aside`() = runBlocking {
        aBackupThenBroken()

        rescue.startOver()

        assertEquals(null, data.check())
        assertEquals(emptyList<String>(), routineNames())
        assertFalse(backups.hasPassword())
        assertFalse(databaseFile.exists())
        assertEquals(1, data.setAside().size)
        assertEquals(1, startedOver)
    }

    @Test
    fun `a backup loaded brings its data and its password, and the old ones lie aside`() = runBlocking {
        val file = aBackupThenBroken()

        val backup = rescue.open({ ByteArrayInputStream(file) }, PASSWORD)
        rescue.restore(backup)

        assertEquals(null, data.check())
        assertEquals(listOf("Vitamine"), routineNames())
        assertTrue(backups.hasPassword())
        assertEquals(1, data.setAside().size)
        assertEquals(0, startedOver)
    }

    @Test
    fun `a backup that does not open leaves everything as it was`() = runBlocking {
        val file = aBackupThenBroken()
        fails = Unopenable.KeyBroken

        try {
            rescue.open({ ByteArrayInputStream(file) }, "Lila und Flieder")
            fail("opened")
        } catch (e: DataUnavailable) {
            // The empty data begun anew do not open either.
        }
        fails = null
        try {
            rescue.open({ ByteArrayInputStream(file) }, "Lila und Flieder")
            fail("opened")
        } catch (e: BackupException) {
            assertEquals(BackupProblem.WrongPassword, e.problem)
        }

        assertEquals("old data", databaseFile.readText())
        assertEquals(emptyList<SetAside>(), data.setAside())
        assertStillUnopenable()
    }

    @Test
    fun `a backup opened but not restored leaves everything as it was`() = runBlocking {
        val file = aBackupThenBroken()

        rescue.open({ ByteArrayInputStream(file) }, PASSWORD)
        rescue.cancel()

        assertEquals("old data", databaseFile.readText())
        assertEquals(emptyList<SetAside>(), data.setAside())
        assertStillUnopenable()
    }

    @Test
    fun `a backup that fails to be restored leaves everything as it was`() = runBlocking {
        val file = aBackupThenBroken()
        val backup = rescue.open({ ByteArrayInputStream(file) }, PASSWORD)
        restoreFails = true

        try {
            rescue.restore(backup)
            fail("restored")
        } catch (e: IllegalStateException) {
            // Failed at its end.
        }

        assertEquals("old data", databaseFile.readText())
        assertEquals(emptyList<SetAside>(), data.setAside())
        assertStillUnopenable()
    }

    /** As the data begun anew are taken back, they are opened anew, which here never fails: fails the next time. */
    private suspend fun assertStillUnopenable() {
        fails = Unopenable.KeyBroken
        assertEquals(Unopenable.KeyBroken, data.check())
    }

    private suspend fun routineNames() = routines.inUse().first().map { it.name }

    private companion object {
        const val PASSWORD = "Flieder und Lila"
    }
}
