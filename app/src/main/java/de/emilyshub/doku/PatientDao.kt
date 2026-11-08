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

    /** Deletes each of [ids] with all that belongs to them, or none of them. Their appointments and measurements follow (#60, #62). */
    @Transaction
    open suspend fun delete(ids: Collection<Long>) {
        ids.forEach { deleteOne(it) }
    }
}
