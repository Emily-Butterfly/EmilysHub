package de.emilyshub.todos

import java.io.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * How a to-do comes again once ticked off (SPEC.md §5): due anew on the day [nextDue] says. Plain Kotlin on local
 * dates, so that a day is a calendar day. Serializable, so that the editor keeps it across rotation and the app lock.
 */
sealed interface Recurrence : Serializable {

    /**
     * Whether it is due next some time after the day it was done, rather than by plan: on the next day of its rule after
     * it was due and after it was done, those missed in between left out. Only those with an interval can.
     */
    val fromDone: Boolean get() = false

    /** The day it is due next after it was due on [due] and done on [doneOn]: always after both. */
    fun nextDue(due: LocalDate, doneOn: LocalDate): LocalDate

    /** The same rule for a to-do due on [due]: on its day of the month or of the year, if it goes by one. */
    fun anchoredTo(due: LocalDate): Recurrence = this

    /** The same rule, due next [fromDone] or by plan, if it has an interval for that. */
    fun countingFromDone(fromDone: Boolean): Recurrence = this

    data object Daily : Recurrence {
        override fun nextDue(due: LocalDate, doneOn: LocalDate): LocalDate = maxOf(due, doneOn).plusDays(1)
    }

    /** On the chosen [days] of every week. */
    data class Weekdays(val days: Set<DayOfWeek>) : Recurrence {
        override fun nextDue(due: LocalDate, doneOn: LocalDate): LocalDate {
            val after = maxOf(due, doneOn)
            return (1L..DAYS_IN_WEEK).map(after::plusDays).firstOrNull { it.dayOfWeek in days } ?: after.plusDays(DAYS_IN_WEEK)
        }
    }

    /** Every [days] days. */
    data class EveryNDays(val days: Int, override val fromDone: Boolean = false) : Recurrence {
        override fun nextDue(due: LocalDate, doneOn: LocalDate): LocalDate {
            val step = days.coerceAtLeast(1).toLong()
            if (fromDone) return doneOn.plusDays(step)
            val behind = ChronoUnit.DAYS.between(due, doneOn).coerceAtLeast(0)
            return due.plusDays((behind / step + 1) * step)
        }

        override fun countingFromDone(fromDone: Boolean) = copy(fromDone = fromDone)
    }

    /** Every month on its [day], or on the last one of a month without it. */
    data class Monthly(val day: Int, override val fromDone: Boolean = false) : Recurrence {
        override fun nextDue(due: LocalDate, doneOn: LocalDate): LocalDate {
            if (fromDone) return doneOn.plusMonths(1)
            val after = maxOf(due, doneOn)
            val thisMonth = dayIn(YearMonth.from(after))
            return if (thisMonth.isAfter(after)) thisMonth else dayIn(YearMonth.from(after).plusMonths(1))
        }

        override fun anchoredTo(due: LocalDate) = copy(day = due.dayOfMonth)

        override fun countingFromDone(fromDone: Boolean) = copy(fromDone = fromDone)

        private fun dayIn(month: YearMonth): LocalDate = month.atDay(day.coerceIn(1, month.lengthOfMonth()))
    }

    /** Every year on its [date], or on 28 February for 29 February in a year without it. */
    data class Yearly(val date: MonthDay, override val fromDone: Boolean = false) : Recurrence {
        override fun nextDue(due: LocalDate, doneOn: LocalDate): LocalDate {
            if (fromDone) return doneOn.plusYears(1)
            val after = maxOf(due, doneOn)
            val thisYear = date.atYear(after.year)
            return if (thisYear.isAfter(after)) thisYear else date.atYear(after.year + 1)
        }

        override fun anchoredTo(due: LocalDate) = copy(date = MonthDay.from(due))

        override fun countingFromDone(fromDone: Boolean) = copy(fromDone = fromDone)
    }

    private companion object {
        const val DAYS_IN_WEEK = 7L
    }
}
