package de.emilyshub.places

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Transaction
    @Query("SELECT * FROM places")
    fun observeAll(): Flow<List<PlaceWithWifis>>

    @Transaction
    @Query("SELECT * FROM places")
    suspend fun all(): List<PlaceWithWifis>

    @Insert
    suspend fun insert(place: Place): Long

    @Update
    suspend fun update(place: Place)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM place_wifis WHERE place_id = :placeId")
    suspend fun deleteWifisOf(placeId: Long)

    @Insert
    suspend fun insertWifis(wifis: List<PlaceWifi>)
}
