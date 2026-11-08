package de.emilyshub.doku

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.File

@RunWith(AndroidJUnit4::class)
class DokuTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val keyFile = File(context.noBackupFilesDir, "doku-test.key")
    private val fingerprint = FakeFingerprint()
    private var clock = 1_000_000L
    private val opened = mutableListOf<ByteArray>()
    private val clipboard = context.getSystemService(ClipboardManager::class.java)

    /** The Doku as a new process finds it. */
    private fun newDoku() = Doku(
        keyFile = keyFile,
        fingerprint = fingerprint,
        openDatabase = { rawKey -> opened += rawKey.bytes; testDokuDatabase(context) },
        clipboard = DokuClipboard(context),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = 1_000,
        now = { clock },
    ).also { runBlocking { it.load() } }

    private val doku = newDoku()

    @After
    fun closeDatabase() {
        doku.lock()
        context.deleteDatabase(DokuDatabase.NAME)
    }

    private val activity = Robolectric.buildActivity(Activity::class.java).get()

    private fun setUp(password: String = PASSWORD) = runBlocking { doku.setUp(password) }

    /** What [block] comes to once the fingerprint's prompt it shows is answered by [answer]. */
    private fun <T> answering(block: suspend () -> T, answer: FakeFingerprint.() -> Unit): T {
        val result = CoroutineScope(Dispatchers.Unconfined).async { block() }
        fingerprint.answer()
        return runBlocking { result.await() }
    }

    private fun DokuDatabase.write(value: String) = runBlocking { settings().put(DokuSetting("probe", value)) }

    private fun DokuDatabase.read() = runBlocking { settings().get("probe") }

    @Test
    fun `at first the Doku has no password, and setting it opens it`() {
        assertEquals(DokuState.New, doku.state)

        setUp()

        assertEquals(DokuState.Open(fingerprint = false), doku.state)
    }

    @Test
    fun `the Doku password has at least eight characters, typed twice alike`() {
        assertEquals(DokuPasswordProblem.TooShort, Doku.passwordProblem("1234567", "1234567"))
        assertEquals(DokuPasswordProblem.NotRepeated, Doku.passwordProblem("12345678", "12345679"))
        assertNull(Doku.passwordProblem("12345678", "12345678"))
        assertThrows(IllegalArgumentException::class.java) { setUp("1234567") }
    }

    @Test
    fun `the right password opens the Doku with its data`() {
        setUp()
        doku.database.write("Frau M.")
        doku.lock()

        assertTrue(runBlocking { doku.unlockWithPassword(PASSWORD) })

        assertEquals(DokuState.Open(fingerprint = false), doku.state)
        assertEquals("Frau M.", doku.database.read())
    }

    @Test
    fun `a wrong password leaves the Doku locked`() {
        setUp()
        doku.lock()

        assertFalse(runBlocking { doku.unlockWithPassword("falsches Passwort") })

        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
        assertThrows(IllegalStateException::class.java) { doku.database }
    }

    @Test
    fun `a new process finds the Doku locked`() {
        setUp()

        val afterRestart = newDoku()

        assertEquals(DokuState.Locked(fingerprint = false), afterRestart.state)
        assertTrue(runBlocking { afterRestart.unlockWithPassword(PASSWORD) })
        afterRestart.lock()
    }

    @Test
    fun `the database key is random and never stored in plain`() {
        setUp()
        val key = opened.single().copyOf()
        doku.lock()

        val stored = keyFile.readBytes()

        val plain = String(key, 2, 64).hexToByteArray()
        assertFalse(stored.toList().windowed(plain.size).any { it.toByteArray().contentEquals(plain) })
    }

    @Test
    fun `locking closes the database and wipes the key from memory`() {
        setUp()
        val database = doku.database

        doku.lock()

        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
        assertFalse(database.isOpen)
        assertTrue(opened.single().all { it == 0.toByte() })
        assertThrows(IllegalStateException::class.java) { doku.database }
    }

    @Test
    fun `what an editor has typed is saved before the Doku locks`() {
        setUp()
        val saver = doku.addSaver { { database -> database.write("ungespeichert") } }

        doku.lock()
        saver.close()
        runBlocking { doku.unlockWithPassword(PASSWORD) }

        assertEquals("ungespeichert", doku.database.read())
    }

    @Test
    fun `changing the password keeps the data and only the new one opens the Doku`() {
        setUp()
        doku.database.write("Herr K.")

        assertTrue(runBlocking { doku.changePassword(PASSWORD, "neues Passwort") })
        doku.lock()

        assertFalse(runBlocking { doku.unlockWithPassword(PASSWORD) })
        assertTrue(runBlocking { doku.unlockWithPassword("neues Passwort") })
        assertEquals("Herr K.", doku.database.read())
    }

    @Test
    fun `changing the password asks for the old one`() {
        setUp()

        assertFalse(runBlocking { doku.changePassword("falsches Passwort", "neues Passwort") })

        doku.lock()
        assertTrue(runBlocking { doku.unlockWithPassword(PASSWORD) })
    }

    @Test
    fun `after five minutes without a touch the Doku locks`() {
        setUp()

        clock += Doku.IDLE_MILLIS - 1
        doku.lockIfIdle()
        assertTrue(doku.state is DokuState.Open)

        clock += 1
        doku.lockIfIdle()
        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
    }

    @Test
    fun `a touch or a key starts the five minutes anew`() {
        setUp()

        clock += Doku.IDLE_MILLIS - 1
        doku.touched()
        clock += Doku.IDLE_MILLIS - 1
        doku.lockIfIdle()

        assertTrue(doku.state is DokuState.Open)
        assertEquals(1L, doku.millisUntilIdle())
    }

    @Test
    fun `the fingerprint, once on, opens the Doku`() {
        fingerprint.enrolled = true
        setUp()
        assertTrue(answering({ doku.turnOnFingerprint(activity) }) { succeed() })
        assertEquals(FingerprintPurpose.TurnOn, fingerprint.lastPurpose)
        doku.database.write("Frau M.")
        doku.lock()

        assertEquals(DokuState.Locked(fingerprint = true), doku.state)
        val result = answering({ doku.unlockWithFingerprint(activity) }) { succeed() }

        assertEquals(FingerprintUnlock.Opened, result)
        assertEquals(FingerprintPurpose.Unlock, fingerprint.lastPurpose)
        assertEquals(DokuState.Open(fingerprint = true), doku.state)
        assertEquals("Frau M.", doku.database.read())
    }

    @Test
    fun `the prompt's "Passwort" asks for the password, and dismissing it leaves the Doku locked to ask again`() {
        fingerprint.enrolled = true
        setUp()
        answering({ doku.turnOnFingerprint(activity) }) { succeed() }
        doku.lock()

        assertEquals(FingerprintUnlock.Password, answering({ doku.unlockWithFingerprint(activity) }) { chooseOther() })
        assertEquals(FingerprintUnlock.Dismissed, answering({ doku.unlockWithFingerprint(activity) }) { dismiss() })
        assertEquals(DokuState.Locked(fingerprint = true), doku.state)
    }

    @Test
    fun `turning the fingerprint on, cancelled, leaves it off`() {
        fingerprint.enrolled = true
        setUp()

        assertFalse(answering({ doku.turnOnFingerprint(activity) }) { chooseOther() })

        assertEquals(DokuState.Open(fingerprint = false), doku.state)
    }

    @Test
    fun `a fingerprint whose key does not open the Doku asks for the password`() {
        fingerprint.enrolled = true
        setUp()
        answering({ doku.turnOnFingerprint(activity) }) { succeed() }
        doku.lock()

        val result = answering({ doku.unlockWithFingerprint(activity) }) {
            // As if the key had changed under the cipher.
            succeedWith { it.updateAAD(byteArrayOf(1)) }
        }

        assertEquals(FingerprintUnlock.Password, result)
        assertEquals(DokuState.Locked(fingerprint = true), doku.state)
    }

    @Test
    fun `a fingerprint confirmed only once the Doku has locked again turns nothing on`() {
        fingerprint.enrolled = true
        setUp()

        val turnedOn = answering({ doku.turnOnFingerprint(activity) }) {
            doku.lock()
            succeed()
        }

        assertFalse(turnedOn)
        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
        assertEquals(DokuState.Locked(fingerprint = false), newDoku().state)
    }

    @Test
    fun `a fingerprint recognised only once the Doku was told to lock leaves it locked`() {
        fingerprint.enrolled = true
        setUp()
        answering({ doku.turnOnFingerprint(activity) }) { succeed() }
        doku.lock()

        answering({ doku.unlockWithFingerprint(activity) }) {
            doku.lock()
            succeed()
        }

        assertEquals(DokuState.Locked(fingerprint = true), doku.state)
    }

    @Test
    fun `an unlock still under way when the Doku is told to lock leaves it locked`() {
        setUp()
        doku.lock()
        val racing = Doku(
            keyFile = keyFile,
            fingerprint = fingerprint,
            // The app leaves the screen just while the database opens.
            openDatabase = { racingDoku.lock(); testDokuDatabase(context) },
            clipboard = DokuClipboard(context),
            io = Dispatchers.Unconfined,
            background = CoroutineScope(Dispatchers.Unconfined),
            iterations = 1_000,
            now = { clock },
        ).also { racingDoku = it; runBlocking { it.load() } }

        runBlocking { racing.unlockWithPassword(PASSWORD) }

        assertEquals(DokuState.Locked(fingerprint = false), racing.state)
        assertThrows(IllegalStateException::class.java) { racing.database }
    }

    private lateinit var racingDoku: Doku

    @Test
    fun `the fingerprint is kept off across a restart`() {
        fingerprint.enrolled = true
        setUp()
        answering({ doku.turnOnFingerprint(activity) }) { succeed() }
        runBlocking { doku.turnOffFingerprint() }

        assertEquals(DokuState.Locked(fingerprint = false), newDoku().state)
        assertFalse(fingerprint.hasKey)
    }

    @Test
    fun `a new fingerprint on the phone turns the fingerprint off and asks for the password, also after a restart`() {
        fingerprint.enrolled = true
        setUp()
        answering({ doku.turnOnFingerprint(activity) }) { succeed() }
        doku.lock()

        fingerprint.enrollNew()

        assertEquals(FingerprintUnlock.Password, runBlocking { doku.unlockWithFingerprint(activity) })
        assertEquals(1, fingerprint.prompts) // only to turn it on
        val lost = DokuState.Locked(fingerprint = false, fingerprintLost = true)
        assertEquals(lost, doku.state)
        assertEquals(lost, newDoku().state)
        assertFalse(fingerprint.hasKey)
    }

    @Test
    fun `after a lost fingerprint the password opens the Doku and offers to turn it on again`() {
        fingerprint.enrolled = true
        setUp()
        answering({ doku.turnOnFingerprint(activity) }) { succeed() }
        doku.lock()
        fingerprint.enrollNew()
        runBlocking { doku.unlockWithFingerprint(activity) }

        runBlocking { doku.unlockWithPassword(PASSWORD) }

        assertEquals(DokuState.Open(fingerprint = false, offerFingerprint = true), doku.state)
        assertTrue(answering({ doku.turnOnFingerprint(activity) }) { succeed() })
        assertEquals(DokuState.Open(fingerprint = true), doku.state)
        doku.lock()
        assertEquals(DokuState.Locked(fingerprint = true), newDoku().state)
    }

    @Test
    fun `the offer declined is not made again`() {
        fingerprint.enrolled = true
        setUp()
        answering({ doku.turnOnFingerprint(activity) }) { succeed() }
        doku.lock()
        fingerprint.enrollNew()
        runBlocking { doku.unlockWithFingerprint(activity) }
        runBlocking { doku.unlockWithPassword(PASSWORD) }

        runBlocking { doku.declineFingerprint() }
        doku.lock()

        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
        assertTrue(runBlocking { doku.unlockWithPassword(PASSWORD) })
        assertEquals(DokuState.Open(fingerprint = false), doku.state)
    }

    @Test
    fun `text copied from the Doku is marked sensitive and cleared when it locks`() {
        setUp()
        val clip = doku.clipboard.markCopied(ClipData.newPlainText("", "Diagnose"))
        clipboard.setPrimaryClip(clip)

        doku.lock()

        assertTrue(clip.description.extras.getBoolean("android.content.extra.IS_SENSITIVE"))
        assertFalse(clipboard.hasPrimaryClip())
    }

    @Test
    fun `something copied elsewhere since stays on the clipboard`() {
        val copied = DokuClipboard(context)
        clipboard.setPrimaryClip(copied.markCopied(ClipData.newPlainText("", "Diagnose")))
        clipboard.setPrimaryClip(ClipData.newPlainText("", "Einkaufsliste"))

        copied.clearIfFromDoku()

        assertEquals("Einkaufsliste", clipboard.primaryClip?.getItemAt(0)?.text)
    }

    private companion object {
        const val PASSWORD = "Doku-Passwort"
    }
}
