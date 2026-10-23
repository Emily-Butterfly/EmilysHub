package de.emilyshub

import android.app.Application
import de.emilyshub.data.AppSettings
import de.emilyshub.data.HubDatabase
import de.emilyshub.routines.Routines
import de.emilyshub.security.AppLock
import de.emilyshub.security.AppLockLifecycle
import de.emilyshub.security.DatabaseKeyFile
import de.emilyshub.security.DeviceAuthenticator
import de.emilyshub.security.KeystoreKeys
import de.emilyshub.security.SecureWindows
import de.emilyshub.security.SystemAuthenticator
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

    open val routines: Routines by lazy { Routines({ database }, DeviceClock) }

    /** Lives as long as the process: a new process is a cold start and starts locked. */
    val appLock = AppLock(canAuthenticate = { authenticator.canAuthenticate() })

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SecureWindows)
        registerActivityLifecycleCallbacks(AppLockLifecycle(appLock))
    }
}
