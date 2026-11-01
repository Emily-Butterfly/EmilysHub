package de.emilyshub.todos

import de.emilyshub.routines.dueAt
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * When a to-do wakes, besides reminding when it is due (SPEC.md §5): [Before] its due day, coming along when it is put
 * off or comes again, or [At] a fixed moment, which holds for that to-do only, also without a due. Always at a [time] on
 * the local wall clock, as due times are.
 */
sealed interface WakeTime : Serializable {
    val time: LocalTime

    /** So many [days] before the day it is due, 0 being that day itself, at [time]; only with a due day. */
    data class Before(val days: Int, override val time: LocalTime) : WakeTime

    /** On [date] at [time], however it is due. */
    data class At(val date: LocalDate, override val time: LocalTime) : WakeTime

    /** The day it wakes on for a to-do [due] then; none for [Before] without a due day. */
    fun date(due: Due): LocalDate? = when (this) {
        is Before -> due.date?.minusDays(days.toLong())
        is At -> date
    }

    /** When it wakes on the wall clock, for a to-do [due] then; none for [Before] without a due day. */
    fun localMoment(due: Due): LocalDateTime? = date(due)?.atTime(time)

    /** When it wakes in [zone], as [dueAt] has a time there, for a to-do [due] then; none for [Before] without a due day. */
    fun moment(due: Due, zone: ZoneId): Instant? = date(due)?.let { dueAt(it, time, zone).toInstant() }

    companion object {
        /** How many days before it is due a to-do can wake. */
        val DAYS_BEFORE = 0..30
    }
}
