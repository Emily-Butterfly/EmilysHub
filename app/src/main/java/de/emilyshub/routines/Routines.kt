package de.emilyshub.routines

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/**
 * The routines and their occurrences (SPEC.md §2). [database] is only called on [io], since the first
 * call opens it. Writes run to the end once started, even if the screen that started them goes away:
 * otherwise a save cut short by the app lock or a rotation could leave the editor open over a routine
 * that was already saved.
 */
class Routines(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().routines()

    /** By their first time, then by name. */
    fun all(): Flow<List<Routine>> = flow {
        emitAll(dao.observeAll().map { it.sortedWith(compareBy<Routine> { it.times.first() }.thenBy(byName, Routine::name)) })
    }.flowOn(io)

    /**
     * Due from now on: a time already past today does not count today, so a routine never starts out
     * overdue. Refuses a draft with problems; the editor shows them before it gets here.
     */
    suspend fun create(draft: RoutineDraft): Long = write {
        val routine = checked(draft).let {
            Routine(
                name = it.name,
                description = it.description,
                times = it.times,
                repetition = it.repetition,
                plannedFrom = now(),
            )
        }
        database().withTransaction {
            dao.insert(routine).also { plan(today()) }
        }
    }

    suspend fun get(id: Long): Routine? = withContext(io) { dao.get(id) }

    /**
     * New times and repetition apply from now on, as [replanDay] describes: today's open occurrences
     * follow them where that makes nothing overdue, while the past and what was ticked off stay as they were.
     */
    suspend fun update(id: Long, draft: RoutineDraft) = write {
        val changes = checked(draft)
        database().withTransaction {
            val routine = dao.get(id) ?: return@withTransaction
            val today = today()
            // Today's list as it was before the change, for the change to apply to.
            plan(today)
            val now = now()
            val rescheduled = changes.times != routine.times || changes.repetition != routine.repetition
            val changed = routine.copy(
                name = changes.name,
                description = changes.description,
                times = changes.times,
                repetition = changes.repetition,
                plannedFrom = if (rescheduled) now else routine.plannedFrom,
            )
            dao.update(changed)
            val planned = dao.occurrencesFrom(id, today).groupBy { it.date }
            (planned.keys + today).forEach { date -> replan(changed, date, planned[date].orEmpty(), now) }
        }
    }

    /**
     * Whether a routine with [times] and [repetition], planned from now on, would ever come due: one
     * on a single date would not once all its times there have gone by.
     */
    fun comesDue(times: List<LocalTime>, repetition: Repetition): Boolean =
        repetition !is Repetition.Once || times.any { isDueAtOrAfter(now(), repetition.date, it, clock.zone) }

    /** Deletes the routine with all its occurrences. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    fun dueOn(date: LocalDate): Flow<List<DueRoutine>> = flow {
        plan(date)
        emitAll(
            dao.observeOccurrences(date).map { rows ->
                rows.map { DueRoutine(it.id, it.routineId, it.name, dueAt(it.date, it.time, clock.zone), it.status) }
                    .sortedWith(compareBy<DueRoutine> { it.dueAt.toInstant() }.thenBy(byName) { it.name })
            },
        )
    }.flowOn(io)

    suspend fun markDone(occurrenceId: Long) = setStatus(occurrenceId, OccurrenceStatus.Done)

    suspend fun skip(occurrenceId: Long) = setStatus(occurrenceId, OccurrenceStatus.Skipped)

    /**
     * Undoes "done" or "skipped". If the routine's times changed meanwhile, the occurrence is planned anew
     * like an open one when they changed ([replanDay]): it moves to the new time it stood in for while
     * that is ahead, and goes if the routine no longer has a time for it and it is still to come.
     */
    suspend fun reopen(occurrenceId: Long) = write {
        database().withTransaction {
            val occurrence = dao.occurrence(occurrenceId) ?: return@withTransaction
            val routine = dao.get(occurrence.routineId) ?: return@withTransaction
            dao.setStatus(occurrenceId, OccurrenceStatus.Open)
            replan(routine, occurrence.date, dao.occurrencesOn(routine.id, occurrence.date), now())
        }
    }

    private suspend fun setStatus(occurrenceId: Long, status: OccurrenceStatus) = write { dao.setStatus(occurrenceId, status) }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    /** The local date now; it changes at midnight in the phone's current time zone. */
    fun today(): LocalDate = today(clock.instant(), clock.zone)

    /** How long until the next local day begins. */
    fun timeUntilTomorrow(): Duration = Duration.between(clock.instant(), today().plusDays(1).atStartOfDay(clock.zone))

    private fun now(): Instant = clock.instant()

    /** Plans the occurrences on [date] of the routines that have none then yet, at their times from when they were planned. */
    private suspend fun plan(date: LocalDate) = database().withTransaction {
        val occurrences = dao.unplannedOn(date).flatMap { routine ->
            routine.plannedTimesOn(date, clock.zone).map { openOccurrence(routine, date, it) }
        }
        dao.insertOccurrences(occurrences)
    }

    /** Brings the routine's [occurrences] on [date] in line with its times from [now] on. */
    private suspend fun replan(routine: Routine, date: LocalDate, occurrences: List<Occurrence>, now: Instant) {
        val changes = replanDay(occurrences, routine.timesOn(date)) { isDueAtOrAfter(now, date, it, clock.zone) }
        changes.moved.forEach { (id, time) -> dao.moveOccurrence(id, time) }
        dao.deleteOccurrences(changes.removed)
        dao.insertOccurrences(changes.added.map { openOccurrence(routine, date, it) })
    }

    private fun openOccurrence(routine: Routine, date: LocalDate, time: LocalTime) =
        Occurrence(routineId = routine.id, date = date, time = time, status = OccurrenceStatus.Open)

    private fun checked(draft: RoutineDraft): RoutineDraft {
        require(draft.problems.isEmpty()) { "Invalid routine: ${draft.problems}" }
        return draft.cleaned()
    }

    private companion object {
        /** German alphabetical order, with umlauts next to their base letters. */
        val byName: Collator = Collator.getInstance(Locale.GERMAN)
    }
}
