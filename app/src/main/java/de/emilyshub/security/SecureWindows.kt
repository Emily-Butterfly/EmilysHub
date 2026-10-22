package de.emilyshub.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager.LayoutParams.FLAG_SECURE

/**
 * Sets FLAG_SECURE on every activity window before it is created: no screenshots or screen
 * recordings, and the app is blanked in "Recents" (SPEC.md §9). Compose dialogs and popups
 * inherit the flag from their parent window.
 */
object SecureWindows : Application.ActivityLifecycleCallbacks {
    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.window.setFlags(FLAG_SECURE, FLAG_SECURE)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
