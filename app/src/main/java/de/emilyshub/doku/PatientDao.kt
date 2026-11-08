package de.emilyshub.doku

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/** The patients in `doku.db`. */
@Dao
abstract class PatientDao {
    /** Adds [patient]; their new id. */
    @Insert
    abstract suspend fun insert(patient: Patient): Long

    /** Writes [patient] over the one with their id. */
    @Update
    abstract suspend fun update(patient: Patient)

    /** Patient [id], if there is one. */
    @Query("SELECT * FROM patient WHERE id = :id")
    abstract suspend fun get(id: Long): Patient?

    /** All patients, active and closed: few enough to sort and search here. */
    @Query("SELECT * FROM patient")
    abstract suspend fun all(): List<Patient>

    @Query("DELETE FROM patient WHERE id = :id")
    protected abstract suspend fun deleteOne(id: Long)

    /** What patient [id]'s measurements found: deleted as the foreign keys would, but not only if they are on. */
    @Query(
        "DELETE FROM measurement_value WHERE measurement_id IN (SELECT m.id FROM measurement m " +
            "JOIN appointment a ON a.id = m.appointment_id WHERE a.patient_id = :id)",
    )
    protected abstract suspend fun deleteMeasuredValues(id: Long)

    /** The measurements at patient [id]'s appointments. */
    @Query("DELETE FROM measurement WHERE appointment_id IN (SELECT id FROM appointment WHERE patient_id = :id)")
    protected abstract suspend fun deleteMeasurements(id: Long)

    /** Patient [id]'s appointments. */
    @Query("DELETE FROM appointment WHERE patient_id = :id")
    protected abstract suspend fun deleteAppointments(id: Long)

    /** Deletes each of [ids] with all that belongs to them, their appointments and measurements, or none of them. */
    @Transaction
    open suspend fun delete(ids: Collection<Long>) {
        ids.forEach {
            deleteMeasuredValues(it)
            deleteMeasurements(it)
            deleteAppointments(it)
            deleteOne(it)
        }
    }
}
