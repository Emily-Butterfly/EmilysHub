package de.emilyshub

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import de.emilyshub.backup.BackupFolder
import de.emilyshub.backup.BackupMigration
import de.emilyshub.backup.FakeBackupFolder
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InMemorySettingsDao
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.places.FakeLocation
import de.emilyshub.places.FakeWifi
import de.emilyshub.reminders.FakeRinger
import de.emilyshub.reminders.FakeToneFiles
import de.emilyshub.reminders.FakeTones
import de.emilyshub.security.FakeAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId
import javax.crypto.KeyGenerator

/**
 * The app as every Robolectric test sees it (set in robolectric.properties): settings in memory and
 * the database schema in memory, both read and written on the spot, a clock the test sets, work in the
 * background done on the spot too, a fake unlock prompt, a full-screen alarm that rings without a sound, the phone's
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

    /** In memory, open to the tests, such as for the history of days gone by. */
    override val database by lazy { inMemoryDatabase() }
    val testDatabase: HubDatabase get() = database

    override val authenticator = FakeAuthenticator()
    override val ringer = FakeRinger()
    override val tones = FakeTones()
    override val toneFiles = FakeToneFiles(tones)
    override val wifi = FakeWifi()
    override val location = FakeLocation()
    override val background = CoroutineScope(Dispatchers.Unconfined)

    /** Stands in for the Android Keystore key, which only exists on a device. */
    private val backupKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override val backupWrappingKey = { backupKey }

    /** Android's own SQLite, as SQLCipher's needs its native library. */
    override val backupMigration by lazy { BackupMigration(this) { FrameworkSQLiteOpenHelperFactory() } }

    /** Enough for the tests, which would take seconds with the real count. */
    override val backupIterations = 1_000

    /** The folder chosen for the nightly backups, at any address but those of [otherBackupFolders]. */
    val backupFolder = FakeBackupFolder()
    val otherBackupFolders = mutableMapOf<String, FakeBackupFolder>()
    override fun backupFolderAt(address: String): BackupFolder = otherBackupFolders[address] ?: backupFolder

    /** Whether Android lets the app fill the screen with a reminder ([ShadowFullScreenIntents]); not until a test says. */
    var fullScreenAlarmsAllowed = false
}
