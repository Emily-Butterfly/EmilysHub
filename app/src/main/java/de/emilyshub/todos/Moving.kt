package de.emilyshub.todos

import java.io.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Where a to-do moves to with one tap, from Heute or the To-dos tab (SPEC.md §1, Menü je Eintrag): counted from today,
 * not from when it was due, so that one overdue for a week is due tomorrow with "Morgen".
 */
enum class MoveTo {
    /** The day after today. */
    Tomorrow,

    /** The coming Saturday; on a Saturday or Sunday, the one after. */
    Weekend,

    /** The coming Monday; on a Monday, the one after. */
    NextWeek,
    ;

    /** Where it moves to from [today]: to the weekend, due from its Saturday until its Sunday; else that one day. */
    fun target(today: LocalDate): MoveTarget = from(today).let { date -> MoveTarget(date, until = date.plusDays(1).takeIf { this == Weekend }) }

    /** The day it moves to, from [today]. */
    fun from(today: LocalDate): LocalDate = when (this) {
        Tomorrow -> today.plusDays(1)
        // From a Sunday, the coming Saturday is the one after the weekend it is.
        Weekend -> today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        NextWeek -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    }
}

/**
 * Where a to-do is moved to: due on [date], and, moved to the weekend, still due [until] its Sunday, overdue only after
 * it; none, that day only.
 */
data class MoveTarget(val date: LocalDate, val until: LocalDate? = null) : Serializable

/**
 * A to-do moved to another day ([Todos.moveTo]): due as [from] before and as [to] after, until [fromUntil] and [toUntil]
 * if it was due over a weekend, and the day its series ended on before, [endedOn], if the move pushed that end out to
 * the new day; to undo it ([Todos.undo]).
 */
data class TodoMove(
    val todoId: Long,
    val from: Due,
    val to: Due,
    val endedOn: LocalDate? = null,
    val fromUntil: LocalDate? = null,
    val toUntil: LocalDate? = null,
) : Serializable

/** Due on [date] instead, at the same time if it has one; one due whenever on that day, without a time. */
internal fun Due.movedTo(date: LocalDate): Due = when (this) {
    Due.None, is Due.On -> Due.On(date)
    is Due.At -> Due.At(date, time)
}
