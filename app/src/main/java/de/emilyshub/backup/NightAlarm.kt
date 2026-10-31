package de.emilyshub.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant

/**
 * The alarm that wakes the app for the night's backup, to [BackupReceiver]. Not exact: a backup may well come a little
 * later, and Android lets it come while the phone rests too. Setting it again replaces it.
 */
class NightAlarm(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun set(at: Instant) {
        // Never null without FLAG_NO_CREATE.
        val operation = checkNotNull(operation(PendingIntent.FLAG_UPDATE_CURRENT))
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
    }

    fun cancel() {
        operation(PendingIntent.FLAG_NO_CREATE)?.let(alarmManager::cancel)
    }

    /** Immutable, and sent only to [BackupReceiver]. */
    private fun operation(flags: Int): PendingIntent? {
        val intent = Intent(context, BackupReceiver::class.java).setAction(BackupReceiver.ACTION_BACKUP)
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }
}
