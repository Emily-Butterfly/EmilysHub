package de.emilyshub.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

/** The one alarm that wakes the app for the next reminder: setting it again replaces it. */
class ReminderAlarm(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun set(at: Instant) {
        // Never null without FLAG_NO_CREATE.
        val operation = checkNotNull(operation(PendingIntent.FLAG_UPDATE_CURRENT, at))
        // Exact alarms are allowed from the start; only Android 12 lets the user take that back. The
        // reminder then still comes, if a little late.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        }
    }

    fun cancel() {
        operation(PendingIntent.FLAG_NO_CREATE)?.let(alarmManager::cancel)
    }

    /** Immutable and sent only to [ReminderReceiver]; it tells when it was set for, as [at]. */
    private fun operation(flags: Int, at: Instant? = null): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND)
        if (at != null) intent.putExtra(ReminderReceiver.EXTRA_AT, at.toEpochMilli())
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}
