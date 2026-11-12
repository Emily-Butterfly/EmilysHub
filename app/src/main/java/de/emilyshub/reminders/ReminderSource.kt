package de.emilyshub.reminders

import de.emilyshub.ledger.Subscriptions
import de.emilyshub.routines.Locate
import de.emilyshub.routines.Routines
import de.emilyshub.todos.Todos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * One kind of thing that reminds, the seam between [Reminders] and what it reminds of: the routines' occurrences
 * ([RoutineReminding]), the to-dos ([TodoReminding]) and the Abos ([SubscriptionReminding]). Each says what of its own
 * is due, waits or was answered, and does what an answer to its reminder asks; [Reminders] does the rest alike for all.
 */
internal interface ReminderSource {

    /** Whether the reminder about [about] is one of its own. */
    fun owns(about: Alarmed): Boolean

    /** Marks what of its own ran out since [since] missed; nothing for what never runs out. */
    suspend fun markMissed(since: Instant)

    /** What of its own comes due after [after] and up to [until], where [locate] lets it pop up. */
    suspend fun due(after: Instant, until: Instant, locate: Locate): List<DueReminder>

    /** What of its own waited for a place [locate] says the device is at now, to pop up now. */
    suspend fun arrive(locate: Locate): List<DueReminder>

    /** What of its own waits for places it is bound to no more, to pop up now. */
    suspend fun release()

    /** The reminder with [id] as it changes while it is still due; none once it is not. */
    fun reminder(id: Long): Flow<DueReminder?>

    /** Of the reminders about [shown], those of its own still due, as they are now. */
    suspend fun stillDue(shown: Collection<Alarmed>): List<DueReminder>

    /** When something of its own is to remind, or run out, next after [after]; none if nothing ever is. */
    suspend fun nextAlarm(after: Instant): Instant?

    /** The tones its reminders may play. */
    suspend fun tones(): Set<Tone>

    /** Whether arriving at a place matters to it now. */
    suspend fun watchesPlaces(): Boolean

    /** Whether something of its own waits now for a place only looking tells arriving at, one with a GPS area. */
    suspend fun needsLookingAround(): Boolean

    /** Pops up what of its own waited for a place [locate] says the device has arrived at by now, as on arrival. */
    suspend fun lookAround(locate: Locate)

    /** Marks the thing its reminder with [id] is about done. */
    suspend fun done(id: Long)

    /** Puts the reminder with [id] off until [until]. */
    suspend fun putOff(id: Long, until: Instant)

    /** "Überspringen" on the reminder with [id]; nothing for what is never skipped. */
    suspend fun skip(id: Long)

    /** "Heute nicht" on the reminder with [id]; nothing for what has none. */
    suspend fun drop(id: Long)

    /** "Läuft weiter" on the reminder with [id]; nothing for what has none, all but an Abo. */
    suspend fun runOn(id: Long)
}

/** The routines' occurrences as a [ReminderSource]: they run out, are missed, and come in joint reminders. */
internal class RoutineReminding(private val routines: Routines) : ReminderSource {
    override fun owns(about: Alarmed) = about is Alarmed.Occurrence

    // Joining a WLAN tells arriving at a routine's places; none is looked around for.
    override suspend fun needsLookingAround() = false

    override suspend fun lookAround(locate: Locate) = Unit

    override suspend fun markMissed(since: Instant) = routines.markMissed(since)

    override suspend fun due(after: Instant, until: Instant, locate: Locate) =
        routines.remindersDue(after, until, locate).map(DueReminder::OfOccurrence)

    override suspend fun arrive(locate: Locate) = routines.arrive(locate).map(DueReminder::OfOccurrence)

    override suspend fun release() {
        routines.releaseFromPlaces()
    }

    override fun reminder(id: Long) = routines.reminder(id).map { it?.let(DueReminder::OfOccurrence) }

    override suspend fun stillDue(shown: Collection<Alarmed>) =
        routines.stillDue(shown.filter(::owns).map { it.id }).map(DueReminder::OfOccurrence)

    override suspend fun nextAlarm(after: Instant) = routines.nextAlarm(after)

    override suspend fun tones() = routines.tones()

    override suspend fun watchesPlaces() = routines.anyBoundToPlaces()

    override suspend fun done(id: Long) = routines.markDone(id)

    // A joint reminder is put off, skipped and dropped for all it reminds of (SPEC.md §3).
    override suspend fun putOff(id: Long, until: Instant) = routines.putOffTogether(id, until)

    override suspend fun skip(id: Long) = routines.skipTogether(id)

    override suspend fun drop(id: Long) = routines.dropTogether(id)

    override suspend fun runOn(id: Long) = Unit
}

/** The to-dos as a [ReminderSource]: they never run out, so any time to put off by, and wait until done. */
internal class TodoReminding(private val todos: Todos) : ReminderSource {
    override fun owns(about: Alarmed) = about is Alarmed.Todo

    override suspend fun needsLookingAround() = todos.needsLookingAround()

    override suspend fun lookAround(locate: Locate) {
        todos.lookAround(locate)
    }

    override suspend fun markMissed(since: Instant) = Unit

    override suspend fun due(after: Instant, until: Instant, locate: Locate) =
        todos.remindersDue(after, until, locate).map(DueReminder::OfTodo)

    override suspend fun arrive(locate: Locate) = todos.arrive(locate).map(DueReminder::OfTodo)

    override suspend fun release() {
        todos.releaseFromPlaces()
    }

    override fun reminder(id: Long) = todos.reminder(id).map { it?.let(DueReminder::OfTodo) }

    override suspend fun stillDue(shown: Collection<Alarmed>) =
        todos.stillDue(shown.filter(::owns).map { it.id }).map(DueReminder::OfTodo)

    override suspend fun nextAlarm(after: Instant) = todos.nextAlarm(after)

    override suspend fun tones() = todos.tones()

    override suspend fun watchesPlaces() = todos.anyWaiting()

    override suspend fun done(id: Long) = todos.markDone(id)

    override suspend fun putOff(id: Long, until: Instant) = todos.putOff(id, until)

    override suspend fun skip(id: Long) = Unit

    override suspend fun drop(id: Long) = Unit

    override suspend fun runOn(id: Long) = Unit
}

/**
 * The Abos as a [ReminderSource] (#80): each reminds at its own times, set by its last day to cancel, wherever the
 * device is, until that day is over; answered with "Läuft weiter" ([runOn]), or cancelled in the app, it reminds no more
 * of that term. Never put off, skipped or dropped; "Gekündigt" opens the Abo, to choose the day it ends on.
 */
internal class SubscriptionReminding(private val subscriptions: Subscriptions) : ReminderSource {
    override fun owns(about: Alarmed) = about is Alarmed.Subscription

    override suspend fun markMissed(since: Instant) = Unit

    override suspend fun due(after: Instant, until: Instant, locate: Locate) =
        subscriptions.remindersDue(after, until).map(DueReminder::OfSubscription)

    override suspend fun arrive(locate: Locate) = emptyList<DueReminder>()

    override suspend fun release() = Unit

    override fun reminder(id: Long) = subscriptions.reminder(id).map { it?.let(DueReminder::OfSubscription) }

    override suspend fun stillDue(shown: Collection<Alarmed>) =
        subscriptions.stillDue(shown.filter(::owns).map { it.id }).map(DueReminder::OfSubscription)

    override suspend fun nextAlarm(after: Instant) = subscriptions.nextAlarm(after)

    override suspend fun tones() = setOf<Tone>(Tone.Standard)

    override suspend fun watchesPlaces() = false

    override suspend fun needsLookingAround() = false

    override suspend fun lookAround(locate: Locate) = Unit

    override suspend fun done(id: Long) = Unit

    override suspend fun putOff(id: Long, until: Instant) = Unit

    override suspend fun skip(id: Long) = Unit

    override suspend fun drop(id: Long) = Unit

    override suspend fun runOn(id: Long) = subscriptions.runOn(id)
}
