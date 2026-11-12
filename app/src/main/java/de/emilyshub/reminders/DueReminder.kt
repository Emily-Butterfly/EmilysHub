package de.emilyshub.reminders

import de.emilyshub.ledger.SubscriptionReminder
import de.emilyshub.routines.Reminder
import de.emilyshub.todos.TodoReminder
import java.time.Duration
import java.time.Instant

/**
 * A reminder due to be on the screen (SPEC.md §3, §5, §13): of a routine's occurrence, alone or the joint one of coupled
 * routines, of a to-do, or of an Abo to cancel; and what it is [about], which tells it apart on the screen and in the
 * answers to it.
 */
sealed interface DueReminder {
    /** What it is about: the occurrence it is posted for, or the to-do. */
    val about: Alarmed

    /** What it is called: the routine's name, those of all a joint one reminds of, or the to-do's title. */
    val title: String

    /** How it reminds (SPEC.md §3): full screen or not, and its tone; what the lock screen shows is up to each kind. */
    val style: RemindingStyle

    /** Whether it fills the screen as a full-screen alarm (SPEC.md §3). */
    val fullScreen: Boolean get() = style.fullScreen

    /** The tone it plays (SPEC.md §3). */
    val tone: Tone get() = style.tone

    /** Whether it reminds of coupled routines at once, and so asks which one was done (SPEC.md §3). */
    val joint: Boolean

    /** When it can no longer be done, so that "Später" comes back before; none for what never runs out. */
    val windowEnd: Instant?

    /** A routine's occurrence's, alone or joint: it runs out at the end of its time window. */
    data class OfOccurrence(val reminder: Reminder) : DueReminder {
        override val about get() = Alarmed.Occurrence(reminder.occurrenceId)
        override val title get() = reminder.title
        override val style get() = reminder.style
        override val joint get() = reminder.together.isNotEmpty()
        override val windowEnd: Instant get() = reminder.windowEnd
    }

    /** A to-do's: it never runs out, and is never joint. */
    data class OfTodo(val reminder: TodoReminder) : DueReminder {
        override val about get() = Alarmed.Todo(reminder.todoId)
        override val title get() = reminder.title
        override val style get() = reminder.style
        override val joint get() = false
        override val windowEnd: Instant? get() = null
    }

    /**
     * An Abo's, to cancel it (#80): a plain notification with the standard tone, never on the full screen, and nothing of
     * it on the lock screen; it goes once its last day is over.
     */
    data class OfSubscription(val reminder: SubscriptionReminder) : DueReminder {
        override val about get() = Alarmed.Subscription(reminder.subscriptionId)
        override val title get() = reminder.name
        override val style get() = RemindingStyle()
        override val joint get() = false
        override val windowEnd: Instant get() = reminder.ends
    }
}

/** What "Später" offers at [now]: those of the [Reminders.PUT_OFF_CHOICES] that come back before it runs out, if it does. */
fun DueReminder.putOffChoices(now: Instant): List<Duration> =
    Reminders.PUT_OFF_CHOICES.filter { choice -> windowEnd?.let { (now + choice).isBefore(it) } ?: true }
