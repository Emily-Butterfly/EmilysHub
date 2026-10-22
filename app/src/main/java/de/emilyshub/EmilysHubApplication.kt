package de.emilyshub

import android.app.Application
import de.emilyshub.security.SecureWindows

class EmilysHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SecureWindows)
    }
}
