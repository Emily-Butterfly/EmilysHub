package de.emilyshub.warum

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReasonDao {
    /** The board's order: by position, and of two at the same, the one made first. */
    @Query("SELECT * FROM reasons ORDER BY position, id")
    fun observeAll(): Flow<List<Reason>>

    @Query("SELECT * FROM reasons ORDER BY position, id")
    suspend fun all(): List<Reason>

    @Query("SELECT * FROM reasons WHERE id = :id")
    suspend fun find(id: Long): Reason?

    /** How many reasons have a photo, but the reason [except]. */
    @Query("SELECT COUNT(*) FROM reasons WHERE photo IS NOT NULL AND id IS NOT :except")
    suspend fun photoCount(except: Long?): Int

    @Query("SELECT MIN(position) FROM reasons")
    suspend fun firstPosition(): Int?

    @Insert
    suspend fun insert(reason: Reason): Long

    @Update
    suspend fun update(reason: Reason)

    @Query("UPDATE reasons SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("DELETE FROM reasons WHERE id = :id")
    suspend fun delete(id: Long)
}
