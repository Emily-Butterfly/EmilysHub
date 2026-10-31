package de.emilyshub.todos

import java.io.Serializable
import java.time.LocalDate

/**
 * When a series that comes again ends (SPEC.md §2, §5): never, after a number of to-dos, or on a date, that day included.
 * Serializable, so that the editors keep it across rotation and the app lock.
 */
sealed interface RecurrenceEnd : Serializable {
    data object Never : RecurrenceEnd

    /** After [times] to-dos in all, the first counted: whatever days were left out by doing one late. */
    data class AfterTimes(val times: Int) : RecurrenceEnd

    /** With the last one due by [date], that day included. */
    data class OnDate(val date: LocalDate) : RecurrenceEnd

    companion object {
        /** How many times a series may come at most. */
        val TIMES = 1..999
    }
}
