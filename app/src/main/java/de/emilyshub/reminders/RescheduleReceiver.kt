package de.emilyshub.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.emilyshub.EmilysHubApplication

/**
 * Sets the reminder alarm and the one for the night's backup anew when Android has dropped them (restart, app update)
 * or their time has moved (clock or time zone changed, exact alarms allowed again). Not exported: the system's broadcasts still
 * arrive, other apps' do not, and any other action is ignored (SPEC.md §9).
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val app = context.applicationContext as EmilysHubApplication
        finishInBackground(app) {
            try {
                app.reminders.update()
            } finally {
                app.nightlyBackups.schedule()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, from Android 12 on.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
