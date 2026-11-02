package de.emilyshub.data

/** How saving an editor's draft went: [Saved] under its id, or [Refused] for its problems, and nothing saved. */
sealed interface Saving<out P> {
    data class Saved(val id: Long) : Saving<Nothing>

    data class Refused<P>(val problems: Set<P>) : Saving<P>
}
