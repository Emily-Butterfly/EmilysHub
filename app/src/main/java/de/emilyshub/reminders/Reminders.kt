package de.emilyshub.reminders

import android.content.Context
import de.emilyshub.routines.Routines
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Reminds of routines on time (SPEC.md §3): one exact alarm for the next moment a reminder is due, and
 * a notification for each open occurrence whose time has come. [update] brings both in line with the
 * routines after every change; the lock keeps an update and an alarm going off from crossing.
 */
class Reminders(
    context: Context,
    private val routines: Routines,
    private val clock: Clock,
    private val alarm: ReminderAlarm = ReminderAlarm(context),
    private val notifications: ReminderNotifications = ReminderNotifications(context),
) {
    private val lock = Mutex()

    /** Takes down the reminders of occurrences ticked off, skipped, put off or gone, and sets the alarm anew. */
    suspend fun update() = lock.withLock { tidyAndSetAlarm() }

    /** The alarm set for [scheduledFor] went off: shows what has come due since, then sets the next alarm. */
    suspend fun remind(scheduledFor: Instant) = lock.withLock {
        val now = clock.instant()
        // An alarm going off long after its time, say once the clock was put forward, leaves what was due then on Heute.
        val from = maxOf(scheduledFor, now - LATE_LIMIT)
        routines.remindersDue(from, now).forEach(notifications::show)
        tidyAndSetAlarm()
    }

    /** "Später": the reminder comes back in ten minutes (for now always ten; #8 offers a choice). */
    suspend fun putOff(occurrenceId: Long) = routines.putOff(occurrenceId, until = clock.instant() + PUT_OFF)

    private suspend fun tidyAndSetAlarm() {
        val shown = notifications.shown()
        (shown - routines.stillDue(shown)).forEach(notifications::cancel)
        when (val next = routines.nextReminder()) {
            null -> alarm.cancel()
            else -> alarm.set(next)
        }
    }

    private companion object {
        val PUT_OFF: Duration = Duration.ofMinutes(10)
        val LATE_LIMIT: Duration = Duration.ofHours(1)
    }
}
