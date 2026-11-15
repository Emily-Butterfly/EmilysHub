package de.emilyshub.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.doku.FakeFingerprint
import de.emilyshub.doku.testDokuDatabase
import de.emilyshub.island.IslandClipboard
import de.emilyshub.island.IslandState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tracking's vault (SPEC.md §17): the same lock as the Doku's, but a password, key and database of its own; emptied
 * with "Alles löschen"; and the five minutes resting while a measurement runs.
 */
@RunWith(AndroidJUnit4::class)
class TrackingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var clock = 1_000_000L

    private val tracking = Tracking(
        keyFile = File(context.noBackupFilesDir, "tracking-test.key"),
        databaseFile = context.getDatabasePath(TrackingDatabase.NAME),
        schema = TrackingDatabase.VERSION,
        fingerprint = FakeFingerprint(),
        openDatabase = { testTrackingDatabase(context) },
        clipboard = IslandClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
        now = { clock },
    ).also { runBlocking { it.load() } }

    @After
    fun closeDatabase() {
        tracking.lock()
        context.deleteDatabase(TrackingDatabase.NAME)
        context.deleteDatabase(DokuDatabase.NAME)
    }

    private fun write(value: String) {
        checkNotNull(runBlocking { tracking.withDatabase { it.settings().put(TrackingSetting("probe", value)) } })
    }

    private fun read(): String? = runBlocking { tracking.withDatabase { it.settings().get("probe") } }

    @Test
    fun `Tracking has a password of its own, which the Doku's does not open`() {
        val doku = Doku(
            keyFile = File(context.noBackupFilesDir, "doku-beside-tracking.key"),
            databaseFile = context.getDatabasePath(DokuDatabase.NAME),
            schema = DokuDatabase.VERSION,
            fingerprint = FakeFingerprint(),
            openDatabase = { testDokuDatabase(context) },
            clipboard = IslandClipboard(context),
            io = Dispatchers.Unconfined,
            background = CoroutineScope(Dispatchers.Unconfined),
            iterations = 1_000,
        )
        runBlocking {
            doku.load()
            doku.setUp(DOKU_PASSWORD)
            tracking.setUp(PASSWORD)
        }
        write("Medikament A")
        tracking.lock()
        doku.lock()

        assertFalse(runBlocking { tracking.unlockWithPassword(DOKU_PASSWORD) })
        assertTrue(tracking.state is IslandState.Locked)
        assertTrue(runBlocking { tracking.unlockWithPassword(PASSWORD) })
        assertEquals("Medikament A", read())
    }

    @Test
    fun `deleting all empties the database, with the password, and Tracking stays open`() {
        runBlocking { tracking.setUp(PASSWORD) }
        write("Medikament A")

        assertTrue(runBlocking { tracking.deleteAll(PASSWORD) })

        assertNull(read())
        assertTrue(tracking.state is IslandState.Open)
        tracking.lock()
        assertTrue("the password stays", runBlocking { tracking.unlockWithPassword(PASSWORD) })
        assertNull(read())
    }

    @Test
    fun `deleting all with a wrong password deletes nothing`() {
        runBlocking { tracking.setUp(PASSWORD) }
        write("Medikament A")

        assertFalse(runBlocking { tracking.deleteAll("falsches Passwort") })

        assertEquals("Medikament A", read())
    }

    /** [millis] go by, on Tracking's clock and the test's virtual time alike. */
    private fun TestScope.wait(millis: Long) {
        clock += millis
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `while a measurement runs the five minutes rest, and start anew once it ends`() = runTest {
        runBlocking { tracking.setUp(PASSWORD) }
        backgroundScope.launch { tracking.lockWhenIdle() }
        val measuring = tracking.measuring()

        wait(3 * Tracking.IDLE_MILLIS)
        assertTrue(tracking.state is IslandState.Open)

        measuring.close()
        wait(Tracking.IDLE_MILLIS - 1)
        assertTrue(tracking.state is IslandState.Open)
        wait(1)
        assertTrue(tracking.state is IslandState.Locked)
    }

    @Test
    fun `a measurement does not keep Tracking from locking when it is left`() {
        runBlocking { tracking.setUp(PASSWORD) }
        tracking.measuring()

        tracking.lock()

        assertTrue(tracking.state is IslandState.Locked)
    }

    @Test
    fun `back from a trip out that took five minutes or more, Tracking locks`() {
        runBlocking { tracking.setUp(PASSWORD) }
        val short = tracking.goingOut()
        clock += Tracking.IDLE_MILLIS - 1
        short.close()
        assertTrue(tracking.state is IslandState.Open)

        val long = tracking.goingOut()
        clock += Tracking.IDLE_MILLIS
        long.close()

        assertTrue(tracking.state is IslandState.Locked)
    }

    private companion object {
        const val PASSWORD = "tracking-geheim"
        const val DOKU_PASSWORD = "doku-geheim-geheim"
    }
}
