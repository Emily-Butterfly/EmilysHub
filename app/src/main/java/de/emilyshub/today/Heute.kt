package de.emilyshub.today

import de.emilyshub.Day
import de.emilyshub.notes.Note
import de.emilyshub.notes.Notes
import de.emilyshub.routines.DueRoutine
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Routines
import de.emilyshub.stats.Heatmap
import de.emilyshub.stats.RoutineHistory
import de.emilyshub.stats.StreakFlame
import de.emilyshub.stats.flame
import de.emilyshub.todos.DueTodo
import de.emilyshub.todos.Todos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate

/**
 * Heute as it stands on [date] (SPEC.md §1): its [entries] in their order ([todayList]), the to-dos shown before their
 * day under Demnächst ([upcoming]), the notes [pinned] here, the [flame] of the overall streak, none without one, and
 * the [heatmap] of the last six months.
 */
data class HeuteDay(
    val date: LocalDate,
    val entries: List<TodayEntry>,
    val upcoming: List<TodayEntry.OfTodo>,
    val pinned: List<Note>,
    val flame: StreakFlame?,
    val heatmap: Heatmap,
)

/**
 * The day before Heute's [date], to catch up on (SPEC.md §1, Gestern nachtragen): its routines' occurrences and to-dos
 * as [entries], in Heute's order ([todayList]), the to-dos as they stood at the end of that day.
 */
data class Yesterday(val date: LocalDate, val entries: List<TodayEntry>)

/**
 * Heute (SPEC.md §1), from the routines, the to-dos and the notes, for the day it is by [day]: what is on it, and what
 * is done with it. A screen only draws a [HeuteDay] and says what was tapped.
 */
class Heute(
    private val routines: Routines,
    private val todos: Todos,
    private val notes: Notes,
    private val day: Day,
) {

    /**
     * Heute for the day it is, once all of it is there: anew as the day turns, each minute, for to-dos turning
     * overdue, as the clock or time zone changes, and as anything on it changes. Never half of one day and half of the
     * next: all of a [HeuteDay] is of its [HeuteDay.date].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun day(): Flow<HeuteDay> = channelFlow {
        // One clock for all parts, so that they turn to the new day together.
        val moments = day.minutes().map { Moment(it, day.dayOf(it)) }.stateIn(this)
        // The routines' occurrences only change with the day; the to-dos turn overdue by the minute.
        val routinesDue = moments.map { it.date }.distinctUntilChanged()
            .flatMapLatest { date -> routines.dueOn(date).map { date to it } }
        val todosDue = moments.flatMapLatest { (now, date) ->
            combine(todos.dueOn(date, now), todos.upcoming(date, now)) { due, upcoming -> TodosDue(date, due, upcoming) }
        }
        val done = combine(routines.history(), todos.archived()) { history, archived -> history to archived }
        combine(routinesDue, todosDue, notes.onToday(), done) { (date, dueRoutines), dueTodos, pinned, (history, archived) ->
            // Until both are of the same day, as one of them may not have followed the clock yet.
            if (dueTodos.date != date) return@combine null
            HeuteDay(
                date = date,
                entries = todayList(dueRoutines, dueTodos.due, date),
                upcoming = dueTodos.upcoming.map { TodayEntry.OfTodo(it) },
                pinned = pinned,
                flame = RoutineHistory(history).flame(date),
                heatmap = Heatmap.of(
                    today = date,
                    routinesDone = history.filter { it.status == OccurrenceStatus.Done }.map { it.date },
                    todosDone = archived.mapNotNull { it.doneAt },
                    // The zone now, so that the to-dos move to their days in a new one.
                    zone = day.zone(),
                ),
            )
        }.filterNotNull().collect { send(it) }
    }

    /**
     * Yesterday to catch up on (SPEC.md §1, Gestern nachtragen), the day before the one it is by [day]: all its
     * occurrences, whatever their status, and the to-dos due by then, open or done that day, as [Todos.dueOn] has them
     * at its end. Anew as the day turns, and as anything on it changes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun yesterday(): Flow<Yesterday> = day.dates().map { it.minusDays(1) }.flatMapLatest { date ->
        combine(routines.dueOn(date), todos.dueOn(date, todos.endOf(date))) { dueRoutines, dueTodos ->
            Yesterday(date, todayList(dueRoutines, dueTodos, date))
        }
    }

    /**
     * Catches up on yesterday's [entry] (SPEC.md §1): [done] ticks it off as done then, not [done] unticks it. A routine
     * as [Routines.catchUp] has it; a to-do done then as [Todos.markDoneYesterday] has it, and opened again as on Heute.
     */
    suspend fun catchUp(entry: TodayEntry, done: Boolean) {
        when (entry) {
            is TodayEntry.OfRoutine -> routines.catchUp(entry.routine.occurrenceId, done)
            is TodayEntry.OfTodo -> if (done) todos.markDoneYesterday(entry.todo.todoId) else todos.reopen(entry.todo.todoId)
        }
    }

    /** Ticks the item [itemId] of the checklist of yesterday's [entry] off, or unticks it, as [catchUp] has it. */
    suspend fun catchUpTick(entry: TodayEntry, itemId: Long, ticked: Boolean) {
        when (entry) {
            is TodayEntry.OfRoutine -> routines.catchUpTick(entry.routine.occurrenceId, itemId, ticked)
            is TodayEntry.OfTodo -> todos.tickYesterday(entry.todo.todoId, itemId, ticked)
        }
    }

    /** Ticks [entry] off, or unticks it, which undoes that. */
    suspend fun setDone(entry: TodayEntry, done: Boolean) {
        when (entry) {
            is TodayEntry.OfRoutine -> {
                val id = entry.routine.occurrenceId
                if (done) routines.markDone(id) else routines.reopen(id)
            }
            is TodayEntry.OfTodo -> if (done) todos.markDone(entry.todo.todoId) else todos.reopen(entry.todo.todoId)
        }
    }

    /** Ticks the item [itemId] of [entry]'s checklist off, or unticks it. */
    suspend fun tick(entry: TodayEntry, itemId: Long, ticked: Boolean) {
        when (entry) {
            is TodayEntry.OfRoutine -> routines.tick(entry.routine.occurrenceId, itemId, ticked)
            is TodayEntry.OfTodo -> todos.tick(entry.todo.todoId, itemId, ticked)
        }
    }

    /** Skips the routine's occurrence (SPEC.md §2). */
    suspend fun skip(routine: DueRoutine) = routines.skip(routine.occurrenceId)

    /** "Heute nicht": drops the routine for the rest of the day (SPEC.md §2). */
    suspend fun drop(routine: DueRoutine) = routines.dropForToday(routine.occurrenceId)

    /** Undoes a skip or "Heute nicht". */
    suspend fun undo(routine: DueRoutine) = routines.reopen(routine.occurrenceId)

    /** Takes the note off Heute. */
    suspend fun unpin(noteId: Long) = notes.pinOnToday(noteId, pinned = false)

    private data class Moment(val now: Instant, val date: LocalDate)

    private data class TodosDue(val date: LocalDate, val due: List<DueTodo>, val upcoming: List<DueTodo>)
}

/** What an occurrence on Heute offers besides its box. */
enum class RoutineOffer {
    /** "Überspringen", and "Heute nicht" in its menu: while it is open. */
    SkipOrDrop,

    /** "Rückgängig": once skipped or dropped by hand. */
    Undo,

    /** Nothing: done, missed, or done by another of its group. */
    None,
}

/** Done by another of its group (SPEC.md §2): as good as done, until that one is undone. */
val DueRoutine.covered: Boolean get() = coveredBy != null

/** Whether its box can be ticked: missed is how it ended, one dropped is undone first, and one covered is done already. */
val DueRoutine.tickable: Boolean
    get() = status != OccurrenceStatus.Missed && status != OccurrenceStatus.Dropped && !covered

/**
 * Whether its box, and its checklist, can be ticked or unticked when catching up on yesterday (SPEC.md §1): missed,
 * dropped and open ones are ticked off late, and done ones unticked; skipped ones, by hand or by another of its group,
 * stay as they are.
 */
val DueRoutine.catchUpTickable: Boolean get() = status != OccurrenceStatus.Skipped && !covered

/** What it offers besides its box, by its status; what [Routines.reopen] refuses, it does not offer. */
val DueRoutine.offer: RoutineOffer
    get() = when (status) {
        OccurrenceStatus.Open -> RoutineOffer.SkipOrDrop
        OccurrenceStatus.Skipped, OccurrenceStatus.Dropped -> if (covered) RoutineOffer.None else RoutineOffer.Undo
        OccurrenceStatus.Done, OccurrenceStatus.Missed -> RoutineOffer.None
    }
