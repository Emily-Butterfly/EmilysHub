package de.emilyshub.doku

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.LocalDate
import java.time.LocalTime

/** The measurements in `doku.db`, each with what it found for the values of its test. */
@Dao
abstract class MeasurementDao {
    /** Measurement [id], if there is one. */
    @Query("SELECT * FROM measurement WHERE id = :id")
    abstract suspend fun get(id: Long): Measurement?

    /** The measurements at appointment [appointmentId], in the order they were added. */
    @Query("SELECT * FROM measurement WHERE appointment_id = :appointmentId ORDER BY id")
    abstract suspend fun at(appointmentId: Long): List<Measurement>

    /** What measurement [measurementId] found. */
    @Query("SELECT * FROM measurement_value WHERE measurement_id = :measurementId")
    abstract suspend fun values(measurementId: Long): List<MeasurementValue>

    /** How many measurements appointment [appointmentId] has. */
    @Query("SELECT COUNT(*) FROM measurement WHERE appointment_id = :appointmentId")
    abstract suspend fun countAt(appointmentId: Long): Int

    /** The measurements of patient [patientId], each with the day of its appointment, in no order. */
    @Query(
        "SELECT m.*, a.date AS date, a.time AS time FROM measurement m JOIN appointment a ON a.id = m.appointment_id " +
            "WHERE a.patient_id = :patientId",
    )
    abstract suspend fun ofPatient(patientId: Long): List<DatedMeasurement>

    /** What the measurements of patient [patientId] found. */
    @Query(
        "SELECT mv.* FROM measurement_value mv JOIN measurement m ON m.id = mv.measurement_id " +
            "JOIN appointment a ON a.id = m.appointment_id WHERE a.patient_id = :patientId",
    )
    abstract suspend fun valuesOfPatient(patientId: Long): List<MeasurementValue>

    /** Every measurement with a remark, with its appointment's patient and day, for the Doku's search. */
    @Query(
        "SELECT m.*, a.date AS date, a.time AS time FROM measurement m JOIN appointment a ON a.id = m.appointment_id " +
            "WHERE m.remark != ''",
    )
    abstract suspend fun remarked(): List<DatedMeasurement>

    /** Writes [measurement], adding it if it has no id, with [values] in place of what it found; its id. */
    @Transaction
    open suspend fun save(measurement: Measurement, values: List<MeasurementValue>): Long {
        val id = if (measurement.id == 0L) insert(measurement) else measurement.id.also { update(measurement) }
        deleteValues(id)
        values.forEach { insertValue(it.copy(measurementId = id)) }
        return id
    }

    /** Deletes measurement [id] with what it found. */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteValues(id)
        deleteOne(id)
    }

    @Insert
    protected abstract suspend fun insert(measurement: Measurement): Long

    @Update
    protected abstract suspend fun update(measurement: Measurement)

    @Insert
    protected abstract suspend fun insertValue(value: MeasurementValue)

    @Query("DELETE FROM measurement_value WHERE measurement_id = :id")
    protected abstract suspend fun deleteValues(id: Long)

    @Query("DELETE FROM measurement WHERE id = :id")
    protected abstract suspend fun deleteOne(id: Long)
}

/** A measurement with the [date] of its appointment, and its time, if any, to order those of a day. */
data class DatedMeasurement(@Embedded val measurement: Measurement, val date: LocalDate, val time: LocalTime?)
