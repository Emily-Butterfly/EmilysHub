package de.emilyshub.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import de.emilyshub.MainActivity
import de.emilyshub.R
import de.emilyshub.routines.Reminder
import de.emilyshub.ui.clockTime
import java.time.Clock
import java.time.Duration

/**
 * The reminders on the screen (SPEC.md §3, §9): heads-up with "Erledigt · Später · Überspringen", and after
 * "Später" the choice when to come back, "10 Min. · 30 Min. · 1 Std.", in the same place. On a
 * locked phone the actions only work once it is unlocked (from Android 12 on), and the lock screen shows
 * the public version, "Erinnerung fällig" without the name or actions, as long as Android is set to hide
 * the content of notifications there; an app cannot make that choice for the user. A routine with a
 * full-screen alarm fills the screen when its reminder pops up ([AlarmActivity]), where Android allows it.
 * One notification per occurrence, tagged with its id.
 */
class ReminderNotifications(private val context: Context, private val clock: Clock) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Pops the reminder up, with a sound even if it is up already: at its time and at each follow-up. */
    fun show(reminder: Reminder) = post(reminder, alert = true, offeringPutOff = false)

    /** Shows the reminder as it is now, without a sound if it is up already; a choice for "Später" stays up. */
    fun refresh(reminder: Reminder) = post(reminder, alert = false, offeringPutOff = isOfferingPutOff(reminder.occurrenceId))

    /** "Später": the reminder offers when to come back instead of its actions, without a sound. */
    fun offerPutOff(reminder: Reminder) = post(reminder, alert = false, offeringPutOff = true)

    fun cancel(occurrenceId: Long) = manager.cancel(tagOf(occurrenceId), ID)

    /**
     * A reminder to try out (SPEC.md §11): popping up as a reminder does, on the lock screen too, only without a
     * routine or actions.
     */
    fun showProbe() {
        manager.createNotificationChannel(channel())
        val notification = Notification.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_probe))
            .setContentText(context.getString(R.string.reminder_probe_text))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion())
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(PROBE_TAG, ID, notification)
    }

    /** The occurrences whose reminders are on the screen. */
    fun shown(): List<Long> = manager.activeNotifications.mapNotNull { it.tag?.removePrefix(TAG_PREFIX)?.toLongOrNull() }

    private fun post(reminder: Reminder, alert: Boolean, offeringPutOff: Boolean) {
        manager.createNotificationChannel(channel())
        val notification = notification(reminder, alert, offeringPutOff)
        // Android fills the screen for a notification posted, not reliably for one changed: a full-screen alarm
        // coming again, at a follow-up for one, is posted anew.
        if (notification.fullScreenIntent != null) manager.cancel(tagOf(reminder.occurrenceId), ID)
        manager.notify(tagOf(reminder.occurrenceId), ID, notification)
        // While the phone or tablet is in use, Android shows only the reminder instead: it rings all the same.
        if (notification.fullScreenIntent != null) AlarmRingService.ring(context, reminder.occurrenceId)
    }

    private fun isOfferingPutOff(occurrenceId: Long) = manager.activeNotifications.any {
        it.tag == tagOf(occurrenceId) && it.notification.extras.getBoolean(EXTRA_OFFERING_PUT_OFF)
    }

    private fun channel(): NotificationChannel {
        val name = context.getString(R.string.reminder_channel)
        return NotificationChannel(REMINDER_CHANNEL, name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
    }

    /** What the lock screen shows of a reminder while Android hides the content of notifications there. */
    private fun publicVersion() = Notification.Builder(context, REMINDER_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.reminder_public))
        .setCategory(Notification.CATEGORY_REMINDER)
        .build()

    private fun notification(reminder: Reminder, alert: Boolean, offeringPutOff: Boolean): Notification {
        val id = reminder.occurrenceId
        // Only what comes back before the time window ends; with none, there is no "Später".
        val choices = reminder.putOffChoices(clock.instant())
        val offering = offeringPutOff && choices.isNotEmpty()
        val text: String
        val actions: List<Notification.Action>
        if (offering) {
            text = context.getString(R.string.reminder_put_off_when)
            actions = choices.map { choice ->
                action(context.putOffLabel(choice), ReminderReceiver.ACTION_PUT_OFF, id, putOffBy = choice)
            }
        } else {
            text = context.getString(R.string.reminder_due_at, reminder.dueAt.clockTime())
            actions = listOfNotNull(
                action(context.getString(R.string.reminder_done), ReminderReceiver.ACTION_DONE, id),
                choices.firstOrNull()?.let {
                    action(context.getString(R.string.reminder_put_off), ReminderReceiver.ACTION_OFFER_PUT_OFF, id)
                },
                action(context.getString(R.string.reminder_skip), ReminderReceiver.ACTION_SKIP, id),
            )
        }
        // An alarm, which "Nicht stören" lets through where it lets alarms through, as it does the ringing.
        val alarm = reminder.fullScreen && context.fullScreenAlarmsAllowed()
        // Only when it pops up: shown again quietly, it does not fill the screen again.
        val fullScreen = alert && alarm
        return Notification.Builder(context, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.name)
            .setContentText(text)
            .setWhen(reminder.dueAt.toInstant().toEpochMilli())
            .setShowWhen(true)
            .setCategory(if (alarm) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion())
            // Tapped, an alarm's reminder opens the alarm, as the reminder is all Android shows of it while in use.
            .setContentIntent(if (alarm) alarm(id, ring = false) else openApp())
            .apply { if (fullScreen) setFullScreenIntent(alarm(id, ring = true), true) }
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .addExtras(Bundle().apply { putBoolean(EXTRA_OFFERING_PUT_OFF, offering) })
            .apply { actions.forEach(::addAction) }
            .build()
    }

    private fun action(title: String, action: String, occurrenceId: Long, putOffBy: Duration? = null): Notification.Action {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            // Tells the intents of different occurrences and choices apart, so that each keeps its own extras.
            .setIdentifier(listOfNotNull(tagOf(occurrenceId), putOffBy?.toMinutes()).joinToString(":"))
            .putExtra(EXTRA_OCCURRENCE, occurrenceId)
        putOffBy?.let { intent.putExtra(ReminderReceiver.EXTRA_MINUTES, it.toMinutes()) }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val operation = PendingIntent.getBroadcast(context, 0, intent, flags)
        return Notification.Action.Builder(null, title, operation)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAuthenticationRequired(true) }
            .build()
    }

    /** The full-screen alarm for the occurrence, shown over the lock screen too, as [alarmIntent] has it. */
    private fun alarm(occurrenceId: Long, ring: Boolean): PendingIntent {
        val intent = alarmIntent(context, occurrenceId, ring)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
        const val TAG_PREFIX = "occurrence:"

        /** Not an occurrence's, so that tidying the reminders leaves it. */
        const val PROBE_TAG = "probe"
        const val ID = 1
        const val EXTRA_OFFERING_PUT_OFF = "de.emilyshub.extra.OFFERING_PUT_OFF"
    }
}

/** One of the choices "Später" offers: "10 Min.", or "1 Std." for whole hours. */
fun Context.putOffLabel(choice: Duration): String = when (choice.toMinutes() % 60) {
    0L -> getString(R.string.reminder_put_off_hours, choice.toHours())
    else -> getString(R.string.reminder_put_off_minutes, choice.toMinutes())
}

/** The channel of the reminders, which the user may turn down on its own in Android's settings. */
const val REMINDER_CHANNEL = "reminders"

/** Whether notifications are allowed, from Android 13 on with a permission of their own. */
fun Context.notificationsAllowed(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

/**
 * Whether reminders pop up (SPEC.md §3), as their channel starts out: the user may turn it down in Android's settings,
 * so that they only show in the list, or not at all.
 */
fun Context.remindersPopUp(): Boolean {
    val channel = getSystemService(NotificationManager::class.java).getNotificationChannel(REMINDER_CHANNEL)
    return channel == null || channel.importance >= NotificationManager.IMPORTANCE_HIGH
}

/**
 * Whether a reminder may fill the screen. Before Android 14 that comes with the app; from Android 14 on the
 * user allows it in Android's settings, which may not allow it to start with.
 */
fun Context.fullScreenAlarmsAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
