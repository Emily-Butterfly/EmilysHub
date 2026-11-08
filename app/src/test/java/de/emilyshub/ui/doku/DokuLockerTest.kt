package de.emilyshub.ui.doku

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuClipboard
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.doku.DokuState
import de.emilyshub.doku.FakeFingerprint
import de.emilyshub.doku.testDokuDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** When the screen locks the Doku (SPEC.md §14): on leaving it, and as the app leaves the screen or the screen goes off. */
@RunWith(AndroidJUnit4::class)
class DokuLockerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val doku = Doku(
        keyFile = File(context.noBackupFilesDir, "doku-locker-test.key"),
        databaseFile = context.getDatabasePath(DokuDatabase.NAME),
        fingerprint = FakeFingerprint(),
        openDatabase = { testDokuDatabase(context) },
        clipboard = DokuClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
    ).also { runBlocking { it.load(); it.setUp("geheim-geheim") } }

    private var rotating = false
    private val locker = DokuLocker(doku) { rotating }

    /** The activity showing the Doku, in front. */
    private val owner = object : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    }
    private val lifecycle: LifecycleRegistry = owner.lifecycle.apply {
        currentState = Lifecycle.State.RESUMED
        addObserver(locker)
    }

    @After
    fun closeDatabase() {
        doku.lock()
        context.deleteDatabase(DokuDatabase.NAME)
    }

    @Test
    fun `leaving the Doku locks it`() {
        locker.left()

        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
    }

    @Test
    fun `the app leaving the screen, or the screen going off, locks the Doku at once`() {
        // Both stop the activity.
        lifecycle.currentState = Lifecycle.State.CREATED

        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
    }

    @Test
    fun `merely pausing, as under a dialog of the system, keeps it open`() {
        lifecycle.currentState = Lifecycle.State.STARTED

        assertEquals(DokuState.Open(fingerprint = false), doku.state)
    }

    @Test
    fun `a rotation keeps it open`() {
        rotating = true

        lifecycle.currentState = Lifecycle.State.CREATED
        locker.left()

        assertEquals(DokuState.Open(fingerprint = false), doku.state)
    }
}
