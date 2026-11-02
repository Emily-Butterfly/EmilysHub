package de.emilyshub

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import de.emilyshub.backup.BackupFolder
import de.emilyshub.backup.BackupKey
import de.emilyshub.backup.BackupMigration
import de.emilyshub.backup.Backups
import de.emilyshub.backup.DocumentFolder
import de.emilyshub.backup.NightAlarm
import de.emilyshub.backup.NightlyBackups
import de.emilyshub.data.AppSettings
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.SettingsDao
import de.emilyshub.ledger.Ledger
import de.emilyshub.notes.Notes
import de.emilyshub.places.CurrentLocation
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.Locator
import de.emilyshub.places.Places
import de.emilyshub.places.SystemLocation
import de.emilyshub.places.SystemWifi
import de.emilyshub.reminders.AlarmRinger
import de.emilyshub.reminders.AlarmSound
import de.emilyshub.reminders.PhoneToneFiles
import de.emilyshub.reminders.PhoneTones
import de.emilyshub.reminders.Reminders
import de.emilyshub.reminders.Sounds
import de.emilyshub.reminders.ToneCollection
import de.emilyshub.reminders.ToneFiles
import de.emilyshub.reminders.ToneLibrary
import de.emilyshub.routines.Routines
import de.emilyshub.search.Search
import de.emilyshub.security.AppLock
import de.emilyshub.security.AppLockLifecycle
import de.emilyshub.security.DatabaseKeyFile
import de.emilyshub.security.DeviceAuthenticator
import de.emilyshub.security.KeystoreKeys
import de.emilyshub.security.SecureWindows
import de.emilyshub.security.SystemAuthenticator
import de.emilyshub.tags.Tags
import de.emilyshub.today.Heute
import de.emilyshub.todos.Todos
import de.emilyshub.wishes.Wishes
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.security.SecureRandom
import java.time.Clock
import javax.crypto.SecretKey
import kotlin.coroutines.CoroutineContext

/**
 * The app, and the one place where its modules are built and wired. Open only so that the Robolectric tests can put
 * their own in the place of the ports, the parts that differ between the phone and the tests (the database, the
 * settings' storage, the clock, the dispatcher for I/O, and what only works on a device); every module is built from
 * them here, the same way for both.
 */
open class EmilysHubApplication : Application() {

    // Ports.

    /** Opened on first use, which touches the Keystore and disk: never access it on the main thread. */
    open val database: HubDatabase by lazy {
        val wrappingKey = KeystoreKeys.getOrCreateAesGcmKey("database-key")
        val key = DatabaseKeyFile(File(noBackupFilesDir, "database.key"), wrappingKey).readOrCreate()
        HubDatabase.open(this, key)
    }

    /** Where the settings are kept: in the database, opened only once it is asked, off the main thread. */
    protected open val settingsStore: () -> SettingsDao = { database.settings() }

    /** The phone's clock; the tests set their own. */
    open val clock: Clock = DeviceClock

    /** Where the modules read and write. */
    protected open val io: CoroutineContext = Dispatchers.IO

    open val authenticator: DeviceAuthenticator by lazy { SystemAuthenticator(this) }

    /** What rings while a full-screen alarm is up. */
    open val ringer: AlarmRinger by lazy { AlarmSound(this) }

    /** The phone's tones (SPEC.md §3). */
    open val tones: ToneLibrary by lazy { PhoneTones(this) }

    /** Where audio files of one's own are kept as tones. */
    open val toneFiles: ToneFiles by lazy { PhoneToneFiles(this) }

    /** The WLAN connected to, by which the places are recognised. */
    open val wifi: CurrentWifi by lazy { SystemWifi(this) }

    /** The position, by which the places with a GPS area are recognised. */
    open val location: CurrentLocation by lazy { SystemLocation(this) }

    /** The key the backups' keys are wrapped with. */
    protected open val backupWrappingKey: () -> SecretKey = { KeystoreKeys.getOrCreateAesGcmKey("backup-key") }

    /** How backups of older versions are brought up to this one: with a key of its own each time, as what is migrated there lives only as long as the migration. */
    protected open val backupMigration: BackupMigration by lazy {
        BackupMigration(this) { HubDatabase.openHelpers(ByteArray(32).also(SecureRandom()::nextBytes)) }
    }

    /** How hard a backup's key is to guess from its password. */
    protected open val backupIterations: Int = BackupKey.ITERATIONS

    /** The folder for the nightly backups at [address]. */
    protected open fun backupFolderAt(address: String): BackupFolder = DocumentFolder(this, address.toUri())

    /** Where receivers and the alarm finish their work once they are gone, and where the app starts up. */
    open val background: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Background work failed", e) },
    )

    // Modules, built from the ports. What changes what the reminders remind of tells them ([updateReminders]): the
    // routines, to-dos, places, looking around, the tones, and a backup restored; goals, notes, tags and the household
    // book never remind of anything (SPEC.md §6, §13).

    val settings: AppSettings by lazy {
        AppSettings(settingsStore, io, onLookAroundChange = ::updateReminders, onTonesChange = ::updateReminders)
    }

    /** The phone's tones as the app chooses from them, with audio files of one's own brought into them. */
    val toneCollection: ToneCollection by lazy { ToneCollection(tones, toneFiles, settingsStore, io) }

    /** What sounds for each reminder, by its tone and the defaults in the settings. */
    val sounds: Sounds by lazy { Sounds(tones, settings::defaultTones) }

    /** The time of day for all that shows it, turning as the phone's clock changes. */
    val day: Day by lazy { Day(clock, clockChanges(this)) }

    /** Heute (SPEC.md §1), from the routines, to-dos and notes, for the day it is. */
    val heute: Heute by lazy { Heute(routines, todos, notes, day) }

    val routines: Routines by lazy { Routines({ database }, clock, io, onChange = ::updateReminders) }

    val todos: Todos by lazy { Todos({ database }, clock, io, onChange = ::updateReminders) }

    val places: Places by lazy { Places({ database }, io, onChange = ::updateReminders) }

    val tags: Tags by lazy { Tags({ database }, io) }

    val wishes: Wishes by lazy { Wishes({ database }, clock, io) }

    val notes: Notes by lazy { Notes({ database }, clock, io) }

    /** The household book (SPEC.md §13). */
    val ledger: Ledger by lazy { Ledger({ database }, clock, io) }

    val search: Search by lazy { Search({ database }, io) }

    /** Where the device is, by the WLAN connected to, the position, and the places that have them. */
    val locator: Locator by lazy { Locator(wifi, location, places) }

    val backups: Backups by lazy {
        Backups(
            database = { database },
            settings = settingsStore,
            wrappingKey = backupWrappingKey,
            clock = clock,
            migration = backupMigration,
            io = io,
            iterations = backupIterations,
            onRestore = ::updateReminders,
        )
    }

    /** The backups made each night into the folder chosen for them. */
    val nightlyBackups: NightlyBackups by lazy {
        NightlyBackups(backups, settingsStore, clock, ::backupFolderAt, NightAlarm(this), io)
    }

    val reminders: Reminders by lazy {
        Reminders(this, routines, todos, clock, locator::whereabouts, settings::mayLookAround, sounds, settings = settingsStore)
    }

    /** Lives as long as the process: a new process is a cold start and starts locked. */
    val appLock = AppLock(canAuthenticate = { authenticator.canAuthenticate() })

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SecureWindows)
        registerActivityLifecycleCallbacks(AppLockLifecycle(appLock))
        // Android drops the alarm when the app is stopped by force; it is set again on the next start.
        background.launch { reminders.update() }
        // So too the alarm for the night's backup; and one missed meanwhile is made up.
        background.launch { nightlyBackups.schedule() }
        // The fixed bookings of the days the app did not run on (SPEC.md §13).
        background.launch { ledger.enterDue() }
    }

    /**
     * After every change to the routines, to-dos, places or to looking around, and after a backup is restored. The change
     * stands even if this fails; the next one tries again.
     */
    protected suspend fun updateReminders() {
        try {
            reminders.update()
        } catch (e: Exception) {
            Log.e(TAG, "Could not update the reminders", e)
        }
    }

    private companion object {
        const val TAG = "EmilysHub"
    }
}
