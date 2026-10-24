package de.emilyshub.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** The pages of Android's settings where the app's permissions are allowed (SPEC.md §11). */
object SystemSettings {
    /** The app's own page, from which every permission is reached. */
    fun app(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageOf(context))

    /** The app's notifications, all of them. */
    fun notifications(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** The app's notification channel [channelId], where it pops up or not. */
    fun notificationChannel(context: Context, channelId: String): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)

    /** Exact alarms have a page of their own from Android 12 on; before, they need nothing. */
    fun exactAlarms(context: Context): Intent = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageOf(context))
        else -> app(context)
    }

    /** Full-screen alarms have a page of their own from Android 14 on; before, they need nothing. */
    fun fullScreenAlarms(context: Context): Intent = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageOf(context))
        else -> app(context)
    }

    /**
     * Android asks right away whether the app may run without battery limits. The Play Store allows that only for some
     * kinds of apps, but the app does not come from there (SPEC.md §12), and SPEC.md §11 asks for it.
     */
    @SuppressLint("BatteryLife")
    fun noBatteryLimits(context: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageOf(context))

    /** The apps kept out of the battery optimisation, where the app can be put back in. */
    fun batteryOptimisation() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** The battery, from where a Samsung's limits for apps in the background are reached. */
    fun battery() = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)

    /** Where the location is turned on. */
    fun location() = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    private fun packageOf(context: Context) = Uri.fromParts("package", context.packageName, null)
}

/** Opens [intent], one of Android's settings; a phone without that page gets the app's page instead. */
fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(SystemSettings.app(this))
    }
}
