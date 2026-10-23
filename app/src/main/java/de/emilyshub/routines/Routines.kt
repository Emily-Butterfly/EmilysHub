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

    /** By time, then by name. */
    fun all(): Flow<List<Routine>> = flow {
        emitAll(dao.observeAll().map { it.sortedWith(compareBy(Routine::time).thenBy(byName, Routine::name)) })
    }.flowOn(io)

    /**
     * Due from today on, or from tomorrow if its time has already passed today: a routine never starts
     * out overdue. Refuses a draft with problems; the editor shows them before it gets here.
     */
    suspend fun create(draft: RoutineDraft): Long = write {
        val routine = checked(draft).let {
            Routine(name = it.name, description = it.description, time = it.time, startDate = firstDueDate(it.time))
        }
        database().withTransaction {
            dao.insert(routine).also { dao.addOccurrences(today(), OccurrenceStatus.Open) }
        }
    }

    suspend fun get(id: Long): Routine? = withContext(io) { dao.get(id) }

    /** Today's occurrence moves along to the new time while it is open; done or skipped, it stays as it was. */
    suspend fun update(id: Long, draft: RoutineDraft) = write {
        val changes = checked(draft)
        database().withTransaction {
            val routine = dao.get(id) ?: return@withTransaction
            dao.update(routine.copy(name = changes.name, description = changes.description, time = changes.time))
            dao.moveOccurrences(id, from = today(), status = OccurrenceStatus.Open, time = changes.time)
        }
    }

    /** Deletes the routine with all its occurrences. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    fun dueOn(date: LocalDate): Flow<List<DueRoutine>> = flow {
        dao.addOccurrences(date, OccurrenceStatus.Open)
        emitAll(
            dao.observeOccurrences(date).map { rows ->
                rows.map { DueRoutine(it.id, it.routineId, it.name, dueAt(it.date, it.time, clock.zone), it.status) }
                    .sortedWith(compareBy<DueRoutine> { it.dueAt.toInstant() }.thenBy(byName) { it.name })
            },
        )
    }.flowOn(io)

    suspend fun markDone(occurrenceId: Long) = setStatus(occurrenceId, OccurrenceStatus.Done)

    suspend fun skip(occurrenceId: Long) = setStatus(occurrenceId, OccurrenceStatus.Skipped)

    /** Undoes "done" or "skipped"; if the routine's time changed meanwhile, it is due at the new one. */
    suspend fun reopen(occurrenceId: Long) = write { dao.reopen(occurrenceId, OccurrenceStatus.Open) }

    private suspend fun setStatus(occurrenceId: Long, status: OccurrenceStatus) = write { dao.setStatus(occurrenceId, status) }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    /** The local date now; it changes at midnight in the phone's current time zone. */
    fun today(): LocalDate = today(clock.instant(), clock.zone)

    /** How long until the next local day begins. */
    fun timeUntilTomorrow(): Duration = Duration.between(clock.instant(), today().plusDays(1).atStartOfDay(clock.zone))

    private fun firstDueDate(time: LocalTime): LocalDate {
        val today = today()
        return if (dueAt(today, time, clock.zone).toInstant().isAfter(clock.instant())) today else today.plusDays(1)
    }

    private fun checked(draft: RoutineDraft): RoutineDraft {
        require(draft.problems.isEmpty()) { "Invalid routine: ${draft.problems}" }
        return draft.cleaned()
    }

    private companion object {
        /** German alphabetical order, with umlauts next to their base letters. */
        val byName: Collator = Collator.getInstance(Locale.GERMAN)
    }
}
