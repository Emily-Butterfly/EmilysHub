package de.emilyshub

import android.app.Application
import android.util.Log
import de.emilyshub.data.AppSettings
import de.emilyshub.data.HubDatabase
import de.emilyshub.reminders.AlarmRinger
import de.emilyshub.reminders.AlarmSound
import de.emilyshub.reminders.Reminders
import de.emilyshub.routines.Routines
import de.emilyshub.security.AppLock
import de.emilyshub.security.AppLockLifecycle
import de.emilyshub.security.DatabaseKeyFile
import de.emilyshub.security.DeviceAuthenticator
import de.emilyshub.security.KeystoreKeys
import de.emilyshub.security.SecureWindows
import de.emilyshub.security.SystemAuthenticator
import de.emilyshub.tags.Tags
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Open so that Robolectric tests can swap the parts that only work on a device. */
open class EmilysHubApplication : Application() {

    /** Opened on first use, which touches the Keystore and disk: never access it on the main thread. */
    val database: HubDatabase by lazy {
        val wrappingKey = KeystoreKeys.getOrCreateAesGcmKey("database-key")
        val key = DatabaseKeyFile(File(noBackupFilesDir, "database.key"), wrappingKey).readOrCreate()
        HubDatabase.open(this, key)
    }

    open val settings: AppSettings by lazy { AppSettings({ database.settings() }) }

    open val authenticator: DeviceAuthenticator by lazy { SystemAuthenticator(this) }

    /** What rings while a full-screen alarm is up. */
    open val ringer: AlarmRinger by lazy { AlarmSound(this) }

    open val routines: Routines by lazy { Routines({ database }, DeviceClock, onChange = ::updateReminders) }

    open val tags: Tags by lazy { Tags({ database }) }

    open val reminders: Reminders by lazy { Reminders(this, routines, DeviceClock) { database.settings() } }

    /** Where receivers and the alarm finish their work once they are gone, and where the app starts up. */
    open val background: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e(TAG, "Background work failed", e) },
    )

    /** Lives as long as the process: a new process is a cold start and starts locked. */
    val appLock = AppLock(canAuthenticate = { authenticator.canAuthenticate() })

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SecureWindows)
        registerActivityLifecycleCallbacks(AppLockLifecycle(appLock))
        // Android drops the alarm when the app is stopped by force; it is set again on the next start.
        background.launch { reminders.update() }
    }

    /** After every change to the routines. The change stands even if this fails; the next one tries again. */
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
