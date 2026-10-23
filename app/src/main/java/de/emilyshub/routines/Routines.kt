package de.emilyshub.routines

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import kotlinx.coroutines.Dispatchers
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
import java.util.Locale
import kotlin.coroutines.CoroutineContext

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

    /** Due from today on. Refuses a draft with problems: the editor shows them before it gets here. */
    suspend fun create(draft: RoutineDraft): Long = withContext(io) {
        val routine = checked(draft).let {
            Routine(name = it.name, description = it.description, time = it.time, startDate = today())
        }
        database().withTransaction {
            dao.insert(routine).also { dao.addOccurrences(routine.startDate, OccurrenceStatus.Open) }
        }
    }

    suspend fun get(id: Long): Routine? = withContext(io) { dao.get(id) }

    /** Today's occurrence moves along to the new time while it is open; done or skipped, it stays as it was. */
    suspend fun update(id: Long, draft: RoutineDraft) = withContext(io) {
        val changes = checked(draft)
        database().withTransaction {
            val routine = dao.get(id) ?: return@withTransaction
            dao.update(routine.copy(name = changes.name, description = changes.description, time = changes.time))
            dao.moveOccurrences(id, from = today(), status = OccurrenceStatus.Open, time = changes.time)
        }
    }

    /** Deletes the routine with all its occurrences. */
    suspend fun delete(id: Long) = withContext(io) { dao.delete(id) }

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

    /** Undoes "done" or "skipped". */
    suspend fun reopen(occurrenceId: Long) = setStatus(occurrenceId, OccurrenceStatus.Open)

    private suspend fun setStatus(occurrenceId: Long, status: OccurrenceStatus) =
        withContext(io) { dao.setStatus(occurrenceId, status) }

    /** The local date now; it changes at midnight in the phone's current time zone. */
    fun today(): LocalDate = today(clock.instant(), clock.zone)

    /** How long until the next local day begins. */
    fun timeUntilTomorrow(): Duration = Duration.between(clock.instant(), today().plusDays(1).atStartOfDay(clock.zone))

    private fun checked(draft: RoutineDraft): RoutineDraft {
        require(draft.problems.isEmpty()) { "Invalid routine: ${draft.problems}" }
        return draft.cleaned()
    }

    private companion object {
        /** German alphabetical order, with umlauts next to their base letters. */
        val byName: Collator = Collator.getInstance(Locale.GERMAN)
    }
}
