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
import de.emilyshub.doku.Appointments
import de.emilyshub.doku.Assessments
import de.emilyshub.doku.Doku
import de.emilyshub.doku.DokuClipboard
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.doku.Fingerprint
import de.emilyshub.doku.Measurements
import de.emilyshub.doku.Patients
import de.emilyshub.doku.RawKey
import de.emilyshub.doku.SystemFingerprint
import de.emilyshub.doku.TestCatalog
import de.emilyshub.inbox.CaptureNotification
import de.emilyshub.inbox.Inbox
import de.emilyshub.languages.AttachmentStore
import de.emilyshub.languages.CameraShot
import de.emilyshub.languages.Languages
import de.emilyshub.languages.LessonDocuments
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.PhoneLessonDocuments
import de.emilyshub.ledger.Ledger
import de.emilyshub.ledger.Subscriptions
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
import de.emilyshub.reminders.RoutineReminding
import de.emilyshub.reminders.Sounds
import de.emilyshub.reminders.SubscriptionReminding
import de.emilyshub.reminders.TodoReminding
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.SecureRandom
import java.time.Clock
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.CoroutineContext

/**
 * The app, and the one place where its modules are built and wired. Open only so that the Robolectric tests can put
 * their own in the place of the ports, the parts that differ between the phone and the tests (the database, the
 * settings' storage, the clock, the dispatcher for I/O, and what only works on a device); every module is built from
 * them here, the same way for both.
 */
open class EmilysHubApplication : Application(), Hub {

    // Ports.

    /** Opened on first use, which touches the Keystore and disk: never access it on the main thread. */
    open val database: HubDatabase by lazy {
        val wrappingKey = KeystoreKeys.getOrCreateAesGcmKey("database-key")
        val key = DatabaseKeyFile(File(noBackupFilesDir, "database.key"), wrappingKey).readOrCreate()
        HubDatabase.open(this, key)
    }

    /** Opens the Doku's database with its key in raw form, once the Doku password or the fingerprint set it free. */
    protected open val openDokuDatabase: (RawKey) -> DokuDatabase = { rawKey -> DokuDatabase.open(this, rawKey) }

    /** The fingerprint that may unlock the Doku. */
    open val fingerprint: Fingerprint by lazy { SystemFingerprint(this) }

    /** Where the settings are kept: in the database, opened only once it is asked, off the main thread. */
    protected open val settingsStore: () -> SettingsDao = { database.settings() }

    /** The phone's clock; the tests set their own. */
    open val clock: Clock = DeviceClock

    /** Where the modules read and write. */
    protected open val io: CoroutineContext = Dispatchers.IO

    override val authenticator: DeviceAuthenticator by lazy { SystemAuthenticator(this) }

    /** What rings while a full-screen alarm is up. */
    open val ringer: AlarmRinger by lazy { AlarmSound(this) }

    /** The phone's tones (SPEC.md §3). */
    open val tones: ToneLibrary by lazy { PhoneTones(this) }

    /** Where audio files of one's own are kept as tones. */
    open val toneFiles: ToneFiles by lazy { PhoneToneFiles(this) }

    /** Files read from Android's file dialog for the lessons, and written to it; PDFs as Android opens them. */
    open val lessonDocuments: LessonDocuments by lazy { PhoneLessonDocuments(this) }

    /**
     * The key the lessons' attachments are encrypted with: random, kept in a file only sealed with the Keystore key of
     * the database, like the database's own. Touches the Keystore and disk: never on the main thread.
     */
    protected open val attachmentKey: () -> SecretKey = {
        val wrappingKey = KeystoreKeys.getOrCreateAesGcmKey("database-key")
        SecretKeySpec(DatabaseKeyFile(File(noBackupFilesDir, "attachments.key"), wrappingKey).readOrCreate(), "AES")
    }

    /** The WLAN connected to, by which the places are recognised. */
    override val wifi: CurrentWifi by lazy { SystemWifi(this) }

    /** The position, by which the places with a GPS area are recognised. */
    override val location: CurrentLocation by lazy { SystemLocation(this) }

    /** The key the backups' keys are wrapped with. */
    protected open val backupWrappingKey: () -> SecretKey = { KeystoreKeys.getOrCreateAesGcmKey("backup-key") }

    /** How backups of older versions are brought up to this one: with a key of its own each time, as what is migrated there lives only as long as the migration. */
    protected open val backupMigration: BackupMigration by lazy {
        BackupMigration(this) { HubDatabase.openHelpers(ByteArray(32).also(SecureRandom()::nextBytes)) }
    }

    /** How hard a backup's key, and the Doku's sealed by its password, are to guess from the password. */
    protected open val backupIterations: Int = BackupKey.ITERATIONS

    /** The folder for the nightly backups at [address]. */
    protected open fun backupFolderAt(address: String): BackupFolder = DocumentFolder(this, address.toUri())

    /** Where receivers and the alarm finish their work once they are gone, and where the app starts up. */
    open val background: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Background work failed: ${e.logged}") },
    )

    // Modules, built from the ports. What changes what the reminders remind of tells them ([updateReminders]): the
    // routines, to-dos, Abos, places, looking around, the tones, and a backup restored; goals, notes, tags and the rest of
    // the household book never remind of anything (SPEC.md §6, §13).

    final override val settings: AppSettings by lazy {
        AppSettings(
            settingsStore, io,
            onLookAroundChange = ::updateReminders,
            onTonesChange = ::updateReminders,
            onCaptureChange = ::updateCaptureNotification,
        )
    }

    /** The phone's tones as the app chooses from them, with audio files of one's own brought into them. */
    final override val toneCollection: ToneCollection by lazy { ToneCollection(tones, toneFiles, settingsStore, io) }

    /** What sounds for each reminder, by its tone and the defaults in the settings. */
    val sounds: Sounds by lazy { Sounds(tones, settings::defaultTones) }

    /** The time of day for all that shows it, turning as the phone's clock changes. */
    final override val day: Day by lazy { Day(clock, clockChanges(this)) }

    /** Heute (SPEC.md §1), from the routines, to-dos and notes, for the day it is. */
    final override val heute: Heute by lazy { Heute(routines, todos, notes, subscriptions, day) }

    final override val routines: Routines by lazy { Routines({ database }, clock, io, onChange = ::updateReminders) }

    final override val todos: Todos by lazy { Todos({ database }, clock, io, onChange = ::updateReminders) }

    final override val places: Places by lazy { Places({ database }, io, onChange = ::updateReminders) }

    final override val tags: Tags by lazy { Tags({ database }, io) }

    final override val inbox: Inbox by lazy { Inbox({ database }, clock, io) }

    final override val wishes: Wishes by lazy { Wishes({ database }, clock, io) }

    final override val notes: Notes by lazy { Notes({ database }, clock, io) }

    /** The household book (SPEC.md §13). */
    final override val ledger: Ledger by lazy { Ledger({ database }, clock, io) }

    /** The Abos of the household book (SPEC.md §13). */
    final override val subscriptions: Subscriptions by lazy { Subscriptions({ database }, clock, io, onChange = ::updateReminders) }

    /** Sprachen (SPEC.md §15). */
    final override val lessonFiles: LessonFiles by lazy {
        val key by lazy { attachmentKey() }
        LessonFiles(AttachmentStore({ File(filesDir, "attachments") }, { key }), lessonDocuments, io)
    }

    final override val languages: Languages by lazy { Languages({ database }, clock, lessonFiles, io) }

    final override val search: Search by lazy { Search({ database }, io) }

    /** Where the device is, by the WLAN connected to, the position, and the places that have them. */
    val locator: Locator by lazy { Locator(wifi, location, places) }

    final override val backups: Backups by lazy {
        Backups(
            database = { database },
            settings = settingsStore,
            wrappingKey = backupWrappingKey,
            clock = clock,
            migration = backupMigration,
            doku = doku,
            attachments = lessonFiles,
            io = io,
            iterations = backupIterations,
            onRestore = ::updateReminders,
        )
    }

    /** The backups made each night into the folder chosen for them. */
    final override val nightlyBackups: NightlyBackups by lazy {
        NightlyBackups(backups, settingsStore, clock, ::backupFolderAt, NightAlarm(this), io)
    }

    val reminders: Reminders by lazy {
        val sources = listOf(RoutineReminding(routines), TodoReminding(todos), SubscriptionReminding(subscriptions))
        Reminders(this, sources, clock, locator::whereabouts, settings::mayLookAround, sounds, settings = settingsStore)
    }

    /** Lives as long as the process: a new process starts with the Doku locked, as with the app. */
    final override val doku: Doku by lazy {
        Doku(
            keyFile = File(noBackupFilesDir, "doku.key"),
            databaseFile = getDatabasePath(DokuDatabase.NAME),
            fingerprint = fingerprint,
            openDatabase = openDokuDatabase,
            clipboard = DokuClipboard(this),
            io = io,
            background = background,
            iterations = backupIterations,
        )
    }

    final override val patients: Patients by lazy { Patients(doku, clock, background) }

    final override val appointments: Appointments by lazy { Appointments(doku, clock, background) }

    final override val tests: TestCatalog by lazy { TestCatalog(doku, clock) }

    final override val measurements: Measurements by lazy { Measurements(doku) }

    final override val assessments: Assessments by lazy { Assessments(doku, background) }

    /** Lives as long as the process: a new process is a cold start and starts locked. */
    final override val appLock = AppLock(canAuthenticate = { authenticator.canAuthenticate() })

    final override fun probe() = reminders.probe()

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
        // What a crash or an editor left without saving left behind of the lessons' attachments (SPEC.md §15).
        background.launch {
            languages.clearUpAttachments()
            CameraShot.clearStale(this@EmilysHubApplication)
        }
    }

    /**
     * After every change to the routines, to-dos, Abos, places or to looking around, and after a backup is restored. The change
     * stands even if this fails; the next one tries again.
     */
    protected suspend fun updateReminders() {
        try {
            reminders.update()
        } catch (e: Exception) {
            Log.e(TAG, "Could not update the reminders: ${e.logged}")
        }
    }

    /** "Gedanke festhalten" in the notifications (SPEC.md §16), shown or gone as its setting is on this device. */
    open val captureNotification: CaptureNotification by lazy { CaptureNotification(this) }

    /** One after the other, so that one that read the setting before it changed cannot show it after it is off. */
    private val captureUpdates = Mutex()

    /** Shows "Gedanke festhalten" or takes it away, as its setting is; after a restart or an update too. */
    suspend fun updateCaptureNotification() {
        try {
            captureUpdates.withLock { captureNotification.update(settings.mayCaptureInNotifications()) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not update the notification for thoughts: ${e.logged}")
        }
    }

    private companion object {
        const val TAG = "EmilysHub"
    }
}
