package de.emilyshub.today

import de.emilyshub.data.InputText
import de.emilyshub.routines.DueRoutine
import de.emilyshub.todos.Due
import de.emilyshub.todos.DueTodo
import java.time.LocalDate
import java.time.LocalDateTime

/** An entry of Heute (SPEC.md §1): an occurrence of a routine, or a to-do. */
sealed interface TodayEntry {
    data class OfRoutine(val routine: DueRoutine) : TodayEntry

    data class OfTodo(val todo: DueTodo) : TodayEntry
}

/**
 * Heute in its order (SPEC.md §1, §5): the overdue to-dos at the very top, the longest overdue first; then the to-dos
 * due on the day without a time; then the routines and the to-dos with a time, by it, a routine first at the same
 * time. A to-do ticked off stays where it was due, one ticked off overdue among those without a time.
 */
fun todayList(routines: List<DueRoutine>, todos: List<DueTodo>, today: LocalDate): List<TodayEntry> {
    val entries = routines.map { TodayEntry.OfRoutine(it) } + todos.map { TodayEntry.OfTodo(it) }
    return entries.sortedWith(
        compareBy<TodayEntry> { it.group(today) }
            .thenBy { it.time() }
            .thenBy { if (it is TodayEntry.OfRoutine) 0 else 1 }
            .thenBy(InputText.byName) { it.name() },
    )
}

/** Overdue to-dos, then the to-dos of [today] without a time or ticked off overdue, then all with a time on it. */
private fun TodayEntry.group(today: LocalDate): Int = when (this) {
    is TodayEntry.OfRoutine -> TIMED
    is TodayEntry.OfTodo -> {
        val due = todo.due
        when {
            todo.overdue -> OVERDUE
            due is Due.At && due.date == today -> TIMED
            else -> WITHOUT_TIME
        }
    }
}

/** When it is due on the local clock, a day without a time at its start; one due whenever is never on Heute. */
private fun TodayEntry.time(): LocalDateTime = when (this) {
    is TodayEntry.OfRoutine -> routine.dueAt.toLocalDateTime()
    is TodayEntry.OfTodo -> when (val due = todo.due) {
        Due.None -> LocalDateTime.MAX
        is Due.On -> due.date.atStartOfDay()
        is Due.At -> due.date.atTime(due.time)
    }
}

private fun TodayEntry.name(): String = when (this) {
    is TodayEntry.OfRoutine -> routine.name
    is TodayEntry.OfTodo -> todo.title
}

private const val OVERDUE = 0
private const val WITHOUT_TIME = 1
private const val TIMED = 2
