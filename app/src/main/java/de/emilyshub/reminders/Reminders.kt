package de.emilyshub.reminders

import android.content.Context
import de.emilyshub.data.Setting
import de.emilyshub.data.SettingsDao
import de.emilyshub.routines.Reminder
import de.emilyshub.routines.Routines
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Reminds of routines on time (SPEC.md §3), and again at their follow-ups while they stay open: one alarm
 * for the next moment a reminder is due, and a notification for each open occurrence whose time has come.
 * [update] brings both in line with the routines after every change. Once an occurrence's time window
 * ends, it is missed and its reminder goes (SPEC.md §2): the alarm goes off for that too, and every update
 * and alarm first marks what ran out meanwhile, also while the phone was off.
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
    private val notifications = ReminderNotifications(context, clock)

    // Keeps an update and an alarm going off from crossing. Not reentrant: nothing done while holding it may
    // write to the routines in a way that reports a change, since every such write updates the reminders.
    // Marking missed and planning do not report one.
    private val lock = Mutex()

    /**
     * Marks what ran out missed, takes down the reminders of occurrences ticked off, skipped, put off,
     * missed or gone, and sets the alarm anew.
     */
    suspend fun update() = lock.withLock {
        routines.markMissed(since = remindedUntil())
        tidyAndSetAlarm()
    }

    /**
     * The alarm went off: marks what ran out missed, shows what has come due since the last time, then sets the
     * next alarm. What came due long ago, say while the phone was off, still pops up while its window is open.
     */
    suspend fun remind() = lock.withLock {
        val now = clock.instant()
        val since = remindedUntil()
        routines.markMissed(since)
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

    /**
     * Puts the reminder off [by] one of its [putOffChoices]: it comes back then, and its follow-ups after that.
     * Only one that has come due and is still open, and only to before its time window ends; otherwise it
     * stays as it is, and one answered meanwhile goes away.
     */
    suspend fun putOff(occurrenceId: Long, by: Duration) {
        val reminder = routines.stillDue(listOf(occurrenceId)).singleOrNull()
        when {
            reminder == null || by !in putOffChoices(reminder) -> update()
            else -> routines.putOff(occurrenceId, until = clock.instant() + by)
        }
    }

    /** What "Später" offers for [reminder] now. */
    fun putOffChoices(reminder: Reminder): List<Duration> = reminder.putOffChoices(clock.instant())

    private suspend fun tidyAndSetAlarm(justShown: Set<Long> = emptySet()) {
        val shown = notifications.shown()
        val stillDue = routines.stillDue(shown)
        // Shown again quietly, as the routine may have been renamed meanwhile; what just popped up is as it is now.
        stillDue.filter { it.occurrenceId !in justShown }.forEach(notifications::refresh)
        (shown - stillDue.map { it.occurrenceId }.toSet()).forEach(notifications::cancel)
        when (val next = routines.nextAlarm(after = remindedUntil())) {
            null -> alarm.cancel()
            // Possibly already past, if a reminder is still to come: then the alarm goes off right away.
            else -> alarm.set(next)
        }
    }

    /**
     * Up to when reminders have come; from the first update on, so a new installation starts now. Never after
     * now: once the clock is put back, what comes due again is reminded of again.
     */
    private suspend fun remindedUntil(): Instant {
        val now = clock.instant()
        val stored = settings().get(REMINDED_UNTIL)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (stored != null && !stored.isAfter(now)) return stored
        settings().put(Setting(REMINDED_UNTIL, now.toString()))
        return now
    }

    companion object {
        /** What "Später" offers (SPEC.md §3). */
        val PUT_OFF_CHOICES: List<Duration> = listOf(10L, 30L, 60L).map(Duration::ofMinutes)

        private const val REMINDED_UNTIL = "reminded-until"
    }
}

/** What "Später" offers at [now]: those of the [Reminders.PUT_OFF_CHOICES] that come back before the time window ends. */
fun Reminder.putOffChoices(now: Instant): List<Duration> = Reminders.PUT_OFF_CHOICES.filter { (now + it).isBefore(windowEnd) }
