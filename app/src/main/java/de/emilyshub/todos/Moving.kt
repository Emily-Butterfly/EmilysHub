package de.emilyshub.todos

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

    /** The day it moves to, from [today]. */
    fun from(today: LocalDate): LocalDate = when (this) {
        Tomorrow -> today.plusDays(1)
        // From a Sunday, the coming Saturday is the one after the weekend it is.
        Weekend -> today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        NextWeek -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    }
}

/**
 * A to-do moved to another day ([Todos.moveTo]): due as [from] before and as [to] after, and the day its series ended on
 * before, [endedOn], if the move pushed that end out to the new day; to undo it ([Todos.undo]).
 */
data class TodoMove(val todoId: Long, val from: Due, val to: Due, val endedOn: LocalDate? = null)

/** Due on [date] instead, at the same time if it has one; one due whenever on that day, without a time. */
internal fun Due.movedTo(date: LocalDate): Due = when (this) {
    Due.None, is Due.On -> Due.On(date)
    is Due.At -> Due.At(date, time)
}
