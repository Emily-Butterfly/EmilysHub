package de.emilyshub.reminders

import android.content.Intent

/**
 * What a full-screen alarm rings for (SPEC.md §3, §5): a routine's occurrence or a to-do, which can take turns in the
 * same alarm. Kept as its [key] where only text goes, such as across a rotation.
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

    /** Puts it into [intent] as a reminder's intents carry it, [EXTRA_OCCURRENCE] or [EXTRA_TODO]. */
    fun putInto(intent: Intent): Intent = when (this) {
        is Occurrence -> intent.putExtra(EXTRA_OCCURRENCE, id)
        is Todo -> intent.putExtra(EXTRA_TODO, id)
    }

    companion object {
        /** The one of [key]; none for anything else. */
        fun of(key: String): Alarmed? {
            val id = key.substringAfter(':').toLongOrNull()?.takeIf { it > 0 } ?: return null
            return when (key.substringBefore(':')) {
                "occurrence" -> Occurrence(id)
                "todo" -> Todo(id)
                else -> null
            }
        }
    }
}

/** What an intent of a reminder is about, an occurrence or else a to-do; none without a valid one. */
internal fun Intent.alarmed(): Alarmed? = occurrenceId()?.let(Alarmed::Occurrence) ?: todoId()?.let(Alarmed::Todo)
