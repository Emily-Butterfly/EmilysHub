package de.emilyshub.routines

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import de.emilyshub.data.InputText
import de.emilyshub.places.Place
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** An occurrence with its routine, which says how it is reminded of, where, and how long it stays open. */
data class OccurrenceOfRoutine(
    @Embedded val occurrence: Occurrence,
    @Relation(parentColumn = "routine_id", entityColumn = "id") val routine: Routine,
    @Relation(parentColumn = "routine_id", entityColumn = "routine_id") val routinePlaces: List<RoutinePlace>,
) {
    /** The places it pops up at; none: everywhere. */
    val placeIds: Set<Long> get() = routinePlaces.map { it.placeId }.toSet()

    /**
     * Whether at its reminder time [time] it pops up only at its places: bound to some, and not popped up yet, at its
     * first reminder time or waiting since.
     */
    fun isHeldForPlaces(time: Instant, zone: ZoneId): Boolean =
        placeIds.isNotEmpty() && (occurrence.waiting || time == occurrence.reminderTime(zone))
}

/** An occurrence on Heute: with its routine, the routine's checklist, and what of it is ticked off on this occurrence. */
data class OccurrenceWithChecklist(
    @Embedded val occurrence: Occurrence,
    @Relation(parentColumn = "routine_id", entityColumn = "id") val routine: Routine,
    @Relation(parentColumn = "routine_id", entityColumn = "routine_id") val checklist: List<ChecklistItem>,
    @Relation(parentColumn = "id", entityColumn = "occurrence_id") val ticks: List<ChecklistTick>,
    @Relation(
        parentColumn = "routine_id",
        entityColumn = "id",
        associateBy = Junction(RoutinePlace::class, parentColumn = "routine_id", entityColumn = "place_id"),
    )
    val places: List<Place>,
) {
    /** The names of the places it waits for, in alphabetical order; none unless it is open and waits. */
    fun waitingFor(): List<String> = when {
        occurrence.waiting && occurrence.status == OccurrenceStatus.Open -> places.map { it.name }.sortedWith(InputText.byName)
        else -> emptyList()
    }

    /** The checklist in its order, each item ticked off on this occurrence or with all of it ticked off at once. */
    fun entries(): List<ChecklistEntry> {
        val ticked = ticks.map { it.itemId }.toSet()
        return checklist.sortedBy { it.position }.map {
            ChecklistEntry(it.id, it.text, ticked = occurrence.status == OccurrenceStatus.Done || it.id in ticked)
        }
    }
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines")
    fun observeAll(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE status = :status")
    suspend fun withStatus(status: RoutineStatus): List<Routine>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun get(id: Long): Routine?

    @Insert
    suspend fun insert(routine: Routine): Long

    @Update
    suspend fun update(routine: Routine)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long)

    /** The routines [active] that have no occurrence on [date] yet. */
    @Query(
        """
        SELECT * FROM routines
        WHERE status = :active AND NOT EXISTS (SELECT 1 FROM occurrences WHERE routine_id = routines.id AND date = :date)
        """,
    )
    suspend fun unplannedOn(date: LocalDate, active: RoutineStatus = RoutineStatus.Active): List<Routine>

    @Query("UPDATE routines SET status = :status WHERE id = :id")
    suspend fun setRoutineStatus(id: Long, status: RoutineStatus)

    /** Makes the routine [active] again, planned from [from] on. */
    @Query("UPDATE routines SET status = :active, planned_from = :from WHERE id = :id")
    suspend fun reactivate(id: Long, from: Instant, active: RoutineStatus = RoutineStatus.Active)

    @Query("SELECT * FROM occurrences WHERE routine_id = :routineId AND status = :open")
    suspend fun openOccurrences(routineId: Long, open: OccurrenceStatus = OccurrenceStatus.Open): List<Occurrence>

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

    /** The occurrences from [from] to [to], each with its routine and checklist. */
    @Transaction
    @Query("SELECT * FROM occurrences WHERE date BETWEEN :from AND :to")
    fun observeOccurrencesWithChecklists(from: LocalDate, to: LocalDate): Flow<List<OccurrenceWithChecklist>>

    @Query("SELECT place_id FROM routine_places WHERE routine_id = :routineId")
    suspend fun placeIdsOf(routineId: Long): List<Long>

    @Query("DELETE FROM routine_places WHERE routine_id = :routineId")
    suspend fun unbindAll(routineId: Long)

    /** Binds the routine to those of [placeIds] that are still there: one deleted meanwhile is left out. */
    @Query("INSERT INTO routine_places (routine_id, place_id) SELECT :routineId, id FROM places WHERE id IN (:placeIds)")
    suspend fun bind(routineId: Long, placeIds: Collection<Long>)

    /** Whether an active routine is bound to a place. */
    @Query("SELECT EXISTS (SELECT 1 FROM routine_places JOIN routines ON routines.id = routine_id WHERE routines.status = :active)")
    suspend fun anyActiveBound(active: RoutineStatus = RoutineStatus.Active): Boolean

    @Query("UPDATE occurrences SET waiting = 0 WHERE routine_id = :routineId")
    suspend fun stopWaiting(routineId: Long)

    @Query("UPDATE occurrences SET waiting = :waiting WHERE id IN (:ids)")
    suspend fun setWaiting(ids: Collection<Long>, waiting: Boolean)

    /** The open occurrences that wait for their routine's places, each with its routine. */
    @Transaction
    @Query("SELECT * FROM occurrences WHERE status = :open AND waiting")
    suspend fun openWaiting(open: OccurrenceStatus = OccurrenceStatus.Open): List<OccurrenceOfRoutine>

    /** They pop up [at] the arrival at their place, and wait no more; their follow-ups count from then. */
    @Query("UPDATE occurrences SET waiting = 0, remind_at = :at WHERE id IN (:ids)")
    suspend fun popUpOnArrival(ids: Collection<Long>, at: Instant)

    @Query("SELECT tag_id FROM routine_tags WHERE routine_id = :routineId")
    suspend fun tagIdsOf(routineId: Long): List<Long>

    @Query("SELECT * FROM routine_tags")
    fun observeRoutineTags(): Flow<List<RoutineTag>>

    @Query("DELETE FROM routine_tags WHERE routine_id = :routineId")
    suspend fun untagAll(routineId: Long)

    /** Gives the routine those of [tagIds] that are still there: one deleted meanwhile is left out. */
    @Query("INSERT INTO routine_tags (routine_id, tag_id) SELECT :routineId, id FROM tags WHERE id IN (:tagIds)")
    suspend fun tag(routineId: Long, tagIds: Collection<Long>)

    @Query("SELECT * FROM checklist_items WHERE routine_id = :routineId ORDER BY position")
    suspend fun checklist(routineId: Long): List<ChecklistItem>

    @Insert
    suspend fun insertChecklistItems(items: List<ChecklistItem>)

    @Update
    suspend fun updateChecklistItems(items: List<ChecklistItem>)

    /** Takes the routine's checklist items away, but those [kept]; with them go their ticks. */
    @Query("DELETE FROM checklist_items WHERE routine_id = :routineId AND id NOT IN (:kept)")
    suspend fun deleteChecklistItemsBut(routineId: Long, kept: Collection<Long>)

    /** The checklist items ticked off on the occurrence. */
    @Query("SELECT item_id FROM checklist_ticks WHERE occurrence_id = :occurrenceId")
    suspend fun ticks(occurrenceId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTicks(ticks: List<ChecklistTick>)

    @Query("DELETE FROM checklist_ticks WHERE occurrence_id = :occurrenceId AND item_id = :itemId")
    suspend fun deleteTick(occurrenceId: Long, itemId: Long)

    @Query("DELETE FROM checklist_ticks WHERE occurrence_id = :occurrenceId")
    suspend fun deleteTicks(occurrenceId: Long)
}
