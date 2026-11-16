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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import de.emilyshub.island.IslandFile
import de.emilyshub.island.IslandVault
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.time.Instant
import javax.crypto.KeyGenerator
import java.util.Base64
import kotlin.random.Random

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
        val file = exported()
        val content = contentOf(file)
        val section = content.getJSONObject("tracking")

        assertProblem(BackupProblem.Damaged) { open(sealed(content.put("tracking", JSONObject(section.toString()).put("keys", "AAAA")), file)) }
        assertProblem(BackupProblem.NewerVersion) {
            open(sealed(content.put("tracking", JSONObject(section.toString()).put("schema", TrackingDatabase.VERSION + 1)), file))
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
        addPhotos(listOf(photo))
        tracking.lock()
        val file = exported()
        assertFalse("the photo's file lies encrypted", file.containsBytes(photo))
        unlock()
        runBlocking { tracking.deleteAll(TRACKING_PASSWORD) }

        restore(file, trackingPassword = TRACKING_PASSWORD)

        unlock()
        assertEquals(listOf(photo.toList()), photos())
    }

    @Test
    fun `a photo deleted while a backup is written stays for it, and goes once it is written`() {
        setUpTracking()
        addPhotos(listOf(byteArrayOf(1, 2, 3)))
        val id = runBlocking { checkNotNull(tracking.withDatabase { database -> database.events().all().flatMap { database.photos().of(it.id) }.single().id }) }
        val events = app.trackingModules.events

        val listed = runBlocking {
            tracking.reading { snapshot ->
                val listed = checkNotNull(snapshot).files.single()
                events.deletePhoto(id)
                assertArrayEquals(listed.read(), listed.read())
                listed
            }
        }

        assertNull(runBlocking { events.photo(id) })
        assertFalse(listed.at.exists())
    }

    @Test
    fun `the photos of a backup opened before another are gone, and it is to be opened again`() {
        setUpTracking()
        addPhotos(listOf(byteArrayOf(1, 2, 3)))
        val first = open(exported())
        runBlocking { tracking.deleteAll(TRACKING_PASSWORD) }
        // Another backup, without the photo, laid aside in its place.
        open(exported())
        unlock()
        addPhotos(listOf(byteArrayOf(4, 5)))
        tracking.lock()

        assertProblem(BackupProblem.Damaged) { runBlocking { backups.restore(first, trackingPassword = TRACKING_PASSWORD) } }

        unlock()
        assertEquals("Tracking as it was", listOf(listOf<Byte>(4, 5)), photos())
    }

    @Test
    fun `a backup whose photos find no room on the phone as it is opened is told so, not as unreadable`() {
        setUpTracking()
        addPhotos(listOf(byteArrayOf(1, 2, 3)))
        val file = exported()
        val full = object : IslandVault by tracking {
            override fun putAside(name: String, bytes: ByteArray): IslandFile? = throw IOException("No space left on device")
        }
        val openingHere = Backups(
            database = { app.database },
            settings = { app.database.settings() },
            wrappingKey = { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey() },
            clock = app.clock,
            migration = BackupMigration(app) { FrameworkSQLiteOpenHelperFactory() },
            doku = app.doku,
            tracking = full,
            attachments = app.lessonFiles,
            io = Dispatchers.Unconfined,
            iterations = FEW_ITERATIONS,
        )

        assertProblem(BackupProblem.NoSpace) { runBlocking { openingHere.open({ ByteArrayInputStream(file) }, PASSWORD) } }
    }

    @Test
    fun `photos more than a backup's content can hold go along, in records of their own`() {
        setUpTracking()
        // Each a megabyte of bytes that do not shrink, more than the content of a backup holds in all.
        val photos = List(BackupFile.MAX_BYTES / MEGABYTE + 2) { index -> Random(index).nextBytes(MEGABYTE) }
        addPhotos(photos)
        tracking.lock()
        val file = exported()
        assertTrue(file.size > BackupFile.MAX_BYTES)
        val opened = BackupFile.open(file, PASSWORD)
        assertTrue(opened.plaintext.size < MEGABYTE)
        assertTrue("each photo a record of its own", opened.attachments.size > photos.size)
        unlock()
        runBlocking { tracking.deleteAll(TRACKING_PASSWORD) }

        restore(file, trackingPassword = TRACKING_PASSWORD)

        unlock()
        assertEquals(photos.map { it.toList() }, photos())
    }

    @Test
    fun `a piece of Tracking's database missing or changed in size is damage`() {
        setUpTracking()
        write("Medikament A")
        val file = exported()
        val opened = BackupFile.open(file, PASSWORD)
        val key = BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS)
        val pieces = opened.attachments

        assertProblem(BackupProblem.Damaged) { open(BackupFile.seal(opened.plaintext, key, pieces.drop(1))) }
        assertProblem(BackupProblem.Damaged) { open(BackupFile.seal(opened.plaintext, key, listOf(pieces.first() + 0) + pieces.drop(1))) }
        assertProblem(BackupProblem.Damaged) { open(BackupFile.seal(opened.plaintext, key, pieces + byteArrayOf(0))) }
    }

    @Test
    fun `a backup that held Tracking in its content, as before format 3, still brings it back`() {
        setUpTracking()
        write("Medikament A")
        tracking.lock()
        val file = exported()
        val opened = BackupFile.open(file, PASSWORD)
        val content = JSONObject(String(opened.plaintext))
        val section = content.getJSONObject("tracking")
        val database = opened.attachments.fold(ByteArray(0)) { all, piece -> all + piece }
        assertEquals(section.getInt("database"), database.size)
        assertFalse(section.has("wal"))
        content.put("tracking", section.put("database", Base64.getEncoder().encodeToString(database)))
        val older = BackupFile.seal2(content.toString().toByteArray(), BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS))
        unlock()
        write("Medikament B")

        restore(older, trackingPassword = TRACKING_PASSWORD)

        unlock()
        assertEquals("Medikament A", read())
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

    /** [jpegs] as the photos of a new event, as Tracking keeps them, each in a file of its own. */
    private fun addPhotos(jpegs: List<ByteArray>) {
        checkNotNull(
            runBlocking {
                tracking.changingWithFiles { database, files ->
                    val event = database.events().insert(TrackingEvent(kind = EventKind.Bath, at = Instant.parse("2026-10-23T04:00:00Z")))
                    jpegs.forEach { jpeg ->
                        val name = files.newName()
                        files.write(name, jpeg)
                        database.photos().insert(EventPhoto(eventId = event, takenAt = Instant.parse("2026-10-23T04:05:00Z"), file = name))
                    }
                }
            },
        )
    }

    /** The photos of all events, as kept. */
    private fun photos(): List<List<Byte>>? = runBlocking {
        tracking.withFiles { database, files ->
            database.events().all().flatMap { database.photos().of(it.id) }.map { checkNotNull(database.photos().file(it.id)?.let(files::read)).toList() }
        }
    }

    private fun ByteArray.containsBytes(part: ByteArray) = (0..size - part.size).any { start -> part.indices.all { this[start + it] == part[it] } }

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

    /** [content] in place of the content of [file], the records that follow it kept. */
    private fun sealed(content: JSONObject, file: ByteArray) = BackupFile.seal(
        content.toString().toByteArray(),
        BackupKey.derive(PASSWORD, iterations = FEW_ITERATIONS),
        BackupFile.open(file, PASSWORD).attachments,
    )

    private fun assertProblem(problem: BackupProblem, block: () -> Unit) {
        assertEquals(problem, assertThrows(BackupException::class.java, block).problem)
    }

    private companion object {
        const val PASSWORD = "Flieder und Lila"
        const val DOKU_PASSWORD = "Doku-Geheimnis"
        const val TRACKING_PASSWORD = "Tracking-Geheimnis"
        const val FEW_ITERATIONS = 1_000
        const val MEGABYTE = 1024 * 1024
    }
}
