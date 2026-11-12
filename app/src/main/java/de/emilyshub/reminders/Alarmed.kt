package de.emilyshub.reminders

import android.content.Intent

/**
 * What a reminder is about (SPEC.md §3, §5, §13): a routine's occurrence or a to-do, which can take turns in the same
 * full-screen alarm, or an Abo to cancel, which never fills the screen. Kept as its [key] where only text goes, such as
 * across a rotation.
 */
sealed interface Alarmed {
    val id: Long
    val key: String

    data class Occurrence(override val id: Long) : Alarmed {
        override val key: String get() = "occurrence:$id"
    }

    data class Todo(override val id: Long) : Alarmed {
        override val key: String get() = "todo:$id"
    }

    /** An Abo, to cancel in time (#80). */
    data class Subscription(override val id: Long) : Alarmed {
        override val key: String get() = "subscription:$id"
    }

    /** Puts it into [intent] as a reminder's intents carry it, [EXTRA_OCCURRENCE], [EXTRA_TODO] or [EXTRA_SUBSCRIPTION]. */
    fun putInto(intent: Intent): Intent = when (this) {
        is Occurrence -> intent.putExtra(EXTRA_OCCURRENCE, id)
        is Todo -> intent.putExtra(EXTRA_TODO, id)
        is Subscription -> intent.putExtra(EXTRA_SUBSCRIPTION, id)
    }

    companion object {
        /** The one of [key]; none for anything else. */
        fun of(key: String): Alarmed? {
            val id = key.substringAfter(':').toLongOrNull()?.takeIf { it > 0 } ?: return null
            return when (key.substringBefore(':')) {
                "occurrence" -> Occurrence(id)
                "todo" -> Todo(id)
                "subscription" -> Subscription(id)
                else -> null
            }
        }
    }
}

/** What an intent of a reminder is about, an occurrence, a to-do or else an Abo; none without a valid one. */
internal fun Intent.alarmed(): Alarmed? =
    occurrenceId()?.let(Alarmed::Occurrence) ?: todoId()?.let(Alarmed::Todo) ?: subscriptionId()?.let(Alarmed::Subscription)
