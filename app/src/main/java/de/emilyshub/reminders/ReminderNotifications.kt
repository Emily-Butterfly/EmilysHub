package de.emilyshub.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import de.emilyshub.MainActivity
import de.emilyshub.R
import de.emilyshub.routines.Reminder
import de.emilyshub.ui.clockTime

/**
 * The reminders on the screen (SPEC.md §3, §9): heads-up with "Erledigt · Später · Überspringen". A locked
 * phone shows only the public version, "Erinnerung fällig" without the name or actions, and the actions
 * only work once the phone is unlocked. One notification per occurrence, tagged with its id.
 */
class ReminderNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(reminder: Reminder) {
        manager.createNotificationChannel(channel())
        manager.notify(tagOf(reminder.occurrenceId), ID, notification(reminder))
    }

    fun cancel(occurrenceId: Long) = manager.cancel(tagOf(occurrenceId), ID)

    /** The occurrences whose reminders are on the screen. */
    fun shown(): List<Long> = manager.activeNotifications.mapNotNull { it.tag?.removePrefix(TAG_PREFIX)?.toLongOrNull() }

    private fun channel(): NotificationChannel {
        val name = context.getString(R.string.reminder_channel)
        return NotificationChannel(CHANNEL, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.reminder_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
    }

    private fun notification(reminder: Reminder): Notification {
        val public = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_public))
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.name)
            .setContentText(context.getString(R.string.reminder_due_at, reminder.dueAt.clockTime()))
            .setWhen(reminder.dueAt.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .addAction(action(R.string.reminder_done, ReminderReceiver.ACTION_DONE, reminder.occurrenceId))
            .addAction(action(R.string.reminder_put_off, ReminderReceiver.ACTION_PUT_OFF, reminder.occurrenceId))
            .addAction(action(R.string.reminder_skip, ReminderReceiver.ACTION_SKIP, reminder.occurrenceId))
            .build()
    }

    private fun action(@StringRes title: Int, action: String, occurrenceId: Long): Notification.Action {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            // Tells the intents of different occurrences apart, so that each keeps its own extras.
            .setIdentifier(tagOf(occurrenceId))
            .putExtra(ReminderReceiver.EXTRA_OCCURRENCE, occurrenceId)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val operation = PendingIntent.getBroadcast(context, 0, intent, flags)
        return Notification.Action.Builder(null, context.getString(title), operation)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAuthenticationRequired(true) }
            .build()
    }

    /** Like a tap on the app's icon: brings the app to the front, behind its lock if that is on. */
    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun tagOf(occurrenceId: Long) = TAG_PREFIX + occurrenceId

    private companion object {
        const val CHANNEL = "reminders"
        const val TAG_PREFIX = "occurrence:"
        const val ID = 1
    }
}
