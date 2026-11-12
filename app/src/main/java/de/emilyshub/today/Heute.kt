package de.emilyshub.today

import de.emilyshub.Day
import de.emilyshub.ledger.SubscriptionReminder
import de.emilyshub.ledger.Subscriptions
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
 * day under Demnächst ([upcoming]), the notes [pinned] here, the [flame] of the overall streak, none without one, the
 * [heatmap] of the last six months, and under Demnächst too the Abos to cancel soon ([subscriptions], #80), from the first
 * day of their lead to their last day.
 */
data class HeuteDay(
    val date: LocalDate,
    val entries: List<TodayEntry>,
    val upcoming: List<TodayEntry.OfTodo>,
    val pinned: List<Note>,
    val flame: StreakFlame?,
    val heatmap: Heatmap,
    val subscriptions: List<SubscriptionReminder> = emptyList(),
)

/**
 * The day before Heute's [date], to catch up on (SPEC.md §1, Gestern nachtragen): its routines' occurrences and to-dos
 * as [entries], in Heute's order ([todayList]), the to-dos as they stood at the end of that day.
 */
data class Yesterday(val date: LocalDate, val entries: List<TodayEntry>)

/**
 * Heute (SPEC.md §1), from the routines, the to-dos, the notes and the Abos, for the day it is by [day]: what is on it,
 * and what is done with it. A screen only draws a [HeuteDay] and says what was tapped.
 */
class Heute(
    private val routines: Routines,
    private val todos: Todos,
    private val notes: Notes,
    private val subscriptions: Subscriptions,
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
        val subscriptionsDue = moments.map { it.date }.distinctUntilChanged()
            .flatMapLatest { date -> subscriptions.upcoming(date).map { date to it } }
        val done = combine(routines.history(), todos.archived()) { history, archived -> history to archived }
        combine(routinesDue, todosDue, notes.onToday(), done, subscriptionsDue) {
                (date, dueRoutines), dueTodos, pinned, (history, archived), (subscriptionsDate, dueSubscriptions) ->
            // Until all are of the same day, as one of them may not have followed the clock yet.
            if (dueTodos.date != date || subscriptionsDate != date) return@combine null
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
                subscriptions = dueSubscriptions,
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

    /** Ticks entries off on Heute, or unticks them, which undoes that. */
    val onToday = Ticking(
        routineDone = { id, done -> if (done) routines.markDone(id) else routines.reopen(id) },
        routineTick = { id, item, ticked -> routines.tick(id, item, ticked) },
        todoDone = { id, done -> if (done) todos.markDone(id) else todos.reopen(id) },
        todoTick = { id, item, ticked -> todos.tick(id, item, ticked) },
    )

    /**
     * Catches up on yesterday's entries (SPEC.md §1): done ticks one off as done then, not done unticks it. A routine
     * as [Routines.catchUp] has it; a to-do done then as [Todos.markDoneYesterday] has it, and opened again as on Heute.
     */
    val onYesterday = Ticking(
        routineDone = { id, done -> routines.catchUp(id, done) },
        routineTick = { id, item, ticked -> routines.catchUpTick(id, item, ticked) },
        todoDone = { id, done -> if (done) todos.markDoneYesterday(id) else todos.reopen(id) },
        todoTick = { id, item, ticked -> todos.tickYesterday(id, item, ticked) },
    )

    /** [onYesterday] when [catchingUp] on yesterday, [onToday] otherwise. */
    fun ticking(catchingUp: Boolean): Ticking = if (catchingUp) onYesterday else onToday

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

/** Ticking entries of one day off, or unticking them: whether a routine's occurrence or a to-do, by its id. */
class Ticking internal constructor(
    private val routineDone: suspend (occurrenceId: Long, done: Boolean) -> Unit,
    private val routineTick: suspend (occurrenceId: Long, itemId: Long, ticked: Boolean) -> Unit,
    private val todoDone: suspend (todoId: Long, done: Boolean) -> Unit,
    private val todoTick: suspend (todoId: Long, itemId: Long, ticked: Boolean) -> Unit,
) {
    /** Ticks [entry] off, or unticks it. */
    suspend fun setDone(entry: TodayEntry, done: Boolean) = when (entry) {
        is TodayEntry.OfRoutine -> routineDone(entry.routine.occurrenceId, done)
        is TodayEntry.OfTodo -> todoDone(entry.todo.todoId, done)
    }

    /** Ticks the item [itemId] of [entry]'s checklist off, or unticks it. */
    suspend fun tick(entry: TodayEntry, itemId: Long, ticked: Boolean) = when (entry) {
        is TodayEntry.OfRoutine -> routineTick(entry.routine.occurrenceId, itemId, ticked)
        is TodayEntry.OfTodo -> todoTick(entry.todo.todoId, itemId, ticked)
    }
}
