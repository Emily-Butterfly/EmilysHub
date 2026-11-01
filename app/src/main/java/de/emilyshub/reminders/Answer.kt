package de.emilyshub.reminders

import java.time.Duration

/**
 * An answer to a reminder (SPEC.md §3, §5), from its notification or its full-screen alarm alike, as
 * [Reminders.answer] takes it.
 */
sealed interface Answer {
    /** "Erledigt": what it is about is done; of a joint reminder, the one chosen. */
    data object Done : Answer

    /** "Erledigt" on a joint reminder: it asks which one it was. */
    data object OfferDone : Answer

    /** "Später": it asks when to come back. */
    data object OfferPutOff : Answer

    /** One of the choices "Später" offers: it comes back [by] then. */
    data class PutOff(val by: Duration) : Answer

    /** "Überspringen", for all a joint reminder reminds of; a to-do is never skipped. */
    data object Skip : Answer

    /** "Heute nicht", for all a joint reminder reminds of; a to-do has none. */
    data object Drop : Answer
}
