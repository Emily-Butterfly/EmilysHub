package de.emilyshub.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

/**
 * An alarm that wakes the app for [action], to [ReminderReceiver]: the one for the next reminder, the one for looking
 * around, or the one for a reminder to try out. Setting it again replaces it.
 */
class ReminderAlarm(private val context: Context, private val action: String = ReminderReceiver.ACTION_REMIND) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun set(at: Instant) {
        // Never null without FLAG_NO_CREATE.
        val operation = checkNotNull(operation(PendingIntent.FLAG_UPDATE_CURRENT))
        try {
            if (context.exactAlarmsAllowed()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
                return
            }
        } catch (_: SecurityException) {
            // The permission was taken back just now; the reminder still comes, if a little late.
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
    }

    fun cancel() {
        operation(PendingIntent.FLAG_NO_CREATE)?.let(alarmManager::cancel)
    }

    /** Immutable, and sent only to [ReminderReceiver]. */
    private fun operation(flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}

/**
 * Whether exact alarms are allowed. Before Android 12 they need nothing, and from Android 13 on
 * USE_EXACT_ALARM comes with the app; only Android 12 lets the user take SCHEDULE_EXACT_ALARM back.
 */
fun Context.exactAlarmsAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
