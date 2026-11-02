package de.emilyshub.reminders

import android.content.Context
import de.emilyshub.data.Setting
import de.emilyshub.data.SettingsDao
import de.emilyshub.routines.Locate
import de.emilyshub.routines.Reminder
import kotlinx.coroutines.flow.Flow
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
 * a to-do is never skipped or missed, and its reminder stays until it is done. While one waits for a place with a GPS
 * area, the app also looks around every [LOOK_AROUND_EVERY] ([lookAround]), as long as [mayLookAround] says the
 * settings let it, so that arriving is noticed without a WLAN too.
 *
 * Each reminder plays its tone, or the default in the settings, once, on a channel of that tone ([Sounds]); a
 * channel no routine or to-do needs any more goes.
 *
 * It keeps how far it has reminded ("reminded-until" in [settings]) and sets the alarm for the first
 * reminder after that, not after now: so a reminder still comes if the alarm was set anew just as it was
 * due, or if a restart or a change of clock took the alarm before it went off.
 *
 * A routine or to-do bound to places (SPEC.md §4) pops up only where [locate] says the device is at one of them, and
 * waits elsewhere; while a routine is bound or a to-do waits, Android wakes the app on joining a WLAN ([arrived]), also
 * while it is closed. What pops up on arrival, or once bound to no place any more, pops up by the alarm, as at its time.
 *
 * Routines and to-dos are reminded of alike: each is a [ReminderSource], which says what of its own is due and does what
 * an answer asks, and the reminders go on the [screen], Android's notifications unless a test puts its own there.
 */
class Reminders internal constructor(
    context: Context,
    private val sources: List<ReminderSource>,
    private val clock: Clock,
    private val locate: Locate,
    private val mayLookAround: suspend () -> Boolean,
    private val sounds: Sounds,
    private val screen: ReminderScreen = ReminderNotifications(context, clock, sounds),
    private val settings: () -> SettingsDao,
) {
    private val alarm = ReminderAlarm(context)
    private val probeAlarm = ReminderAlarm(context, ReminderReceiver.ACTION_PROBE)
    private val lookAroundAlarm = ReminderAlarm(context, ReminderReceiver.ACTION_LOOK_AROUND)
    private val arrivals = ArrivalWatch(context)

    // Keeps an update and an alarm going off from crossing. Not reentrant: nothing done while holding it may
    // write to the routines or to-dos in a way that reports a change, since every such write updates the reminders.
    // Marking missed and planning do not report one.
    private val lock = Mutex()

    /**
     * Marks what ran out missed, takes down the reminders of occurrences ticked off, skipped, put off,
     * missed or gone, and sets the alarm anew.
     */
    suspend fun update() = lock.withLock {
        val since = remindedUntil()
        sources.forEach { it.markMissed(since) }
        sources.forEach { it.release() }
        tidyAndSetAlarm()
    }

    /**
     * The alarm went off: marks what ran out missed, shows what has come due since the last time, then sets the
     * next alarm. What came due long ago, say while the phone was off, still pops up while its window is open.
     */
    suspend fun remind() = lock.withLock {
        val now = clock.instant()
        val since = remindedUntil()
        sources.forEach { it.markMissed(since) }
        val shown = sources.flatMap { it.due(after = since, until = now, locate) }
        shown.forEach { screen.show(it, alert = true, Offering.Actions) }
        settings().put(Setting(REMINDED_UNTIL, now.toString()))
        tidyAndSetAlarm(justShown = shown.map { it.about }.toSet())
    }

    /**
     * The device joined a WLAN: what waits for the places it is at now pops up, as long as its time window is open.
     * It does so by the alarm, set for now: Android lets a full-screen alarm ring when an exact alarm goes off, but not
     * when it reports a network.
     */
    suspend fun arrived() = lock.withLock {
        val arrived = sources.flatMap { it.arrive(locate) }
        if (arrived.isNotEmpty()) tidyAndSetAlarm()
    }

    /**
     * The look around is due: where the device has arrived at a place a to-do waits for, as its source tells, the
     * to-do pops up by the alarm, as on arrival. The next look comes [LOOK_AROUND_EVERY] later, while a to-do still waits.
     */
    suspend fun lookAround() = lock.withLock {
        settings().put(Setting(NEXT_LOOK, ""))
        try {
            sources.forEach { it.lookAround(locate) }
        } finally {
            // The next look comes all the same, should this one fail.
            tidyAndSetAlarm()
        }
    }

    /**
     * Does what [answer] asks of the reminder about [about], from its notification or its full-screen alarm alike:
     *
     * - "Erledigt" marks it done; on a joint reminder, the one chosen, after it asked which one it was;
     * - "Später" asks when to come back, and the choice puts it off then, and its follow-ups after that: only one that has
     *   come due and is still open, and only by a choice it offers now, else it stays as it is;
     * - "Überspringen" and "Heute nicht" are for all a joint reminder reminds of, and nothing for a to-do.
     *
     * Asking anything of one answered meanwhile takes it down. What writes to the routines or to-dos runs outside the
     * lock, as their change brings the reminders up to date, which takes it.
     */
    suspend fun answer(about: Alarmed, answer: Answer) {
        val source = sourceOf(about)
        when (answer) {
            Answer.Done -> source.done(about.id)
            Answer.OfferDone -> offer(about, Offering.Done, onlyIf = DueReminder::joint)
            Answer.OfferPutOff -> offer(about, Offering.PutOff)
            is Answer.PutOff -> putOff(about, answer.by)
            Answer.Skip -> source.skip(about.id)
            Answer.Drop -> source.drop(about.id)
        }
    }

    /**
     * The reminder about [about] offers [offering] instead, quietly, if it is one it offers [onlyIf] is for; one answered
     * meanwhile goes away.
     */
    private suspend fun offer(about: Alarmed, offering: Offering, onlyIf: (DueReminder) -> Boolean = { true }) = lock.withLock {
        when (val reminder = stillDue(about)) {
            null -> screen.cancel(about)
            else -> if (onlyIf(reminder)) screen.show(reminder, alert = false, offering)
        }
    }

    /**
     * Puts the reminder about [about] off [by] one of the choices its source offers now; anything else, or one no longer
     * due, leaves it as it is, and brings the screen up to date.
     */
    private suspend fun putOff(about: Alarmed, by: Duration) {
        val source = sourceOf(about)
        val reminder = stillDue(about)
        val now = clock.instant()
        when {
            reminder == null || by !in reminder.putOffChoices(now) -> update()
            else -> source.putOff(about.id, until = now + by)
        }
    }

    /** The reminder about [about] still due, alone or the joint one it is posted for; none once it is not. */
    private suspend fun stillDue(about: Alarmed): DueReminder? =
        sourceOf(about).stillDue(listOf(about)).firstOrNull { it.about == about }

    /** Of the [sources], the one whose reminder [about] is. */
    private fun sourceOf(about: Alarmed): ReminderSource = sources.single { it.owns(about) }

    /** A reminder to try out (SPEC.md §11), in [PROBE_DELAY], by an alarm as the reminders come: also with the app closed. */
    fun probe() = probeAlarm.set(clock.instant() + PROBE_DELAY)

    /** The reminder to try out pops up. */
    fun showProbe() = screen.showProbe()

    /** The reminder about [about] as it changes while it is still due, as its full-screen alarm shows it; none once it is not. */
    fun reminder(about: Alarmed): Flow<DueReminder?> = sourceOf(about).reminder(about.id)

    /** What "Später" offers for [reminder] now: before its time window ends, for a routine's; any time, for a to-do's. */
    fun putOffChoices(reminder: DueReminder): List<Duration> = reminder.putOffChoices(clock.instant())

    /**
     * Shows the reminders on the screen as they are now, takes down those no longer due, sets the alarm for what comes
     * next, and watches for arrivals while something may wait for a place, looking around too while a to-do does. What
     * [justShown] popped up just now is as it is.
     */
    private suspend fun tidyAndSetAlarm(justShown: Set<Alarmed> = emptySet()) {
        val shown = screen.shown()
        val stillDue = sources.flatMap { it.stillDue(shown) }
        // Shown again quietly, as the routine may have been renamed meanwhile; what just popped up is as it is now. A
        // choice offered stays up.
        stillDue.filter { it.about !in justShown }.forEach { screen.show(it, alert = false, screen.offering(it.about)) }
        (shown - stillDue.map { it.about }.toSet()).forEach(screen::cancel)
        val after = remindedUntil()
        when (val next = sources.mapNotNull { it.nextAlarm(after) }.minOrNull()) {
            null -> alarm.cancel()
            // Possibly already past, if a reminder is still to come: then the alarm goes off right away.
            else -> alarm.set(next)
        }
        // A tone no routine or to-do has any more takes its channel along, once no reminder on the screen is on it.
        val tones = (sources.flatMap { it.tones() } + Tone.Standard).distinct()
        screen.tidyChannels(inUse = tones.map { sounds.notification(it) })
        arrivals.watch(on = sources.any { it.watchesPlaces() })
        keepLookingAround(on = sources.any { it.needsLookingAround() } && mayLookAround())
    }

    /**
     * Keeps a look around coming [LOOK_AROUND_EVERY] after the last while [on]; none otherwise. Its alarm is set again
     * each time, as a restart takes it: one that should have come by now comes right away, and one too far ahead after
     * the clock was put back comes [LOOK_AROUND_EVERY] from now.
     */
    private suspend fun keepLookingAround(on: Boolean) {
        if (!on) {
            lookAroundAlarm.cancel()
            settings().put(Setting(NEXT_LOOK, ""))
            return
        }
        val now = clock.instant()
        val stored = stored(NEXT_LOOK)
        val next = when {
            stored == null || stored.isAfter(now + LOOK_AROUND_EVERY) -> now + LOOK_AROUND_EVERY
            stored.isBefore(now) -> now
            else -> stored
        }
        lookAroundAlarm.set(next)
        settings().put(Setting(NEXT_LOOK, next.toString()))
    }

    /**
     * Up to when reminders have come; from the first update on, so a new installation starts now. Never after
     * now: once the clock is put back, what comes due again is reminded of again.
     */
    private suspend fun remindedUntil(): Instant {
        val now = clock.instant()
        val stored = stored(REMINDED_UNTIL)
        if (stored != null && !stored.isAfter(now)) return stored
        settings().put(Setting(REMINDED_UNTIL, now.toString()))
        return now
    }

    /** The moment stored under [name]; none if there is none, or anything else. */
    private suspend fun stored(name: String): Instant? = settings().get(name)?.let { runCatching { Instant.parse(it) }.getOrNull() }

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
fun Reminder.putOffChoices(now: Instant): List<Duration> = DueReminder.OfOccurrence(this).putOffChoices(now)
