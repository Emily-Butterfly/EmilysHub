package de.emilyshub.backup

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.alarmsFor
import de.emilyshub.routine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The backups made each night (SPEC.md §10) into a folder chosen once, with the key kept of the backup password: the
 * last 7 stay, and Heute is told when the last backup is over 30 days old or failed.
 */
@RunWith(AndroidJUnit4::class)
class NightlyBackupsTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val nightly = app.nightlyBackups
    private val folder = app.backupFolder
    private val alarmManager = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    @Test
    fun `a folder chosen keeps its access, and a backup is made there right away, to open with the password`() {
        app.routine("Vitamine", "07:30")
        setPassword()

        assertTrue(runBlocking { nightly.choose(FOLDER) })

        assertTrue(folder.kept)
        assertEquals(listOf("EmilysHub-Backup-2026-10-23.emhb"), folder.fileNames())
        val backup = runBlocking { app.backups.open({ ByteArrayInputStream(folder.files.values.single()) }, PASSWORD) }
        assertEquals(1, backup.summary.routines)
        assertEquals("Backups", runBlocking { nightly.state() }.folderName)
    }

    @Test
    fun `the next backup comes at 3 at night, and after it the one of the night after`() {
        setPassword()
        runBlocking { nightly.choose(FOLDER) }
        assertEquals(listOf(berlin("2026-10-24T03:00")), nights())

        night("2026-10-24T03:00")

        assertEquals(listOf("EmilysHub-Backup-2026-10-23.emhb", "EmilysHub-Backup-2026-10-24.emhb"), folder.fileNames())
        assertEquals(listOf(berlin("2026-10-25T03:00")), nights())
    }

    @Test
    fun `the last 7 backups stay, older ones are deleted, and other files in the folder are left alone`() {
        folder.files["Urlaub.jpg"] = byteArrayOf(1)
        setPassword()
        runBlocking { nightly.choose(FOLDER) }

        (24..31).forEach { night("2026-10-${it}T03:00") }

        assertEquals(
            (25..31).map { "EmilysHub-Backup-2026-10-$it.emhb" } + "Urlaub.jpg",
            folder.fileNames().sorted(),
        )
    }

    @Test
    fun `a night missed while the phone was off is made up as soon as it is on again`() {
        setPassword()
        runBlocking { nightly.choose(FOLDER) }

        app.clock.now = berlin("2026-10-26T09:15")
        runBlocking { nightly.schedule() }

        assertEquals(listOf(berlin("2026-10-26T09:15")), nights())
    }

    @Test
    fun `a backup that fails is told on Heute until one is made again`() {
        setPassword()
        runBlocking { nightly.choose(FOLDER) }
        folder.failing = true

        night("2026-10-24T03:00")

        assertEquals(BackupWarning.Failed, runBlocking { nightly.warning() })
        assertTrue(runBlocking { nightly.state() }.failed)
        // It is tried again the next night all the same.
        assertEquals(listOf(berlin("2026-10-25T03:00")), nights())

        folder.failing = false
        night("2026-10-25T03:00")
        assertNull(runBlocking { nightly.warning() })
    }

    @Test
    fun `Heute is told once the last backup, made by hand too, is over 30 days old`() {
        setPassword()
        runBlocking { app.backups.export { java.io.ByteArrayOutputStream() } }
        assertEquals(app.clock.now, runBlocking { nightly.state() }.lastMade?.toInstant())

        app.clock.now = berlin("2026-11-22T07:00")
        assertNull(runBlocking { nightly.warning() })
        app.clock.now = berlin("2026-11-23T07:01")
        assertEquals(BackupWarning.Old, runBlocking { nightly.warning() })
    }

    @Test
    fun `without any backup, Heute is told so once the app is in use for 30 days`() {
        runBlocking { nightly.schedule() }

        app.clock.now = berlin("2026-11-22T07:00")
        assertNull(runBlocking { nightly.warning() })
        app.clock.now = berlin("2026-11-23T07:01")
        assertEquals(BackupWarning.None, runBlocking { nightly.warning() })
    }

    @Test
    fun `turned off, no more backups are made and the folder's access is given back`() {
        setPassword()
        runBlocking { nightly.choose(FOLDER) }

        runBlocking { nightly.stop() }

        assertFalse(folder.kept)
        assertEquals(emptyList<Instant>(), nights())
        assertNull(runBlocking { nightly.state() }.folderName)
        runBlocking { nightly.schedule() }
        assertEquals(emptyList<Instant>(), nights())
    }

    @Test
    fun `a folder the first backup fails in is not taken, and the one before stays`() {
        setPassword()
        runBlocking { nightly.choose(FOLDER) }
        val other = FakeBackupFolder("Anderswo").apply { failing = true }
        app.otherBackupFolders[OTHER_FOLDER] = other

        assertFalse(runBlocking { nightly.choose(OTHER_FOLDER) })

        assertFalse(other.kept)
        assertTrue(folder.kept)
        assertEquals("Backups", runBlocking { nightly.state() }.folderName)
        assertEquals(listOf(berlin("2026-10-24T03:00")), nights())
        assertNull(runBlocking { nightly.warning() })
    }

    @Test
    fun `turned off after a backup failed, Heute is no longer told of it`() {
        setPassword()
        runBlocking { nightly.choose(FOLDER) }
        folder.failing = true
        night("2026-10-24T03:00")

        runBlocking { nightly.stop() }

        assertNull(runBlocking { nightly.warning() })
    }

    @Test
    fun `a backup restored leaves the folder and the nights as they were`() {
        setPassword()
        runBlocking { nightly.choose(FOLDER) }
        val file = folder.files.values.single()
        app.routine("Yoga", "18:00")

        runBlocking { app.backups.restore(app.backups.open({ ByteArrayInputStream(file) }, PASSWORD)) }

        assertEquals(emptyList<String>(), runBlocking { app.routines.inUse().first() }.map { it.name })
        assertEquals("Backups", runBlocking { nightly.state() }.folderName)
        assertEquals(listOf(berlin("2026-10-24T03:00")), nights())
    }

    private fun setPassword() = runBlocking { app.backups.setPassword(PASSWORD) }

    /** The clock at [localDateTime] in Berlin, and the alarm for the night's backup going off then. */
    private fun night(localDateTime: String) {
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(alarmManager.alarmsFor(BackupReceiver.ACTION_BACKUP).single())
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun nights() = alarmManager.alarmsFor(BackupReceiver.ACTION_BACKUP).map { Instant.ofEpochMilli(it.triggerAtMs) }

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Europe/Berlin")).toInstant()

    private companion object {
        const val PASSWORD = "Flieder und Lila"
        const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ABackups"
        const val OTHER_FOLDER = "content://com.android.externalstorage.documents/tree/primary%3AAnderswo"
    }
}
