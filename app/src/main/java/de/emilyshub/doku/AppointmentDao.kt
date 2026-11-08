package de.emilyshub.doku

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.MapColumn
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import java.time.LocalDate

/** The appointments in `doku.db`. */
@Dao
abstract class AppointmentDao {
    /** Adds [appointment]; its new id. */
    @Insert
    abstract suspend fun insert(appointment: Appointment): Long

    /** Writes [appointment] over the one with its id. */
    @Update
    abstract suspend fun update(appointment: Appointment)

    /** Appointment [id], if there is one. */
    @Query("SELECT * FROM appointment WHERE id = :id")
    abstract suspend fun get(id: Long): Appointment?

    /** The appointments of patient [patientId], in no order. */
    @Query("SELECT * FROM appointment WHERE patient_id = :patientId")
    abstract suspend fun of(patientId: Long): List<Appointment>

    /** All appointments: few enough to search here. */
    @Query("SELECT * FROM appointment")
    abstract suspend fun all(): List<Appointment>

    /** Each patient's last day with an appointment up to [today], by the patient's id; none for those without. */
    @Query("SELECT patient_id, MAX(date) AS last_seen FROM appointment WHERE date <= :today GROUP BY patient_id")
    abstract suspend fun lastSeen(today: LocalDate): Map<@MapColumn("patient_id") Long, @MapColumn("last_seen") LocalDate>

    /** Deletes appointment [id] with its measurements, or nothing. */
    @Transaction
    open suspend fun delete(id: Long) {
        deleteMeasuredValues(id)
        deleteMeasurements(id)
        deleteOne(id)
    }

    @Query("DELETE FROM measurement_value WHERE measurement_id IN (SELECT id FROM measurement WHERE appointment_id = :id)")
    protected abstract suspend fun deleteMeasuredValues(id: Long)

    @Query("DELETE FROM measurement WHERE appointment_id = :id")
    protected abstract suspend fun deleteMeasurements(id: Long)

    @Query("DELETE FROM appointment WHERE id = :id")
    protected abstract suspend fun deleteOne(id: Long)
}
