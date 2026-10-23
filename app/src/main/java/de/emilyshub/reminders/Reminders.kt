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
 * Reminds of routines on time (SPEC.md §3), and again at their follow-ups while they stay open: one alarm
 * for the next moment a reminder is due, and a notification for each open occurrence whose time has come.
 * [update] brings both in line with the routines after every change.
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
        val due = routines.remindersDue(after = since, until = now)
        due.forEach(notifications::show)
        settings().put(Setting(REMINDED_UNTIL, now.toString()))
        tidyAndSetAlarm(justShown = due.map { it.occurrenceId }.toSet())
    }

    /** "Später": the reminder asks when to come back, out of [PUT_OFF_CHOICES]; one answered meanwhile goes away. */
    suspend fun offerPutOff(occurrenceId: Long) = lock.withLock {
        when (val reminder = routines.stillDue(listOf(occurrenceId)).singleOrNull()) {
            null -> notifications.cancel(occurrenceId)
            else -> notifications.offerPutOff(reminder)
        }
    }

    /** Puts the reminder off [by] one of [PUT_OFF_CHOICES]: it comes back then, and its follow-ups after that. */
    suspend fun putOff(occurrenceId: Long, by: Duration) = routines.putOff(occurrenceId, until = clock.instant() + by)

    private suspend fun tidyAndSetAlarm(justShown: Set<Long> = emptySet()) {
        val shown = notifications.shown()
        val stillDue = routines.stillDue(shown)
        // Shown again quietly, as the routine may have been renamed meanwhile; what just popped up is as it is now.
        stillDue.filter { it.occurrenceId !in justShown }.forEach(notifications::refresh)
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

    companion object {
        /** What "Später" offers (SPEC.md §3). */
        val PUT_OFF_CHOICES: List<Duration> = listOf(10L, 30L, 60L).map(Duration::ofMinutes)

        private const val REMINDED_UNTIL = "reminded-until"
        private val LATE_LIMIT: Duration = Duration.ofHours(1)
    }
}
