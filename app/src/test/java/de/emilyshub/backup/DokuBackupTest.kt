package de.emilyshub.backup

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuClipboard
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.doku.DokuState
import de.emilyshub.doku.PatientDraft
import de.emilyshub.doku.PatientOrder
import de.emilyshub.doku.FakeFingerprint
import de.emilyshub.doku.createDokuAsExported
import de.emilyshub.doku.testDokuDatabase
import de.emilyshub.routine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The Doku in the backups (SPEC.md §10, §14): taken along in its own encryption, without unlocking it, and put back
 * only with the Doku password that held when the backup was made; else it stays as it is.
 */
@RunWith(AndroidJUnit4::class)
class DokuBackupTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val backups = app.backups
    private val doku = app.doku
    private val databaseFile = app.getDatabasePath(DokuDatabase.NAME)

    init {
        runBlocking {
            backups.setPassword(PASSWORD)
            doku.load()
        }
    }

    @After
    fun closeDatabase() {
        doku.lock()
        app.deleteDatabase(DokuDatabase.NAME)
    }

    @Test
    fun `a backup brings back the Doku, once its password is given`() {
        setUpDoku()
        patient("Frau M.")
        doku.lock()
        val file = exported()
        unlock()
        patient("Herr K.")
        app.routine("Yoga", "18:00")

        restore(file, dokuPassword = DOKU_PASSWORD)

        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
        unlock()
        assertEquals(listOf("Frau M."), patients())
        assertEquals(emptyList<String>(), routineNames())
    }

    @Test
    fun `the Doku is backed up while it is open as it is then, and stays open`() {
        setUpDoku()
        patient("Frau M.")
        val file = exported()
        assertEquals(DokuState.Open(fingerprint = false), doku.state)
        patient("Herr K.")

        restore(file, dokuPassword = DOKU_PASSWORD)

        unlock()
        assertEquals(listOf("Frau M."), patients())
    }

    @Test
    fun `a backup takes the Doku along while it is locked, without asking for its password`() {
        setUpDoku()
        patient("Frau M.")
        doku.lock()

        val backup = open(exported())

        assertTrue(backup.hasDoku)
        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
    }

    @Test
    fun `without a Doku set up the backup has none`() {
        val file = exported()

        assertFalse(open(file).hasDoku)
        assertFalse(contentOf(file).has("doku"))
    }

    @Test
    fun `a wrong Doku password restores nothing`() {
        setUpDoku()
        patient("Frau M.")
        val file = exported()
        patient("Herr K.")
        app.routine("Yoga", "18:00")
        doku.lock()

        assertProblem(BackupProblem.WrongDokuPassword) { restore(file, dokuPassword = "Falsch und falsch") }

        assertEquals(listOf("Yoga"), routineNames())
        unlock()
        assertEquals(listOf("Frau M.", "Herr K."), patients())
    }

    @Test
    fun `restored without the Doku, all else comes back and the Doku stays as it is`() {
        setUpDoku()
        patient("Frau M.")
        val file = exported()
        patient("Herr K.")
        app.routine("Yoga", "18:00")
        doku.lock()

        restore(file, dokuPassword = null)

        assertEquals(emptyList<String>(), routineNames())
        unlock()
        assertEquals(listOf("Frau M.", "Herr K."), patients())
    }

    @Test
    fun `a backup without a Doku, as every older one, leaves the Doku as it is`() {
        val file = exported()
        setUpDoku()
        patient("Frau M.")
        doku.lock()

        restore(file, dokuPassword = DOKU_PASSWORD)

        unlock()
        assertEquals(listOf("Frau M."), patients())
    }

    @Test
    fun `after restoring, the Doku password of the backup holds and the fingerprint is off`() {
        setUpDoku()
        patient("Frau M.")
        val file = exported()
        runBlocking { assertTrue(doku.changePassword(DOKU_PASSWORD, "Ganz neues Passwort")) }
        app.fingerprint.enrolled = true
        turnOnFingerprint()
        doku.lock()

        restore(file, dokuPassword = DOKU_PASSWORD)

        assertEquals(DokuState.Locked(fingerprint = false), doku.state)
        assertFalse(app.fingerprint.hasKey)
        runBlocking { assertFalse(doku.unlockWithPassword("Ganz neues Passwort")) }
        unlock()
        turnOnFingerprint()
        assertEquals(DokuState.Open(fingerprint = true), doku.state)
    }

    @Test
    fun `the backup holds the key sealed under the Doku password, not under the fingerprint`() {
        setUpDoku()
        app.fingerprint.enrolled = true
        turnOnFingerprint()

        val backup = open(exported())

        val snapshot = checkNotNull(backup.content.doku)
        assertFalse(snapshot.keys.hasFingerprint)
        assertTrue(snapshot.opensWith(DOKU_PASSWORD))
    }

    @Test
    fun `the backup password alone does not open the Doku`() {
        setUpDoku()
        patient("Frau M.")
        val file = exported()

        assertFalse(checkNotNull(open(file).content.doku).opensWith(PASSWORD))
        assertProblem(BackupProblem.WrongDokuPassword) { restore(file, dokuPassword = PASSWORD) }
    }

    @Test
    fun `a Doku section changed is refused`() {
        setUpDoku()
        val content = contentOf(exported())
        val doku = content.getJSONObject("doku")

        assertProblem(BackupProblem.Damaged) { open(sealed(content.put("doku", JSONObject(doku.toString()).put("keys", "AAAA")))) }
        assertProblem(BackupProblem.Damaged) { open(sealed(content.put("doku", JSONObject(doku.toString()).put("keys", "kein Base64")))) }
        assertProblem(BackupProblem.Damaged) { open(sealed(content.put("doku", JSONObject(doku.toString()).put("more", "")))) }
        assertProblem(BackupProblem.Damaged) { open(sealed(content.put("doku", JSONObject(doku.toString()).apply { remove("database") }))) }
        assertProblem(BackupProblem.Damaged) { open(sealed(content.put("doku", "Doku"))) }
        assertProblem(BackupProblem.NewerVersion) {
            open(sealed(content.put("doku", JSONObject(doku.toString()).put("schema", DokuDatabase.VERSION + 1))))
        }
    }

    @Test
    fun `the Doku counts towards the size of a backup`() {
        setUpDoku()
        doku.lock()
        databaseFile.appendBytes(ByteArray(BackupFile.MAX_BYTES))

        assertProblem(BackupProblem.TooLarge) { exported() }
    }

    @Test
    fun `a Doku of an older app version is brought up to date when it is first opened`() {
        setUpDoku()
        doku.lock()
        app.deleteDatabase(DokuDatabase.NAME)
        createDokuAsExported(app, version = 1) { it.execSQL("INSERT INTO doku_setting (`key`, value) VALUES ('probe', 'kept')") }
        val file = exported()
        app.deleteDatabase(DokuDatabase.NAME)

        restore(file, dokuPassword = DOKU_PASSWORD)

        unlock()
        assertEquals(emptyList<String>(), patients())
        assertEquals("kept", runBlocking { doku.withDatabase { it.settings().get("probe") } })
    }

    @Test
    fun `a Doku put back where there was none has the password of the backup`() {
        setUpDoku()
        patient("Frau M.")
        val snapshot = runBlocking { checkNotNull(doku.snapshot()) }
        doku.lock()
        app.deleteDatabase(DokuDatabase.NAME)
        val elsewhere = dokuAt(File(app.noBackupFilesDir, "doku-elsewhere.key"))
        assertEquals(DokuState.New, elsewhere.state)

        runBlocking { elsewhere.replacedBy(snapshot) {} }

        assertEquals(DokuState.Locked(fingerprint = false), elsewhere.state)
        runBlocking { assertTrue(elsewhere.unlockWithPassword(DOKU_PASSWORD)) }
        assertEquals(listOf("Frau M."), runBlocking { elsewhere.withDatabase { db -> db.patients().all().map { it.name } } })
        elsewhere.lock()
    }

    @Test
    fun `a restore cut off while the files were put in place is undone at the next start`() {
        setUpDoku()
        patient("Frau M.")
        doku.lock()
        val keyFile = File(app.noBackupFilesDir, "doku.key")
        // Killed after the files before were set aside, the first of the backup's already in place.
        keyFile.renameTo(File("${keyFile.path}.old"))
        databaseFile.renameTo(File("${databaseFile.path}.old"))
        databaseFile.writeBytes(ByteArray(10))
        File("${keyFile.path}.restore").writeBytes(ByteArray(10))

        val restarted = dokuAt(keyFile)

        assertEquals(DokuState.Locked(fingerprint = false), restarted.state)
        runBlocking { assertTrue(restarted.unlockWithPassword(DOKU_PASSWORD)) }
        assertEquals(listOf("Frau M."), runBlocking { restarted.withDatabase { db -> db.patients().all().map { it.name } } })
        restarted.lock()
        assertEquals(emptyList<String>(), leftOver())
    }

    @Test
    fun `a restore cut off once the files were in place keeps them, the files before deleted`() {
        setUpDoku()
        doku.lock()
        val keyFile = File(app.noBackupFilesDir, "doku.key")
        keyFile.copyTo(File("${keyFile.path}.old"))
        databaseFile.copyTo(File("${databaseFile.path}.old"))

        val restarted = dokuAt(keyFile)

        assertEquals(DokuState.Locked(fingerprint = false), restarted.state)
        assertEquals(emptyList<String>(), leftOver())
    }

    @Test
    fun `when the rest fails to restore, the Doku is put back as it was`() {
        setUpDoku()
        patient("Frau M.")
        val snapshot = runBlocking { checkNotNull(doku.snapshot()) }
        patient("Herr K.")
        doku.lock()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { doku.replacedBy(snapshot) { error("The rest failed") } }
        }

        unlock()
        assertEquals(listOf("Frau M.", "Herr K."), patients())
        assertEquals(emptyList<String>(), leftOver())
    }

    /** The files kept aside or staged by a restore that are still there. */
    private fun leftOver() = listOf(databaseFile.parentFile, app.noBackupFilesDir)
        .flatMap { it?.listFiles().orEmpty().toList() }
        .map { it.name }
        .filter { it.endsWith(".old") || it.endsWith(".restore") }

    /** A Doku over the database here and [keyFile], as a new process finds it. */
    private fun dokuAt(keyFile: File) = Doku(
        keyFile = keyFile,
        databaseFile = databaseFile,
        fingerprint = FakeFingerprint(),
        openDatabase = { testDokuDatabase(app) },
        clipboard = DokuClipboard(app),
        io = Dispatchers.Unconfined,
        background = CoroutineScope(Dispatchers.Unconfined),
        iterations = FEW_ITERATIONS,
    ).also { runBlocking { it.load() } }

    private fun setUpDoku() = runBlocking { doku.setUp(DOKU_PASSWORD) }

    private fun unlock() = runBlocking { assertTrue(doku.unlockWithPassword(DOKU_PASSWORD)) }

    private fun turnOnFingerprint() {
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        val turning = CoroutineScope(Dispatchers.Unconfined).async { doku.turnOnFingerprint(activity) }
        app.fingerprint.succeed()
        runBlocking { assertTrue(turning.await()) }
    }

    private fun patient(name: String) = runBlocking { checkNotNull(app.patients.create(PatientDraft(name))) }

    private fun patients() = runBlocking { checkNotNull(app.patients.active(PatientOrder.Name)) }.map { it.name }.sorted()

    private fun routineNames() = runBlocking { app.routines.inUse().first() }.map { it.name }

    private fun exported(): ByteArray {
        val file = ByteArrayOutputStream()
        runBlocking { backups.export { file } }
        return file.toByteArray()
    }

    private fun open(file: ByteArray): Backup = runBlocking { backups.open({ ByteArrayInputStream(file) }, PASSWORD) }

    private fun restore(file: ByteArray, dokuPassword: String?) = runBlocking { backups.restore(open(file), dokuPassword) }

    private fun contentOf(file: ByteArray) = JSONObject(String(BackupFile.open(file, PASSWORD).plaintext))

    private fun sealed(content: JSONObject) =
        BackupFile.seal(content.toString().toByteArray(), BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS))

    private fun assertProblem(problem: BackupProblem, block: () -> Unit) {
        assertEquals(problem, assertThrows(BackupException::class.java, block).problem)
    }

    private companion object {
        const val PASSWORD = "Flieder und Lila"
        const val DOKU_PASSWORD = "Doku-Geheimnis"
        const val FEW_ITERATIONS = 1_000
    }
}
