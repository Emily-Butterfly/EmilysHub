package de.emilyshub.reminders

import android.content.Context
import de.emilyshub.data.Setting
import de.emilyshub.data.SettingsDao
import de.emilyshub.routines.Routines
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Reminds of routines on time (SPEC.md §3): one alarm for the next moment a reminder is due, and a
 * notification for each open occurrence whose time has come. [update] brings both in line with the
 * routines after every change.
 *
 * It keeps how far it has reminded ("reminded-until" in [settings]) and sets the alarm for the first
 * reminder after that, not after now: so a reminder still comes if the alarm was set anew just as it was
 * due, or if a restart or a change of clock took the alarm before it went off.
 */
class Reminders(
    context: Context,
    private val routines: Routines,
    private val clock: Clock,
    private val settings: () -> SettingsDao,
) {
    private val alarm = ReminderAlarm(context)
    private val notifications = ReminderNotifications(context)

    // Keeps an update and an alarm going off from crossing. Not reentrant: nothing done while holding it
    // may write to the routines, since every write updates the reminders.
    private val lock = Mutex()

    /** Takes down the reminders of occurrences ticked off, skipped, put off or gone, and sets the alarm anew. */
    suspend fun update() = lock.withLock { tidyAndSetAlarm() }

    /** The alarm went off: shows what has come due since the last time, then sets the next alarm. */
    suspend fun remind() = lock.withLock {
        val now = clock.instant()
        // What came due more than an hour ago, say while the phone was off or before the clock was put
        // forward, stays on Heute instead of popping up all at once (#10 brings a time window per routine).
        val since = maxOf(remindedUntil(), now - LATE_LIMIT)
        routines.remindersDue(after = since, until = now).forEach(notifications::show)
        settings().put(Setting(REMINDED_UNTIL, now.toString()))
        tidyAndSetAlarm()
    }

    /** "Später": the reminder comes back in ten minutes (for now always ten; #8 offers a choice). */
    suspend fun putOff(occurrenceId: Long) = routines.putOff(occurrenceId, until = clock.instant() + PUT_OFF)

    private suspend fun tidyAndSetAlarm() {
        val shown = notifications.shown()
        val stillDue = routines.stillDue(shown)
        // Shown again quietly, as the routine may have been renamed meanwhile.
        stillDue.forEach(notifications::show)
        (shown - stillDue.map { it.occurrenceId }.toSet()).forEach(notifications::cancel)
        when (val next = routines.nextReminder(after = remindedUntil())) {
            null -> alarm.cancel()
            // Possibly already past, if a reminder is still to come: then the alarm goes off right away.
            else -> alarm.set(next)
        }
    }

    /** Up to when reminders have come; from the first update on, so a new installation starts now. */
    private suspend fun remindedUntil(): Instant =
        settings().get(REMINDED_UNTIL)?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: clock.instant().also { settings().put(Setting(REMINDED_UNTIL, it.toString())) }

    private companion object {
        const val REMINDED_UNTIL = "reminded-until"
        val PUT_OFF: Duration = Duration.ofMinutes(10)
        val LATE_LIMIT: Duration = Duration.ofHours(1)
    }
}
