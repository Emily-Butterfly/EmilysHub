package de.emilyshub.backup

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.doku.PatientDraft
import de.emilyshub.doku.PatientOrder
import de.emilyshub.island.IslandState
import de.emilyshub.routine
import de.emilyshub.tracking.DeviceKind
import de.emilyshub.tracking.EventKind
import de.emilyshub.tracking.EventPhoto
import de.emilyshub.tracking.GarminSample
import de.emilyshub.tracking.HealthSource
import de.emilyshub.tracking.SampleKind
import de.emilyshub.tracking.TrackingDatabase
import de.emilyshub.tracking.TrackingEvent
import de.emilyshub.tracking.TrackingSetting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * Tracking in the backups (SPEC.md §10, §17): taken along in its own encryption, without unlocking it, as the Doku is,
 * and put back only with the Tracking password that held when the backup was made; else it stays as it is.
 */
@RunWith(AndroidJUnit4::class)
class TrackingBackupTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val backups = app.backups
    private val tracking = app.tracking

    init {
        runBlocking {
            backups.setPassword(PASSWORD)
            tracking.load()
            app.doku.load()
        }
    }

    @After
    fun closeDatabases() {
        tracking.lock()
        app.doku.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
        app.deleteDatabase(DokuDatabase.NAME)
    }

    @Test
    fun `a backup takes Tracking along while it is locked, and brings it back once its password is given`() {
        setUpTracking()
        write("Medikament A")
        tracking.lock()
        val file = exported()
        assertEquals(IslandState.Locked(fingerprint = false), tracking.state)
        assertTrue(open(file).hasTracking)
        unlock()
        write("Medikament B")
        app.routine("Yoga", "18:00")

        restore(file, trackingPassword = TRACKING_PASSWORD)

        assertEquals(IslandState.Locked(fingerprint = false), tracking.state)
        unlock()
        assertEquals("Medikament A", read())
        assertEquals(emptyList<String>(), routineNames())
    }

    @Test
    fun `without Tracking set up the backup has none`() {
        val file = exported()

        assertFalse(open(file).hasTracking)
        assertFalse(contentOf(file).has("tracking"))
    }

    @Test
    fun `a wrong Tracking password restores nothing`() {
        setUpTracking()
        write("Medikament A")
        val file = exported()
        write("Medikament B")
        app.routine("Yoga", "18:00")
        tracking.lock()

        assertProblem(BackupProblem.WrongTrackingPassword) { restore(file, trackingPassword = "Falsch und falsch") }

        assertEquals(listOf("Yoga"), routineNames())
        unlock()
        assertEquals("Medikament B", read())
    }

    @Test
    fun `the backup password and the Doku password do not open Tracking`() {
        setUpTracking()
        runBlocking { app.doku.setUp(DOKU_PASSWORD) }
        val file = exported()

        val snapshot = checkNotNull(open(file).content.tracking)
        assertFalse(snapshot.opensWith(PASSWORD))
        assertFalse(snapshot.opensWith(DOKU_PASSWORD))
        assertProblem(BackupProblem.WrongTrackingPassword) { restore(file, trackingPassword = DOKU_PASSWORD) }
    }

    @Test
    fun `restored without Tracking, all else comes back and Tracking stays as it is`() {
        setUpTracking()
        write("Medikament A")
        val file = exported()
        write("Medikament B")
        app.routine("Yoga", "18:00")
        tracking.lock()

        restore(file, trackingPassword = null)

        assertEquals(emptyList<String>(), routineNames())
        unlock()
        assertEquals("Medikament B", read())
    }

    @Test
    fun `the Doku and Tracking come back together, each with its own password`() {
        setUpTracking()
        write("Medikament A")
        runBlocking { app.doku.setUp(DOKU_PASSWORD) }
        runBlocking { app.patients.create(PatientDraft("Frau M.")) }
        val file = exported()
        write("Medikament B")
        runBlocking { app.patients.create(PatientDraft("Herr K.")) }

        restore(file, dokuPassword = DOKU_PASSWORD, trackingPassword = TRACKING_PASSWORD)

        unlock()
        assertEquals("Medikament A", read())
        runBlocking { assertTrue(app.doku.unlockWithPassword(DOKU_PASSWORD)) }
        assertEquals(listOf("Frau M."), runBlocking { checkNotNull(app.patients.active(PatientOrder.Name)) }.map { it.name })
    }

    @Test
    fun `a wrong Tracking password leaves the Doku as it is too`() {
        setUpTracking()
        runBlocking { app.doku.setUp(DOKU_PASSWORD) }
        runBlocking { app.patients.create(PatientDraft("Frau M.")) }
        val file = exported()
        runBlocking { app.patients.create(PatientDraft("Herr K.")) }
        app.doku.lock()

        assertProblem(BackupProblem.WrongTrackingPassword) {
            restore(file, dokuPassword = DOKU_PASSWORD, trackingPassword = "Falsch und falsch")
        }

        runBlocking { assertTrue(app.doku.unlockWithPassword(DOKU_PASSWORD)) }
        assertEquals(2, runBlocking { checkNotNull(app.patients.active(PatientOrder.Name)) }.size)
    }

    @Test
    fun `a Tracking section of a newer app version is refused`() {
        setUpTracking()
        val content = contentOf(exported())
        val section = content.getJSONObject("tracking")

        assertProblem(BackupProblem.Damaged) { open(sealed(content.put("tracking", JSONObject(section.toString()).put("keys", "AAAA")))) }
        assertProblem(BackupProblem.NewerVersion) {
            open(sealed(content.put("tracking", JSONObject(section.toString()).put("schema", TrackingDatabase.VERSION + 1))))
        }
    }

    @Test
    fun `the values read from Health Connect are in the backup as copies`() {
        setUpTracking()
        val at = Instant.parse("2026-10-23T04:00:00Z")
        runBlocking {
            tracking.changing { it.garmin().put(listOf(GarminSample.of("hr#0", SampleKind.HeartRate, at, at, 64, HealthSource.GARMIN_CONNECT, DeviceKind.Watch))) }
        }
        val file = exported()
        runBlocking { tracking.deleteAll(TRACKING_PASSWORD) }

        restore(file, trackingPassword = TRACKING_PASSWORD)

        unlock()
        assertEquals(64L, runBlocking { tracking.withDatabase { it.garmin().overlapping(SampleKind.HeartRate, 0, Long.MAX_VALUE) } }?.single()?.value)
    }

    @Test
    fun `the photos of the events go along and come back`() {
        setUpTracking()
        val photo = byteArrayOf(-1, -40, 1, 2, 3)
        runBlocking {
            tracking.changing { database ->
                val event = database.events().insert(TrackingEvent(kind = EventKind.Bath, at = Instant.parse("2026-10-23T04:00:00Z")))
                database.photos().insert(EventPhoto(eventId = event, takenAt = Instant.parse("2026-10-23T04:05:00Z"), jpeg = photo))
            }
        }
        tracking.lock()
        val file = exported()
        unlock()
        runBlocking { tracking.deleteAll(TRACKING_PASSWORD) }

        restore(file, trackingPassword = TRACKING_PASSWORD)

        unlock()
        val kept = runBlocking { tracking.withDatabase { database -> database.events().all().flatMap { database.photos().of(it.id) }.map { database.photos().jpeg(it.id) } } }
        assertEquals(listOf(photo.toList()), kept?.map { it?.toList() })
    }

    @Test
    fun `deleting all empties what a backup of Tracking then holds`() {
        setUpTracking()
        write("Medikament A")
        runBlocking { assertTrue(tracking.deleteAll(TRACKING_PASSWORD)) }
        val file = exported()
        write("Medikament B")

        restore(file, trackingPassword = TRACKING_PASSWORD)

        unlock()
        assertNull(read())
    }

    private fun setUpTracking() = runBlocking { tracking.setUp(TRACKING_PASSWORD) }

    private fun unlock() = runBlocking { assertTrue(tracking.unlockWithPassword(TRACKING_PASSWORD)) }

    private fun write(value: String) {
        checkNotNull(runBlocking { tracking.withDatabase { it.settings().put(TrackingSetting("probe", value)) } })
    }

    private fun read(): String? = runBlocking { tracking.withDatabase { it.settings().get("probe") } }

    private fun routineNames() = runBlocking { app.routines.inUse().first() }.map { it.name }

    private fun exported(): ByteArray {
        val file = ByteArrayOutputStream()
        runBlocking { backups.export { file } }
        return file.toByteArray()
    }

    private fun open(file: ByteArray): Backup = runBlocking { backups.open({ ByteArrayInputStream(file) }, PASSWORD) }

    private fun restore(file: ByteArray, dokuPassword: String? = null, trackingPassword: String?) =
        runBlocking { backups.restore(open(file), dokuPassword, trackingPassword) }

    private fun contentOf(file: ByteArray) = JSONObject(String(BackupFile.open(file, PASSWORD).plaintext))

    private fun sealed(content: JSONObject) =
        BackupFile.seal(content.toString().toByteArray(), BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS))

    private fun assertProblem(problem: BackupProblem, block: () -> Unit) {
        assertEquals(problem, assertThrows(BackupException::class.java, block).problem)
    }

    private companion object {
        const val PASSWORD = "Flieder und Lila"
        const val DOKU_PASSWORD = "Doku-Geheimnis"
        const val TRACKING_PASSWORD = "Tracking-Geheimnis"
        const val FEW_ITERATIONS = 1_000
    }
}
