package de.emilyshub.reminders

import android.content.Context
import de.emilyshub.data.Setting
import de.emilyshub.data.SettingsDao
import de.emilyshub.routines.Locate
import de.emilyshub.routines.Reminder
import de.emilyshub.routines.Routines
import de.emilyshub.todos.Todos
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
 * To-dos remind the same way (SPEC.md §5), at their time and on arrival at their places, only with "Erledigt · Später":
 * a to-do is never skipped or missed, and its reminder stays until it is done. While one waits for a place, the app
 * also looks around every [LOOK_AROUND_EVERY] ([lookAround]), as long as [lookingAround] says the settings let it, so
 * that arriving is noticed at a place without a WLAN too.
 *
 * It keeps how far it has reminded ("reminded-until" in [settings]) and sets the alarm for the first
 * reminder after that, not after now: so a reminder still comes if the alarm was set anew just as it was
 * due, or if a restart or a change of clock took the alarm before it went off.
 *
 * A routine or to-do bound to places (SPEC.md §4) pops up only where [locate] says the device is at one of them, and
 * waits elsewhere; while a routine is bound or a to-do waits, Android wakes the app on joining a WLAN ([arrived]), also
 * while it is closed. What pops up on arrival, or once bound to no place any more, pops up by the alarm, as at its time.
 */
class Reminders(
    context: Context,
    private val routines: Routines,
    private val todos: Todos,
    private val clock: Clock,
    private val locate: Locate,
    private val lookingAround: suspend () -> Boolean,
    private val settings: () -> SettingsDao,
) {
    private val alarm = ReminderAlarm(context)
    private val probeAlarm = ReminderAlarm(context, ReminderReceiver.ACTION_PROBE)
    private val lookAroundAlarm = ReminderAlarm(context, ReminderReceiver.ACTION_LOOK_AROUND)
    private val arrivals = ArrivalWatch(context)
    private val notifications = ReminderNotifications(context, clock)

    // Keeps an update and an alarm going off from crossing. Not reentrant: nothing done while holding it may
    // write to the routines or to-dos in a way that reports a change, since every such write updates the reminders.
    // Marking missed and planning do not report one.
    private val lock = Mutex()

    /**
     * Marks what ran out missed, takes down the reminders of occurrences ticked off, skipped, put off,
     * missed or gone, and sets the alarm anew.
     */
    suspend fun update() = lock.withLock {
        routines.markMissed(since = remindedUntil())
        routines.releaseFromPlaces()
        todos.releaseFromPlaces()
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
        val shown = routines.remindersDue(after = since, until = now, locate)
        val todosShown = todos.remindersDue(after = since, until = now, locate)
        shown.forEach(notifications::show)
        todosShown.forEach(notifications::show)
        settings().put(Setting(REMINDED_UNTIL, now.toString()))
        tidyAndSetAlarm(justShown = shown.map { it.occurrenceId }.toSet(), todosJustShown = todosShown.map { it.todoId }.toSet())
    }

    /**
     * The device joined a WLAN: what waits for the places it is at now pops up, as long as its time window is open.
     * It does so by the alarm, set for now: Android lets a full-screen alarm ring when an exact alarm goes off, but not
     * when it reports a network.
     */
    suspend fun arrived() = lock.withLock {
        val routinesArrived = routines.arrive(locate)
        val todosArrived = todos.arrive(locate)
        if (routinesArrived.isNotEmpty() || todosArrived.isNotEmpty()) tidyAndSetAlarm()
    }

    /**
     * The look around is due: where the device has arrived at a place a to-do waits for, as [Todos.lookAround] tells, the
     * to-do pops up by the alarm, as on arrival. The next look comes [LOOK_AROUND_EVERY] later, while a to-do still waits.
     */
    suspend fun lookAround() = lock.withLock {
        settings().put(Setting(NEXT_LOOK, ""))
        todos.lookAround(locate)
        tidyAndSetAlarm()
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

    /** "Später" on a to-do's reminder: it asks when to come back, out of [PUT_OFF_CHOICES]; one done meanwhile goes away. */
    suspend fun offerTodoPutOff(todoId: Long) = lock.withLock {
        when (val reminder = todos.stillDue(listOf(todoId)).singleOrNull()) {
            null -> notifications.cancelTodo(todoId)
            else -> notifications.offerPutOff(reminder)
        }
    }

    /**
     * Puts the to-do's reminder off [by] one of the [PUT_OFF_CHOICES], any of them, as a to-do has no time window: it
     * comes back then, anywhere. Only one that has popped up and is still open; otherwise it stays as it is, and one done
     * meanwhile goes away.
     */
    suspend fun putOffTodo(todoId: Long, by: Duration) {
        val reminder = todos.stillDue(listOf(todoId)).singleOrNull()
        when {
            reminder == null || by !in PUT_OFF_CHOICES -> update()
            else -> todos.putOff(todoId, until = clock.instant() + by)
        }
    }

    /** A reminder to try out (SPEC.md §11), in [PROBE_DELAY], by an alarm as the reminders come: also with the app closed. */
    fun probe() = probeAlarm.set(clock.instant() + PROBE_DELAY)

    /** The reminder to try out pops up. */
    fun showProbe() = notifications.showProbe()

    /** What "Später" offers for [reminder] now. */
    fun putOffChoices(reminder: Reminder): List<Duration> = reminder.putOffChoices(clock.instant())

    /**
     * Shows the reminders on the screen as they are now, takes down those no longer due, sets the alarm for what comes
     * next, and watches for arrivals while something may wait for a place. What [justShown] and [todosJustShown] popped up
     * just now is as it is.
     */
    private suspend fun tidyAndSetAlarm(justShown: Set<Long> = emptySet(), todosJustShown: Set<Long> = emptySet()) {
        val shown = notifications.shown()
        val stillDue = routines.stillDue(shown)
        // Shown again quietly, as the routine may have been renamed meanwhile; what just popped up is as it is now.
        stillDue.filter { it.occurrenceId !in justShown }.forEach(notifications::refresh)
        (shown - stillDue.map { it.occurrenceId }.toSet()).forEach(notifications::cancel)
        val todosShown = notifications.shownTodos()
        val todosStillDue = todos.stillDue(todosShown)
        todosStillDue.filter { it.todoId !in todosJustShown }.forEach(notifications::refresh)
        (todosShown - todosStillDue.map { it.todoId }.toSet()).forEach(notifications::cancelTodo)
        val after = remindedUntil()
        when (val next = listOfNotNull(routines.nextAlarm(after), todos.nextAlarm(after)).minOrNull()) {
            null -> alarm.cancel()
            // Possibly already past, if a reminder is still to come: then the alarm goes off right away.
            else -> alarm.set(next)
        }
        val todoWaits = todos.anyWaiting()
        arrivals.watch(on = routines.anyBoundToPlaces() || todoWaits)
        keepLookingAround(on = todoWaits && lookingAround())
    }

    /**
     * Keeps a look around coming [LOOK_AROUND_EVERY] after the last while [on]; none otherwise. Its alarm is set again
     * each time, as a restart takes it; one that should have come by now, or is too far ahead after the clock was put
     * back, comes [LOOK_AROUND_EVERY] from now.
     */
    private suspend fun keepLookingAround(on: Boolean) {
        if (!on) {
            lookAroundAlarm.cancel()
            settings().put(Setting(NEXT_LOOK, ""))
            return
        }
        val now = clock.instant()
        val next = settings().get(NEXT_LOOK)?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.takeIf { it.isAfter(now) && !it.isAfter(now + LOOK_AROUND_EVERY) }
            ?: (now + LOOK_AROUND_EVERY)
        lookAroundAlarm.set(next)
        settings().put(Setting(NEXT_LOOK, next.toString()))
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

        /** How long until the reminder to try out comes (SPEC.md §11). */
        val PROBE_DELAY: Duration = Duration.ofSeconds(10)

        /** How often the app looks around while a to-do waits for a place. */
        val LOOK_AROUND_EVERY: Duration = Duration.ofMinutes(30)

        private const val REMINDED_UNTIL = "reminded-until"

        /** When the app looks around next; empty while it does not. */
        private const val NEXT_LOOK = "next-look-around"
    }
}

/** What "Später" offers at [now]: those of the [Reminders.PUT_OFF_CHOICES] that come back before the time window ends. */
fun Reminder.putOffChoices(now: Instant): List<Duration> = Reminders.PUT_OFF_CHOICES.filter { (now + it).isBefore(windowEnd) }
