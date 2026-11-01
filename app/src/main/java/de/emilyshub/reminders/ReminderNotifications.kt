package de.emilyshub.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri
import de.emilyshub.MainActivity
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.routines.Reminder
import de.emilyshub.todos.TodoReminder
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.placesLabel
import de.emilyshub.ui.todos.dueText
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

/**
 * The reminders on the screen (SPEC.md §3, §9): heads-up with "Erledigt · Später · Überspringen", and after
 * "Später" the choice when to come back, "10 Min. · 30 Min. · 1 Std.", in the same place. On a
 * locked phone the actions only work once it is unlocked (from Android 12 on), and the lock screen shows
 * the public version, "Erinnerung fällig" without the name or actions, as long as Android is set to hide
 * the content of notifications there; an app cannot make that choice for the user. A routine or to-do that
 * allows its name there shows its name and time in the public version instead, still without actions, and never
 * more of it. A routine with a full-screen alarm fills the screen when its reminder pops up ([AlarmActivity]), where Android allows it.
 * One notification per occurrence, tagged with its id. A to-do's reminder offers "Erledigt · Später" only, as a to-do
 * is never skipped (SPEC.md §5), and is tagged with the to-do's id; one with a full-screen alarm fills the screen too.
 *
 * Each reminder plays its tone once (SPEC.md §3), as [Sounds] has it, on the channel of that tone: the phone's own
 * notification tone on the usual one, any other tone on one of its own, as Android fixes a channel's sound once it is
 * made. A full-screen alarm that rings comes on the quiet one, as it plays its tone over and over itself.
 */
class ReminderNotifications(private val context: Context, private val clock: Clock, private val sounds: Sounds) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Pops the reminder up, with a sound even if it is up already: at its time and at each follow-up. */
    suspend fun show(reminder: Reminder) = post(reminder, alert = true, Offering.Actions)

    /** Shows the reminder as it is now, without a sound if it is up already; a choice offered stays up. */
    suspend fun refresh(reminder: Reminder) = post(reminder, alert = false, offeringOf(tagOf(reminder.occurrenceId)))

    /** "Später": the reminder offers when to come back instead of its actions, without a sound. */
    suspend fun offerPutOff(reminder: Reminder) = post(reminder, alert = false, Offering.PutOff)

    /** "Erledigt" on a joint reminder (SPEC.md §3): it asks which one it was instead of its actions, without a sound. */
    suspend fun offerDone(reminder: Reminder) = post(reminder, alert = false, Offering.Done)

    fun cancel(occurrenceId: Long) = manager.cancel(tagOf(occurrenceId), ID)

    /** Pops the to-do's reminder up, with a sound even if it is up already: at its time, on arrival, or put off to. */
    suspend fun show(reminder: TodoReminder) = post(reminder, alert = true, offeringPutOff = false)

    /** Shows the to-do's reminder as it is now, without a sound if it is up already; a choice for "Später" stays up. */
    suspend fun refresh(reminder: TodoReminder) =
        post(reminder, alert = false, offeringPutOff = isOfferingPutOff(todoTagOf(reminder.todoId)))

    /** "Später" on a to-do's reminder: it offers when to come back instead of its actions, without a sound. */
    suspend fun offerPutOff(reminder: TodoReminder) = post(reminder, alert = false, offeringPutOff = true)

    fun cancelTodo(todoId: Long) = manager.cancel(todoTagOf(todoId), ID)

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
    fun shown(): List<Long> = shownTagged(TAG_PREFIX)

    /** The to-dos whose reminders are on the screen. */
    fun shownTodos(): List<Long> = shownTagged(TODO_TAG_PREFIX)

    /** The ids in the tags starting with [prefix] of the reminders on the screen. */
    private fun shownTagged(prefix: String): List<Long> = manager.activeNotifications.mapNotNull { shown ->
        shown.tag?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toLongOrNull()
    }

    private suspend fun post(reminder: Reminder, alert: Boolean, offering: Offering) {
        val channel = channelFor(reminder.fullScreen, reminder.tone)
        val notification = notification(reminder, alert, offering, channel)
        notify(tagOf(reminder.occurrenceId), notification, Alarmed.Occurrence(reminder.occurrenceId))
    }

    private suspend fun post(reminder: TodoReminder, alert: Boolean, offeringPutOff: Boolean) {
        val channel = channelFor(reminder.fullScreen, reminder.tone)
        notify(todoTagOf(reminder.todoId), notification(reminder, alert, offeringPutOff, channel), Alarmed.Todo(reminder.todoId))
    }

    /** Shows [notification] under [tag]; as a full-screen alarm, posted anew and ringing for what it is [about]. */
    private fun notify(tag: String, notification: Notification, about: Alarmed) {
        val fullScreen = notification.fullScreenIntent != null
        // Android fills the screen for a notification posted, not reliably for one changed: a full-screen alarm
        // coming again, at a follow-up for one, is posted anew.
        if (fullScreen) manager.cancel(tag, ID)
        manager.notify(tag, ID, notification)
        // While the phone or tablet is in use, Android shows only the reminder instead: it rings all the same.
        if (fullScreen) AlarmRingService.ring(context, about)
    }

    private fun isOfferingPutOff(tag: String) = offeringOf(tag) == Offering.PutOff

    /** What the reminder under [tag] offers now, as it is on the screen; its actions if it is not. */
    private fun offeringOf(tag: String): Offering {
        val shown = manager.activeNotifications.firstOrNull { it.tag == tag }?.notification?.extras ?: return Offering.Actions
        return when {
            shown.getBoolean(EXTRA_OFFERING_PUT_OFF) -> Offering.PutOff
            shown.getBoolean(EXTRA_OFFERING_DONE) -> Offering.Done
            else -> Offering.Actions
        }
    }

    /**
     * Takes away the channels of tones that no routine or to-do uses any more, those [inUse] aside, and those of the
     * reminders on the screen, which would go with them.
     */
    fun tidyChannels(inUse: Collection<Sound>) {
        // The quiet one too, which every ringing full-screen alarm comes on: made anew for each, Android would count it
        // deleted each time, and bring it back as it was then.
        val kept = (inUse + Sound.Silent).map(::channelIdOf)
        val keep = kept.toSet() + manager.activeNotifications.map { it.notification.channelId }
        manager.notificationChannels
            .filter { it.id.startsWith(TONE_CHANNEL_PREFIX) && it.id !in keep }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    /** The channel a reminder of [tone] comes on, made if need be; the quiet one for a [fullScreen] alarm that rings. */
    private suspend fun channelFor(fullScreen: Boolean, tone: Tone): String {
        val sound = if (fullScreen && context.fullScreenAlarmsAllowed()) Sound.Silent else sounds.notification(tone)
        manager.createNotificationChannel(channel(sound))
        return channelIdOf(sound)
    }

    private fun channel(sound: Sound = Sound.PhoneDefault): NotificationChannel {
        val name = when (sound) {
            Sound.PhoneDefault -> context.getString(R.string.reminder_channel)
            Sound.Silent -> context.getString(R.string.reminder_channel_silent)
            is Sound.Of -> context.getString(
                R.string.reminder_channel_tone,
                sounds.title(sound.uri) ?: context.getString(R.string.tone_unnamed),
            )
        }
        return NotificationChannel(channelIdOf(sound), name, NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.reminder_channel_description)
            // The phone's own notification tone, as a channel has it unless told otherwise.
            when (sound) {
                Sound.PhoneDefault -> Unit
                // Without a sound, Android vibrates only if the channel says so; a ringing alarm vibrates by itself too.
                Sound.Silent -> {
                    setSound(null, null)
                    enableVibration(true)
                }
                is Sound.Of -> setSound(sound.uri.toUri(), NOTIFICATION_AUDIO)
            }
        }
    }

    /** The usual channel for the phone's own tone, another for no sound, and one for each tone by its address. */
    private fun channelIdOf(sound: Sound): String = when (sound) {
        Sound.PhoneDefault -> REMINDER_CHANNEL
        Sound.Silent -> TONE_CHANNEL_PREFIX + "silent"
        is Sound.Of -> TONE_CHANNEL_PREFIX + "tone-" + UUID.nameUUIDFromBytes(sound.uri.toByteArray())
    }

    /**
     * What the lock screen shows of a reminder while Android hides the content of notifications there: "Erinnerung
     * fällig", or the [title] and [text] of one that allows them there (SPEC.md §3, §9). Never its actions.
     */
    private fun publicVersion(title: String? = null, text: String? = null) = Notification.Builder(context, REMINDER_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title ?: context.getString(R.string.reminder_public))
        .setContentText(text)
        .setCategory(Notification.CATEGORY_REMINDER)
        .build()

    private fun notification(reminder: Reminder, alert: Boolean, offering: Offering, channel: String): Notification {
        val about = ReminderOf(tagOf(reminder.occurrenceId), EXTRA_OCCURRENCE, reminder.occurrenceId)
        // Only what comes back before the time window ends, and "Heute nicht" after it: Android shows three actions at most,
        // so two times at most. With no time left, "Später" still offers "Heute nicht".
        val choices = reminder.putOffChoices(clock.instant()).take(2)
        val dueAt = context.getString(R.string.reminder_due_at, reminder.dueAt.clockTime())
        val joint = reminder.together.isNotEmpty()
        val text: String
        val actions: List<Notification.Action>
        when (offering) {
            Offering.PutOff -> {
                text = context.getString(R.string.reminder_put_off_when)
                actions = putOffActions(choices, about) +
                    action(context.getString(R.string.reminder_drop), ReminderReceiver.ACTION_DROP, about)
            }
            // Which one was done: one action for each, by name, as many as Android shows.
            Offering.Done -> {
                text = context.getString(R.string.reminder_which_done)
                actions = reminder.occurrences.sortedWith(compareBy(InputText.byName) { it.name }).take(MAX_ACTIONS).map {
                    action(it.name, ReminderReceiver.ACTION_DONE, about.copy(id = it.occurrenceId))
                }
            }
            Offering.Actions -> {
                text = dueAt
                val done = if (joint) ReminderReceiver.ACTION_OFFER_DONE else ReminderReceiver.ACTION_DONE
                actions = listOf(
                    action(context.getString(R.string.reminder_done), done, about),
                    action(context.getString(R.string.reminder_put_off), ReminderReceiver.ACTION_OFFER_PUT_OFF, about),
                    action(context.getString(R.string.reminder_skip), ReminderReceiver.ACTION_SKIP, about),
                )
            }
        }
        val public = if (reminder.titleOnLockScreen) publicVersion(reminder.title, dueAt) else publicVersion()
        return builder(channel, reminder.title, text, alert, offering, public)
            .setWhen(reminder.dueAt.toInstant().toEpochMilli())
            .setShowWhen(true)
            .asAlarmIf(reminder.fullScreen, Alarmed.Occurrence(reminder.occurrenceId), alert, otherwise = openApp())
            .apply { actions.forEach(::addAction) }
            .build()
    }

    /** A to-do's reminder: when it is due, or where it reminds without a time; any time to put it off by, as it never ends. */
    private fun notification(reminder: TodoReminder, alert: Boolean, offeringPutOff: Boolean, channel: String): Notification {
        val about = ReminderOf(todoTagOf(reminder.todoId), EXTRA_TODO, reminder.todoId)
        val text: String?
        val actions: List<Notification.Action>
        if (offeringPutOff) {
            text = context.getString(R.string.reminder_put_off_when)
            actions = putOffActions(Reminders.PUT_OFF_CHOICES, about)
        } else {
            text = textOf(reminder)
            actions = listOf(
                action(context.getString(R.string.reminder_done), ReminderReceiver.ACTION_DONE, about),
                action(context.getString(R.string.reminder_put_off), ReminderReceiver.ACTION_OFFER_PUT_OFF, about),
            )
        }
        // When it is due, but not where: its places stay off the lock screen.
        val public = if (reminder.nameOnLockScreen) {
            publicVersion(reminder.title, context.resources.lockScreenText(reminder, LocalDate.now(clock)))
        } else {
            publicVersion()
        }
        return builder(channel, reminder.title, text, alert, if (offeringPutOff) Offering.PutOff else Offering.Actions, public)
            // Tapped, it opens the app, or its alarm, and stays up: a to-do waits until it is done.
            .asAlarmIf(reminder.fullScreen, Alarmed.Todo(reminder.todoId), alert, otherwise = openApp())
            .setAutoCancel(false)
            .apply { actions.forEach(::addAction) }
            .build()
    }

    private fun textOf(reminder: TodoReminder): String? = context.resources.reminderText(reminder, LocalDate.now(clock))

    /**
     * With a [fullScreen] alarm, where Android allows it, the reminder is an alarm about [about]: one "Nicht stören" lets
     * through where it lets alarms through, as it does the ringing, which fills the screen as it pops up ([alert]) and opens
     * the alarm when tapped, as the reminder is all Android shows of it while in use. Otherwise a reminder opening [otherwise].
     */
    private fun Notification.Builder.asAlarmIf(
        fullScreen: Boolean,
        about: Alarmed,
        alert: Boolean,
        otherwise: PendingIntent,
    ): Notification.Builder {
        val alarm = fullScreen && context.fullScreenAlarmsAllowed()
        setCategory(if (alarm) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
        setContentIntent(if (alarm) alarm(about, ring = false) else otherwise)
        // Only when it pops up: shown again quietly, it does not fill the screen again.
        if (alarm && alert) setFullScreenIntent(alarm(about, ring = true), true)
        return this
    }

    /**
     * What every reminder has: its [title] and [text], on the lock screen only its [public] version, with the sound of
     * its [channel] only if it [alert]s.
     */
    private fun builder(
        channel: String,
        title: String,
        text: String?,
        alert: Boolean,
        offering: Offering,
        public: Notification,
    ): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .setOnlyAlertOnce(!alert)
            .addExtras(
                Bundle().apply {
                    putBoolean(EXTRA_OFFERING_PUT_OFF, offering == Offering.PutOff)
                    putBoolean(EXTRA_OFFERING_DONE, offering == Offering.Done)
                },
            )

    private fun putOffActions(choices: List<Duration>, about: ReminderOf) = choices.map { choice ->
        action(context.putOffLabel(choice), ReminderReceiver.ACTION_PUT_OFF, about, putOffBy = choice)
    }

    private fun action(title: String, action: String, about: ReminderOf, putOffBy: Duration? = null): Notification.Action {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            // Tells the intents of different reminders and choices apart, so that each keeps its own extras.
            .setIdentifier(listOfNotNull(about.tag, about.id, putOffBy?.toMinutes()).joinToString(":"))
            .putExtra(about.idExtra, about.id)
        putOffBy?.let { intent.putExtra(ReminderReceiver.EXTRA_MINUTES, it.toMinutes()) }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val operation = PendingIntent.getBroadcast(context, 0, intent, flags)
        return Notification.Action.Builder(null, title, operation)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAuthenticationRequired(true) }
            .build()
    }

    /** The full-screen alarm for what it is [about], shown over the lock screen too, as [alarmIntent] has it. */
    private fun alarm(about: Alarmed, ring: Boolean): PendingIntent {
        val intent = alarmIntent(context, about, ring)
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

    private fun todoTagOf(todoId: Long) = TODO_TAG_PREFIX + todoId

    /**
     * What a reminder and its actions are about, an occurrence or a to-do: its [tag] on the screen, and its [id] in the
     * intents under the name [idExtra].
     */
    private data class ReminderOf(val tag: String, val idExtra: String, val id: Long)

    /** What a reminder offers: its actions, when to come back after "Später", or which one was done after "Erledigt". */
    private enum class Offering { Actions, PutOff, Done }

    private companion object {
        const val TAG_PREFIX = "occurrence:"
        const val TODO_TAG_PREFIX = "todo:"

        /** Not an occurrence's, so that tidying the reminders leaves it. */
        const val PROBE_TAG = "probe"
        const val ID = 1
        const val EXTRA_OFFERING_PUT_OFF = "de.emilyshub.extra.OFFERING_PUT_OFF"
        const val EXTRA_OFFERING_DONE = "de.emilyshub.extra.OFFERING_DONE"

        /** As many actions as Android shows on a notification. */
        const val MAX_ACTIONS = 3

        /** The channels of tones of their own, and of no sound; the usual one does not start so. */
        const val TONE_CHANNEL_PREFIX = "$REMINDER_CHANNEL-"

        /** How a notification's tone plays: as a notification, at its volume. */
        val NOTIFICATION_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

/**
 * What a to-do's reminder says, in the notification and in its alarm: "Fällig: heute, 18:00" as the To-dos list says it,
 * on [today]; without a due, the places it reminds at, if it still has any.
 */
fun Resources.reminderText(reminder: TodoReminder, today: LocalDate): String? =
    lockScreenText(reminder, today) ?: placesLabel(reminder.places)

/**
 * What the lock screen shows of a to-do's reminder besides its title, where it allows that (SPEC.md §3): when it is due,
 * as [reminderText] says it on [today], but never where; none without a due.
 */
fun Resources.lockScreenText(reminder: TodoReminder, today: LocalDate): String? =
    dueText(reminder.due, today)?.let { getString(R.string.todo_due_label, it) }

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
