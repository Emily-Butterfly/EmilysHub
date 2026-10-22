package de.emilyshub.security

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Tells [lock] when the app as a whole leaves the screen and comes back, counted across all its
 * activities. A rotation's stop and restart is far too quick to count as a break.
 */
class AppLockLifecycle(private val lock: AppLock) : Application.ActivityLifecycleCallbacks {
    private var started = 0

    // Runs before the activity's window shows again, so a locked app never shows a frame of content.
    override fun onActivityPreStarted(activity: Activity) {
        if (started++ == 0) lock.onForeground()
    }

    override fun onActivityStopped(activity: Activity) {
        if (--started == 0) lock.onBackground()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
