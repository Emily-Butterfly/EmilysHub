package de.emilyshub.today

import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Priority
import de.emilyshub.todos.Due
import de.emilyshub.todos.DueTodo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Heute (SPEC.md §1): routines and to-dos by time, the overdue to-dos at the very top. */
class TodayListTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `overdue to-dos come first, then the to-dos of the day without a time, then routines and to-dos by their time`() {
        val routines = listOf(routine("Tabletten", "08:00"), routine("Lesen", "21:00"))
        val todos = listOf(
            todo("Steuer abends", Due.At(today, LocalTime.of(19, 0))),
            todo("Müll", Due.On(today)),
            todo("Seit gestern", Due.On(today.minusDays(1)), overdue = true),
            todo("Seit früh", Due.At(today, LocalTime.of(6, 30)), overdue = true),
            todo("Arzt", Due.At(today, LocalTime.of(8, 0))),
        )

        assertEquals(
            listOf("Seit gestern", "Seit früh", "Müll", "Tabletten", "Arzt", "Steuer abends", "Lesen"),
            todayList(routines, todos, today).map { it.title },
        )
    }

    @Test
    fun `a to-do ticked off today stays where it was due, an overdue one among those of the day without a time`() {
        val todos = listOf(
            todo("Gestern fällig, heute erledigt", Due.On(today.minusDays(1)), done = true),
            todo("Heute fällig", Due.On(today)),
            todo("Heute früh erledigt", Due.At(today, LocalTime.of(7, 0)), done = true),
        )

        assertEquals(
            listOf("Gestern fällig, heute erledigt", "Heute fällig", "Heute früh erledigt"),
            todayList(listOf(routine("Lesen", "21:00")), todos, today).map { it.title }.dropLast(1),
        )
    }

    private fun routine(name: String, time: String) = DueRoutine(
        occurrenceId = name.hashCode().toLong(),
        routineId = 1,
        name = name,
        dueAt = ZonedDateTime.of(today, LocalTime.parse(time), zone),
        status = OccurrenceStatus.Open,
        priority = Priority.Normal,
    )

    private fun todo(title: String, due: Due, overdue: Boolean = false, done: Boolean = false) =
        DueTodo(title.hashCode().toLong(), title, due, Priority.Normal, done = done, overdue = overdue)

    private val TodayEntry.title: String
        get() = when (this) {
            is TodayEntry.OfRoutine -> routine.name
            is TodayEntry.OfTodo -> todo.title
        }
}
