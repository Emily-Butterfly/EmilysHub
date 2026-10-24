package de.emilyshub.tags

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tags")
    suspend fun all(): List<Tag>

    @Insert
    suspend fun insert(tag: Tag): Long

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)
}
