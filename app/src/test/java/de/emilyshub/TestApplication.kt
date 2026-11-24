package de.emilyshub

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import de.emilyshub.backup.BackupFolder
import de.emilyshub.backup.BackupMigration
import de.emilyshub.backup.FakeBackupFolder
import de.emilyshub.data.DataUnavailable
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InMemorySettingsDao
import de.emilyshub.data.Unopenable
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.doku.FakeFingerprint
import de.emilyshub.doku.testDokuDatabase
import de.emilyshub.island.RawKey
import de.emilyshub.languages.FakeLessonDocuments
import de.emilyshub.places.FakeLocation
import de.emilyshub.places.FakeWifi
import de.emilyshub.reminders.FakeRinger
import de.emilyshub.reminders.FakeToneFiles
import de.emilyshub.reminders.FakeTones
import de.emilyshub.security.FakeAuthenticator
import de.emilyshub.speech.FakeSpeech
import de.emilyshub.tracking.FakeHealthSource
import de.emilyshub.tracking.TrackingDatabase
import de.emilyshub.tracking.testTrackingDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId
import javax.crypto.KeyGenerator

/**
 * The app as every Robolectric test sees it (set in robolectric.properties): settings in memory and
 * the database schema in memory, both read and written on the spot, a clock the test sets, work in the
 * background done on the spot too, a fake unlock prompt and fingerprint, a full-screen alarm that rings without a sound, the phone's
 * tones in memory, a WLAN the test connects to, a position the test sets, backups with a key derived quickly, and a
 * folder for the nightly ones in memory.
 */
class TestApplication : EmilysHubApplication() {
    val settingsDao = InMemorySettingsDao()
    override val settingsStore = { settingsDao }

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    override val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))

    /** On the spot, so that the tests and Compose see every change at once. */
    override val io = Dispatchers.Unconfined
    override val work = Dispatchers.Unconfined

    /** In memory, open to the tests, such as for the history of days gone by; or failing for [openFails] (#100). */
    override fun openDatabase(): HubDatabase = openFails?.let { throw DataUnavailable(it) } ?: inMemoryDatabase()
    val testDatabase: HubDatabase get() = database

    /** Why opening the data fails from now on, as a test sets it; none for them to open (#100). */
    var openFails: Unopenable? = null

    override val authenticator = FakeAuthenticator()
    override val fingerprint = FakeFingerprint()
    override val trackingFingerprint = FakeFingerprint()
    override val healthSource = FakeHealthSource()

    /** In a file, so that it outlives locking, but with Android's own SQLite, which takes no key. */
    override val openDokuDatabase: (RawKey) -> DokuDatabase = { testDokuDatabase(this) }
    override val openTrackingDatabase: (RawKey) -> TrackingDatabase = { testTrackingDatabase(this) }
    override val ringer = FakeRinger()
    override val tones = FakeTones()
    override val toneFiles = FakeToneFiles(tones)
    override val lessonDocuments = FakeLessonDocuments()

    /** A speech output with the voices a test gives it, which keeps what it reads aloud. */
    override val speech = FakeSpeech()

    /** Stands in for the key sealed with the Android Keystore key, which only exists on a device. */
    private val attachmentKeyForTests = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override val attachmentKey = { attachmentKeyForTests }
    override val wifi = FakeWifi()
    override val location = FakeLocation()
    override val background = CoroutineScope(Dispatchers.Unconfined)

    /** Stands in for the Android Keystore key, which only exists on a device. */
    private val backupKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    /** If set, the Keystore key fails once it was asked for so many more times, as a Keystore that went away would. */
    var backupKeyFailsAfter: Int? = null
    override val backupWrappingKey = {
        backupKeyFailsAfter?.let { left ->
            check(left > 0) { "The Keystore key is gone" }
            backupKeyFailsAfter = left - 1
        }
        backupKey
    }

    /** Android's own SQLite, as SQLCipher's needs its native library. */
    override val backupMigration by lazy { BackupMigration(this) { FrameworkSQLiteOpenHelperFactory() } }

    /** Enough for the tests, which would take seconds with the real count. */
    override val backupIterations = 1_000

    /** The folder chosen for the nightly backups, at any address but those of [otherBackupFolders]. */
    val backupFolder = FakeBackupFolder()
    val otherBackupFolders = mutableMapOf<String, FakeBackupFolder>()
    override fun backupFolderAt(address: String): BackupFolder = otherBackupFolders[address] ?: backupFolder

    /** The folders the images of Mein Warum went to, by their addresses. */
    val pictureFolders = mutableMapOf<String, FakeBackupFolder>()
    override fun pictureFolderAt(address: String): BackupFolder = pictureFolders.getOrPut(address) { FakeBackupFolder("Bilder") }

    /** Whether Android lets the app fill the screen with a reminder ([ShadowFullScreenIntents]); not until a test says. */
    var fullScreenAlarmsAllowed = false
}
