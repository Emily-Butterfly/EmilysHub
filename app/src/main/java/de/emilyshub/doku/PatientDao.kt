package de.emilyshub.doku

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/** The patients in `doku.db`. */
@Dao
abstract class PatientDao {
    @Insert
    abstract suspend fun insert(patient: Patient): Long

    @Update
    abstract suspend fun update(patient: Patient)

    @Query("SELECT * FROM patient WHERE id = :id")
    abstract suspend fun get(id: Long): Patient?

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
