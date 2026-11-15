package de.emilyshub.tracking

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.time.LocalDate
import java.time.LocalTime

/**
 * A medication as typed in (SPEC.md §17, "Medikamente"), with no list to choose from: its [name], how it is taken
 * ([form]), at which [times], what for ([purpose]), whether the package leaflet says it raises the risk of thrombosis
 * and whether it lowers the pulse, and the side effects watched for. Its timeline: taken from [start], up to [end] if
 * it was stopped, its doses changed between ([DoseChange]).
 */
@Entity(tableName = "medication")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val form: String = "",
    val times: List<LocalTime> = emptyList(),
    val purpose: String = "",
    @ColumnInfo(name = "thrombosis_risk") val thrombosisRisk: Boolean = false,
    @ColumnInfo(name = "lowers_pulse") val lowersPulse: Boolean = false,
    @ColumnInfo(name = "side_effects") val sideEffects: String = "",
    val start: LocalDate,
    val end: LocalDate? = null,
) {
    /** Whether it is taken on [day]: from its start up to its end, both included. */
    fun isActiveOn(day: LocalDate): Boolean = !day.isBefore(start) && (end == null || !day.isAfter(end))
}

/** A change of [medicationId]'s dose on [date], from [from] to [to], both as typed in. */
@Entity(
    tableName = "dose_change",
    foreignKeys = [ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medication_id")],
)
data class DoseChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medication_id") val medicationId: Long,
    val date: LocalDate,
    @ColumnInfo(name = "dose_before") val from: String,
    @ColumnInfo(name = "dose_after") val to: String,
)

/** How an intake went otherwise than planned. */
enum class Deviation(val stored: String) {
    /** At another time, [IntakeDeviation.time]. */
    OtherTime("other_time"),

    /** Not at all. */
    Forgotten("forgotten"),
}

/**
 * How [medicationId] was taken otherwise than planned on [date], as the evening entry tells; with an evening entry and
 * no such row, it was taken as planned.
 */
@Entity(
    tableName = "intake_deviation",
    primaryKeys = ["date", "medication_id"],
    foreignKeys = [
        ForeignKey(entity = EveningEntry::class, parentColumns = ["date"], childColumns = ["date"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("medication_id")],
)
data class IntakeDeviation(
    val date: LocalDate,
    @ColumnInfo(name = "medication_id") val medicationId: Long,
    val deviation: Deviation,
    val time: LocalTime? = null,
)

/** The medications and their dose changes. */
@Dao
interface MedicationDao {
    /** All medications, by name. */
    @Query("SELECT * FROM medication ORDER BY name COLLATE NOCASE, id")
    suspend fun all(): List<Medication>

    /** Medication [id], if any. */
    @Query("SELECT * FROM medication WHERE id = :id")
    suspend fun get(id: Long): Medication?

    /** Adds [medication]: its id. */
    @Insert
    suspend fun insert(medication: Medication): Long

    /** Writes [medication] in place of the one of its id. */
    @Update
    suspend fun update(medication: Medication)

    /** The dose changes of [medicationId], in the order of their dates. */
    @Query("SELECT * FROM dose_change WHERE medication_id = :medicationId ORDER BY date, id")
    suspend fun doseChanges(medicationId: Long): List<DoseChange>

    /** Adds [change]: its id. */
    @Insert
    suspend fun insertDoseChange(change: DoseChange): Long

    /** Takes [change] off its timeline. */
    @Delete
    suspend fun deleteDoseChange(change: DoseChange)
}
