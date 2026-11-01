package de.emilyshub.reminders

import de.emilyshub.routines.Reminder
import de.emilyshub.todos.TodoReminder

/**
 * A reminder due to be on the screen (SPEC.md §3, §5): of a routine's occurrence, alone or the joint one of coupled
 * routines, or of a to-do; and what it is [about], which tells it apart on the screen and in the answers to it.
 */
sealed interface DueReminder {
    val about: Alarmed

    data class OfOccurrence(val reminder: Reminder) : DueReminder {
        override val about get() = Alarmed.Occurrence(reminder.occurrenceId)
    }

    data class OfTodo(val reminder: TodoReminder) : DueReminder {
        override val about get() = Alarmed.Todo(reminder.todoId)
    }
}
