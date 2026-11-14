package de.emilyshub.ui.navigation

import java.io.Serializable

/** What the app is asked to open from outside, a reminder or a shortcut, once it is unlocked (SPEC.md §9). */
sealed interface Opening : Serializable {
    /** A screen over the tabs, over what was open. */
    data class Show(val destination: Destination) : Opening

    /**
     * "Starten" on the reminder of the occurrence [occurrenceId] (#103): its run, once the reminder is answered so, or
     * Heute if it was answered meanwhile.
     */
    data class StartRun(val occurrenceId: Long) : Opening
}
