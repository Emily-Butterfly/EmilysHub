package de.emilyshub.routines

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/** An occurrence row joined with its routine's name. */
data class OccurrenceRow(
    val id: Long,
    val routineId: Long,
    val name: String,
    val date: LocalDate,
    val time: LocalTime,
    val status: OccurrenceStatus,
)

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines")
    fun observeAll(): Flow<List<Routine>>

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

    @Query("SELECT * FROM occurrences WHERE routine_id = :routineId AND date = :date")
    suspend fun occurrencesOn(routineId: Long, date: LocalDate): List<Occurrence>

    /** The routine's occurrences on [date] and after. */
    @Query("SELECT * FROM occurrences WHERE routine_id = :routineId AND date >= :date")
    suspend fun occurrencesFrom(routineId: Long, date: LocalDate): List<Occurrence>

    @Query("UPDATE occurrences SET time = :time WHERE id = :id")
    suspend fun moveOccurrence(id: Long, time: LocalTime)

    @Query("DELETE FROM occurrences WHERE id IN (:ids)")
    suspend fun deleteOccurrences(ids: Collection<Long>)

    @Query("UPDATE occurrences SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: OccurrenceStatus)

    @Query(
        """
        SELECT o.id AS id, o.routine_id AS routineId, r.name AS name, o.date AS date, o.time AS time, o.status AS status
        FROM occurrences o JOIN routines r ON r.id = o.routine_id
        WHERE o.date = :date
        """,
    )
    fun observeOccurrences(date: LocalDate): Flow<List<OccurrenceRow>>
}
