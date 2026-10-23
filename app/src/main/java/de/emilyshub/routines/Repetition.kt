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

    /** The first day it is due on [date] or after; none if it never is again. */
    fun firstDueFrom(date: LocalDate): LocalDate?

    data object Daily : Repetition {
        override fun isDueOn(date: LocalDate) = true

        override fun firstDueFrom(date: LocalDate) = date
    }

    /** On the chosen [days] of every week. */
    data class Weekdays(val days: Set<DayOfWeek>) : Repetition {
        override fun isDueOn(date: LocalDate) = date.dayOfWeek in days

        override fun firstDueFrom(date: LocalDate) = (0L..6L).map(date::plusDays).firstOrNull(::isDueOn)
    }

    /** Every [days] days, starting on [from]: counted in calendar days, never before [from]. */
    data class EveryNDays(val days: Int, val from: LocalDate) : Repetition {
        override fun isDueOn(date: LocalDate): Boolean {
            val since = ChronoUnit.DAYS.between(from, date)
            return since >= 0 && since % days == 0L
        }

        override fun firstDueFrom(date: LocalDate): LocalDate {
            if (date.isBefore(from)) return from
            val sinceLast = ChronoUnit.DAYS.between(from, date) % days
            return if (sinceLast == 0L) date else date.plusDays(days - sinceLast)
        }
    }

    /** Only on [date]. */
    data class Once(val date: LocalDate) : Repetition {
        override fun isDueOn(date: LocalDate) = date == this.date

        override fun firstDueFrom(date: LocalDate) = this.date.takeUnless { date.isAfter(it) }
    }
}
