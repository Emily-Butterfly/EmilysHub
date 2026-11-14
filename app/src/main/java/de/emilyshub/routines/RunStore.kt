package de.emilyshub.routines

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.coroutines.CoroutineContext

/**
 * The run through the checklist of an occurrence (SPEC.md §2, Ablauf-Modus), as far as it is, kept so that it outlives
 * the app and a restart of the phone: the step [stepItemId] that shows, since [stepStartedAt], whether its time running
 * out has [alerted], the items "Fertig" ticked off in it ([tickedHere], "3,5,8" in their order) and when the last of
 * them did the routine ([finishedAt]). One at most; it goes with its occurrence.
 */
@Entity(
    tableName = "runs",
    foreignKeys = [
        ForeignKey(entity = Occurrence::class, parentColumns = ["id"], childColumns = ["occurrence_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class RunRow(
    @PrimaryKey @ColumnInfo(name = "occurrence_id") val occurrenceId: Long,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    @ColumnInfo(name = "step_item_id") val stepItemId: Long?,
    @ColumnInfo(name = "step_started_at") val stepStartedAt: Instant,
    @ColumnInfo(name = "ticked_here") val tickedHere: String = "",
    @ColumnInfo(name = "finished_at") val finishedAt: Instant? = null,
    val alerted: Boolean = false,
)

/** A kept run, with when its occurrence's run was first started ([startedAt]). */
data class RunWithStart(
    @Embedded val row: RunRow,
    @ColumnInfo(name = "started_at") val startedAt: Instant?,
)

@Dao
interface RunDao {
    @Query(RUNS)
    fun observeRuns(): Flow<List<RunWithStart>>

    @Query(RUNS)
    suspend fun runs(): List<RunWithStart>

    @Upsert
    suspend fun save(row: RunRow)

    @Query("DELETE FROM runs")
    suspend fun deleteAll()

    @Query("DELETE FROM runs WHERE occurrence_id = :occurrenceId")
    suspend fun delete(occurrenceId: Long)

    private companion object {
        const val RUNS = "SELECT runs.*, occurrences.run_started_at AS started_at FROM runs " +
            "JOIN occurrences ON occurrences.id = runs.occurrence_id"
    }
}

/**
 * Where the run is kept ([RunRow]), read and written only on [io], since the first call opens the [database]. Writes run
 * to their end once started, as the routines' do.
 */
class RunStore(private val database: () -> HubDatabase, private val io: CoroutineContext) {
    private val dao get() = database().runs()

    /** The run kept, as it changes; none while none is. */
    fun observe(): Flow<Run?> = flow { emitAll(dao.observeRuns().map { it.firstOrNull()?.toRun() }.distinctUntilChanged()) }.flowOn(io)

    /** The run kept; none while none is. */
    suspend fun current(): Run? = withContext(io) { dao.runs().firstOrNull()?.toRun() }

    /** Keeps [run] as the only one: any other goes. */
    suspend fun replace(run: Run) = withContext(io + NonCancellable) {
        database().withTransaction {
            dao.deleteAll()
            dao.save(run.toRow())
        }
    }

    /** Keeps [run] as it is now. */
    suspend fun save(run: Run) = withContext(io + NonCancellable) { dao.save(run.toRow()) }

    /** No run is kept any more. */
    suspend fun clear() = withContext(io + NonCancellable) { dao.deleteAll() }

    private fun RunWithStart.toRun() = Run(
        occurrenceId = row.occurrenceId,
        routineId = row.routineId,
        startedAt = startedAt ?: row.stepStartedAt,
        tickedHere = row.tickedHere.split(",").filter { it.isNotEmpty() }.map(String::toLong),
        finishedAt = row.finishedAt,
        stepItemId = row.stepItemId,
        stepStartedAt = row.stepStartedAt,
        alerted = row.alerted,
    )

    private fun Run.toRow() = RunRow(occurrenceId, routineId, stepItemId, stepStartedAt, tickedHere.joinToString(","), finishedAt, alerted)
}
