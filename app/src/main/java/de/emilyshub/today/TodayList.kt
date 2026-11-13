package de.emilyshub.today

import de.emilyshub.data.InputText
import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.todos.Due
import de.emilyshub.todos.DueTodo
import java.time.LocalDate
import java.time.LocalDateTime

/** An entry of Heute (SPEC.md §1): an occurrence of a routine, or a to-do. */
sealed interface TodayEntry {
    /** The routine's name, or the to-do's title. */
    val name: String

    data class OfRoutine(val routine: DueRoutine) : TodayEntry {
        override val name: String get() = routine.name
    }

    data class OfTodo(val todo: DueTodo) : TodayEntry {
        override val name: String get() = todo.title
    }
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
            .thenBy(InputText.byName) { it.name },
    )
}

/**
 * Heute in its blocks (SPEC.md §1, Drei Blöcke): the to-dos [overdue], the longest overdue first; what is still open
 * of the day [upNext], in Heute's order ([todayList]), past its time too, and yesterday's routines whose time window
 * is still open; and what is [past]: done, skipped, missed or dropped, by its time.
 */
data class TodayBlocks(
    val overdue: List<TodayEntry.OfTodo>,
    val upNext: List<TodayEntry>,
    val past: List<TodayEntry>,
) {
    /** Whether there is nothing on Heute at all. */
    val isEmpty: Boolean get() = overdue.isEmpty() && upNext.isEmpty() && past.isEmpty()

    /** All of Heute's entries, block by block. */
    val all: List<TodayEntry> get() = overdue + upNext + past

    /** How far the day is: what is done of all entries but the skipped ones, by hand or by a coupled routine. */
    val stand: DayStand
        get() {
            val counted = all.filterNot { it.skipped }
            return DayStand(done = counted.count { it.done }, of = counted.size)
        }

    /** What [past] holds, by its status. */
    val summary: PastSummary
        get() = PastSummary(
            done = past.count { it.done },
            skipped = past.count { it.skipped },
            missed = past.count { it is TodayEntry.OfRoutine && it.routine.status == OccurrenceStatus.Missed },
            dropped = past.count { it is TodayEntry.OfRoutine && it.routine.status == OccurrenceStatus.Dropped },
        )
}

/** How far the day is (SPEC.md §1, Kopfzeile): [done] of [of] entries. */
data class DayStand(val done: Int, val of: Int)

/** What is over on Heute (SPEC.md §1, Schon vorbei), counted by its status; skipped by a coupled routine among [skipped]. */
data class PastSummary(val done: Int, val skipped: Int, val missed: Int, val dropped: Int)

/**
 * Heute in its blocks ([TodayBlocks]): the overdue to-dos, then what is open, both in Heute's order ([todayList]);
 * then what is over, by its time, a routine first at the same time.
 */
fun todayBlocks(routines: List<DueRoutine>, todos: List<DueTodo>, today: LocalDate): TodayBlocks {
    val list = todayList(routines, todos, today)
    val (open, past) = list.partition { it.open }
    val (overdue, upNext) = open.partition { it is TodayEntry.OfTodo && it.todo.overdue }
    return TodayBlocks(
        overdue = overdue.map { it as TodayEntry.OfTodo },
        upNext = upNext,
        past = past.sortedWith(
            compareBy<TodayEntry> { it.time() }
                .thenBy { if (it is TodayEntry.OfRoutine) 0 else 1 }
                .thenBy(InputText.byName) { it.name },
        ),
    )
}

/** Still to do: a to-do not ticked off, an occurrence open, and not done by another of its group. */
private val TodayEntry.open: Boolean
    get() = when (this) {
        is TodayEntry.OfRoutine -> routine.status == OccurrenceStatus.Open && routine.coveredBy == null
        is TodayEntry.OfTodo -> !todo.done
    }

/** Ticked off. */
private val TodayEntry.done: Boolean
    get() = when (this) {
        is TodayEntry.OfRoutine -> routine.status == OccurrenceStatus.Done
        is TodayEntry.OfTodo -> todo.done
    }

/** Skipped, by hand or by another of its group. */
private val TodayEntry.skipped: Boolean
    get() = this is TodayEntry.OfRoutine && (routine.status == OccurrenceStatus.Skipped || routine.coveredBy != null)

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

private const val OVERDUE = 0
private const val WITHOUT_TIME = 1
private const val TIMED = 2
