package de.emilyshub.routines

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** An occurrence with its routine, which says how it is reminded of and how long it stays open. */
data class OccurrenceOfRoutine(
    @Embedded val occurrence: Occurrence,
    @Relation(parentColumn = "routine_id", entityColumn = "id") val routine: Routine,
)

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines")
    fun observeAll(): Flow<List<Routine>>

    @Query("SELECT * FROM routines")
    suspend fun getAll(): List<Routine>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun get(id: Long): Routine?

    @Insert
    suspend fun insert(routine: Routine): Long

    @Update
    suspend fun update(routine: Routine)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long)

    /** The routines that have no occurrence on [date] yet. */
    @Query("SELECT * FROM routines WHERE NOT EXISTS (SELECT 1 FROM occurrences WHERE routine_id = routines.id AND date = :date)")
    suspend fun unplannedOn(date: LocalDate): List<Routine>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrences(occurrences: List<Occurrence>)

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun occurrence(id: Long): Occurrence?

    /** Every routine's occurrences on [date] and after, and those put off, whenever they were due. */
    @Query("SELECT * FROM occurrences WHERE date >= :date OR remind_at IS NOT NULL")
    suspend fun occurrencesToRemindFrom(date: LocalDate): List<Occurrence>

    /** The open occurrences from [from] to [to], and those put off, whenever they were due. */
    @Transaction
    @Query("SELECT * FROM occurrences WHERE status = :open AND (date BETWEEN :from AND :to OR remind_at IS NOT NULL)")
    suspend fun openToRemind(from: LocalDate, to: LocalDate, open: OccurrenceStatus): List<OccurrenceOfRoutine>

    /** Every open occurrence, whenever it was due. */
    @Transaction
    @Query("SELECT * FROM occurrences WHERE status = :open")
    suspend fun allOpen(open: OccurrenceStatus): List<OccurrenceOfRoutine>

    @Transaction
    @Query("SELECT * FROM occurrences WHERE id IN (:ids)")
    suspend fun occurrencesWithRoutines(ids: Collection<Long>): List<OccurrenceOfRoutine>

    @Transaction
    @Query("SELECT * FROM occurrences WHERE id = :id")
    fun observeOccurrenceWithRoutine(id: Long): Flow<OccurrenceOfRoutine?>

    @Query("SELECT * FROM occurrences WHERE routine_id = :routineId AND date = :date")
    suspend fun occurrencesOn(routineId: Long, date: LocalDate): List<Occurrence>

    /** The routine's occurrences on [date] and after. */
    @Query("SELECT * FROM occurrences WHERE routine_id = :routineId AND date >= :date")
    suspend fun occurrencesFrom(routineId: Long, date: LocalDate): List<Occurrence>

    /** Moves it to [time], where it is reminded of at that time again. */
    @Query("UPDATE occurrences SET time = :time, remind_at = NULL WHERE id = :id")
    suspend fun moveOccurrence(id: Long, time: LocalTime)

    @Query("UPDATE occurrences SET remind_at = :until WHERE id = :id")
    suspend fun putOff(id: Long, until: Instant)

    @Query("DELETE FROM occurrences WHERE id IN (:ids)")
    suspend fun deleteOccurrences(ids: Collection<Long>)

    /** Sets the status of an occurrence not [missed]: once missed, it stays so. */
    @Query("UPDATE occurrences SET status = :status WHERE id = :id AND status != :missed")
    suspend fun setStatus(id: Long, status: OccurrenceStatus, missed: OccurrenceStatus = OccurrenceStatus.Missed)

    @Query("UPDATE occurrences SET status = :status WHERE id IN (:ids)")
    suspend fun setStatuses(ids: Collection<Long>, status: OccurrenceStatus)

    /** The occurrences from [from] to [to], each with its routine. */
    @Transaction
    @Query("SELECT * FROM occurrences WHERE date BETWEEN :from AND :to")
    fun observeOccurrencesWithRoutines(from: LocalDate, to: LocalDate): Flow<List<OccurrenceOfRoutine>>
}
