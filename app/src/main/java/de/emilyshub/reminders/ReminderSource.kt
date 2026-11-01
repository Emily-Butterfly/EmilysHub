package de.emilyshub.reminders

import de.emilyshub.routines.Locate
import de.emilyshub.routines.Routines
import de.emilyshub.todos.Todos
import java.time.Duration
import java.time.Instant

/**
 * One kind of thing that reminds, the seam between [Reminders] and what it reminds of: the routines' occurrences
 * ([RoutineReminding]) and the to-dos ([TodoReminding]). Each says what of its own is due, waits or was answered, and
 * does what an answer to its reminder asks; [Reminders] does the rest alike for both.
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

    /** Of the reminders about [shown], those of its own still due, as they are now. */
    suspend fun stillDue(shown: Collection<Alarmed>): List<DueReminder>

    /** When something of its own is to remind, or run out, next after [after]; none if nothing ever is. */
    suspend fun nextAlarm(after: Instant): Instant?

    /** The tones its reminders may play. */
    suspend fun tones(): Set<Tone>

    /** Whether arriving at a place matters to it now. */
    suspend fun watchesPlaces(): Boolean

    /** Marks the thing its reminder with [id] is about done. */
    suspend fun done(id: Long)

    /** What "Später" offers for [reminder] at [now]. */
    fun putOffChoices(reminder: DueReminder, now: Instant): List<Duration>

    /** Puts the reminder with [id] off until [until]. */
    suspend fun putOff(id: Long, until: Instant)

    /** Whether its reminders may be joint ones, which ask which one was done ([Answer.OfferDone]). */
    val joint: Boolean

    /** "Überspringen" on the reminder with [id]; nothing for what is never skipped. */
    suspend fun skip(id: Long)

    /** "Heute nicht" on the reminder with [id]; nothing for what has none. */
    suspend fun drop(id: Long)
}

/** The routines' occurrences as a [ReminderSource]: they run out, are missed, and come in joint reminders. */
internal class RoutineReminding(private val routines: Routines) : ReminderSource {
    override fun owns(about: Alarmed) = about is Alarmed.Occurrence

    override suspend fun markMissed(since: Instant) = routines.markMissed(since)

    override suspend fun due(after: Instant, until: Instant, locate: Locate) =
        routines.remindersDue(after, until, locate).map(DueReminder::OfOccurrence)

    override suspend fun arrive(locate: Locate) = routines.arrive(locate).map(DueReminder::OfOccurrence)

    override suspend fun release() {
        routines.releaseFromPlaces()
    }

    override suspend fun stillDue(shown: Collection<Alarmed>) =
        routines.stillDue(shown.filter(::owns).map { it.id }).map(DueReminder::OfOccurrence)

    override suspend fun nextAlarm(after: Instant) = routines.nextAlarm(after)

    override suspend fun tones() = routines.tones()

    override suspend fun watchesPlaces() = routines.anyBoundToPlaces()

    override suspend fun done(id: Long) = routines.markDone(id)

    override fun putOffChoices(reminder: DueReminder, now: Instant) =
        (reminder as DueReminder.OfOccurrence).reminder.putOffChoices(now)

    // A joint reminder is put off, skipped and dropped for all it reminds of (SPEC.md §3).
    override suspend fun putOff(id: Long, until: Instant) = routines.putOffTogether(id, until)

    override val joint = true

    override suspend fun skip(id: Long) = routines.skipTogether(id)

    override suspend fun drop(id: Long) = routines.dropTogether(id)
}

/** The to-dos as a [ReminderSource]: they never run out, so any time to put off by, and wait until done. */
internal class TodoReminding(private val todos: Todos) : ReminderSource {
    override fun owns(about: Alarmed) = about is Alarmed.Todo

    override suspend fun markMissed(since: Instant) = Unit

    override suspend fun due(after: Instant, until: Instant, locate: Locate) =
        todos.remindersDue(after, until, locate).map(DueReminder::OfTodo)

    override suspend fun arrive(locate: Locate) = todos.arrive(locate).map(DueReminder::OfTodo)

    override suspend fun release() {
        todos.releaseFromPlaces()
    }

    override suspend fun stillDue(shown: Collection<Alarmed>) =
        todos.stillDue(shown.filter(::owns).map { it.id }).map(DueReminder::OfTodo)

    override suspend fun nextAlarm(after: Instant) = todos.nextAlarm(after)

    override suspend fun tones() = todos.tones()

    override suspend fun watchesPlaces() = todos.anyWaiting()

    override suspend fun done(id: Long) = todos.markDone(id)

    override fun putOffChoices(reminder: DueReminder, now: Instant) = Reminders.PUT_OFF_CHOICES

    override suspend fun putOff(id: Long, until: Instant) = todos.putOff(id, until)

    override val joint = false

    override suspend fun skip(id: Long) = Unit

    override suspend fun drop(id: Long) = Unit
}
