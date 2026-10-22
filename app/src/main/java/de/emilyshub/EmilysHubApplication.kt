package de.emilyshub

import android.app.Application
import de.emilyshub.data.HubDatabase
import de.emilyshub.security.DatabaseKeyFile
import de.emilyshub.security.KeystoreKeys
import de.emilyshub.security.SecureWindows
import java.io.File

class EmilysHubApplication : Application() {

    /** Opened on first use, which touches the Keystore and disk: never access it on the main thread. */
    val database: HubDatabase by lazy {
        val wrappingKey = KeystoreKeys.getOrCreateAesGcmKey("database-key")
        val key = DatabaseKeyFile(File(noBackupFilesDir, "database.key"), wrappingKey).readOrCreate()
        HubDatabase.open(this, key)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SecureWindows)
    }
}
