package de.emilyshub.tracking

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.time.Instant

/** The two ways of a Stehtest at home (SPEC.md §17, "Stehtest"), with the [minutes] after standing up it measures at. */
enum class StandVariant(val stored: String, val minutes: List<Int>, val sources: List<Source>) {
    /** Standing freely, measured after 1, 3, 5, 8 and 10 minutes. [CMAJ-POTS-2022] [DGN-SYNK-2020] */
    Standing("standing", listOf(1, 3, 5, 8, 10), listOf(Source.CmajPots2022, Source.DgnSyncope2020)),

    /** The NASA Lean Test: shoulder blades against the wall, heels some 15–20 cm before it, measured each minute. [LEE-2020] */
    Leaning("leaning", (1..10).toList(), listOf(Source.Lee2020)),
    ;

    /** The first of its [minutes] not in [done], the measured and the skipped; none once all are. */
    fun nextMinute(done: Set<Int>): Int? = minutes.firstOrNull { it !in done }
}

/** Why a Stehtest ended before its last measurement. */
enum class AbortCause(val stored: String) {
    /** "Abbrechen – hinlegen" was pressed, with a reason if one was given. */
    Pressed("pressed"),

    /** Tracking locked meanwhile: the app went to the background or the screen went off. */
    Locked("locked"),
}

/**
 * A Stehtest (SPEC.md §17): of [variant], lying down from [startedAt], standing up at [standingAt] once the lying
 * values are in; [abortCause] says it ended early, at [abortedMinute] after standing up (none if still lying), with
 * [abortReason] as typed in; [finished] once the last point was measured. Before it began: how many coffees
 * ([caffeine]), how long since the last meal ([mealMinutesBefore]), how much was drunk today ([fluidDecilitres]). What
 * was not filled in is null, "nicht erfasst".
 */
@Entity(tableName = "stand_test")
data class StandTest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val variant: StandVariant,
    @ColumnInfo(name = "started_at") val startedAt: Instant,
    @ColumnInfo(name = "standing_at") val standingAt: Instant? = null,
    @ColumnInfo(name = "lying_pulse") val lyingPulse: Int? = null,
    @ColumnInfo(name = "lying_systolic") val lyingSystolic: Int? = null,
    @ColumnInfo(name = "lying_diastolic") val lyingDiastolic: Int? = null,
    val caffeine: Int? = null,
    @ColumnInfo(name = "meal_minutes_before") val mealMinutesBefore: Int? = null,
    @ColumnInfo(name = "fluid_decilitres") val fluidDecilitres: Int? = null,
    @ColumnInfo(name = "abort_cause") val abortCause: AbortCause? = null,
    @ColumnInfo(name = "aborted_minute") val abortedMinute: Int? = null,
    @ColumnInfo(name = "abort_reason") val abortReason: String = "",
    val finished: Boolean = false,
) {
    /** Whether it still runs: neither finished nor aborted. */
    val running: Boolean get() = !finished && abortCause == null
}

/**
 * A measurement of Stehtest [testId] at [minute] after standing up: the pulse, the blood pressure (optional), dizziness,
 * palpitations and breathlessness 0–3, and whether the legs discoloured. A point skipped has a row with nothing in it.
 */
@Entity(
    tableName = "stand_point",
    primaryKeys = ["test_id", "minute"],
    foreignKeys = [ForeignKey(entity = StandTest::class, parentColumns = ["id"], childColumns = ["test_id"], onDelete = ForeignKey.CASCADE)],
)
data class StandPoint(
    @ColumnInfo(name = "test_id") val testId: Long,
    val minute: Int,
    val pulse: Int? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val dizziness: Int? = null,
    val palpitations: Int? = null,
    val breathlessness: Int? = null,
    @ColumnInfo(name = "legs_discoloured") val legsDiscoloured: Boolean? = null,
)

/** How long before Stehtest [testId] began medication [medicationId] was last taken today, as entered. */
@Entity(
    tableName = "stand_intake",
    primaryKeys = ["test_id", "medication_id"],
    foreignKeys = [
        ForeignKey(entity = StandTest::class, parentColumns = ["id"], childColumns = ["test_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("medication_id")],
)
data class StandIntake(
    @ColumnInfo(name = "test_id") val testId: Long,
    @ColumnInfo(name = "medication_id") val medicationId: Long,
    @ColumnInfo(name = "minutes_before") val minutesBefore: Int,
)

/** The Stehtests, their points and the intakes before them. */
@Dao
interface StandTestDao {
    /** All Stehtests, the latest first. */
    @Query("SELECT * FROM stand_test ORDER BY id DESC")
    suspend fun all(): List<StandTest>

    /** Stehtest [id], if any. */
    @Query("SELECT * FROM stand_test WHERE id = :id")
    suspend fun get(id: Long): StandTest?

    /** Adds [test]: its id. */
    @Insert
    suspend fun insert(test: StandTest): Long

    /** Writes [test] in place of the one of its id. */
    @Update
    suspend fun update(test: StandTest)

    /** The points of Stehtest [testId], by minute. */
    @Query("SELECT * FROM stand_point WHERE test_id = :testId ORDER BY minute")
    suspend fun points(testId: Long): List<StandPoint>

    /** Writes [point], in place of the one of its minute. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPoint(point: StandPoint)

    /** Adds [intakes]. */
    @Insert
    suspend fun insertIntakes(intakes: List<StandIntake>)

    /** The intakes before Stehtest [testId]. */
    @Query("SELECT * FROM stand_intake WHERE test_id = :testId")
    suspend fun intakes(testId: Long): List<StandIntake>
}

/** What is to be ticked before a Stehtest begins (SPEC.md §17); without all of it, it does not. */
enum class ChecklistItem {
    /** A second person is there. */
    SecondPerson,

    /** Beside a bed or a sofa. */
    BesideBed,

    /** No warning signs today. */
    NoWarningSigns,

    /** Not right after a hot shower, alcohol or a large meal. */
    NotAfterHeat,

    /** The medications taken as usual. */
    MedicationsAsUsual,
}

/** The checklist before a Stehtest. */
object StandChecklist {
    /** Whether all of it is [ticked]. */
    fun complete(ticked: Set<ChecklistItem>): Boolean = ticked.containsAll(ChecklistItem.entries)
}
