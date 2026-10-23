package de.emilyshub.routines

import java.io.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * On which days a routine is due (SPEC.md §2, Wiederholung). Plain Kotlin on local dates, so that a
 * day is a calendar day: months, leap days and clock changes need no special care. Serializable, so
 * that the editor keeps it across rotation and the app lock.
 */
sealed interface Repetition : Serializable {
    fun isDueOn(date: LocalDate): Boolean

    data object Daily : Repetition {
        override fun isDueOn(date: LocalDate) = true
    }

    /** On the chosen [days] of every week. */
    data class Weekdays(val days: Set<DayOfWeek>) : Repetition {
        override fun isDueOn(date: LocalDate) = date.dayOfWeek in days
    }

    /** Every [days] days, starting on [from]: counted in calendar days, never before [from]. */
    data class EveryNDays(val days: Int, val from: LocalDate) : Repetition {
        override fun isDueOn(date: LocalDate): Boolean {
            val since = ChronoUnit.DAYS.between(from, date)
            return since >= 0 && since % days == 0L
        }
    }

    /** Only on [date]. */
    data class Once(val date: LocalDate) : Repetition {
        override fun isDueOn(date: LocalDate) = date == this.date
    }
}
