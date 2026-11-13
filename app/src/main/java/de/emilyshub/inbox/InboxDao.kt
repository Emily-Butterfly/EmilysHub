package de.emilyshub.inbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InboxDao {
    /** The newest first; of two captured at once, the one captured last. */
    @Query("SELECT * FROM inbox ORDER BY captured_at DESC, id DESC")
    fun observeAll(): Flow<List<InboxEntry>>

    @Query("SELECT COUNT(*) FROM inbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM inbox WHERE id = :id")
    suspend fun find(id: Long): InboxEntry?

    @Insert
    suspend fun insert(entry: InboxEntry): Long

    @Query("UPDATE inbox SET text = :text WHERE id = :id")
    suspend fun changeText(id: Long, text: String)

    @Query("DELETE FROM inbox WHERE id = :id")
    suspend fun delete(id: Long)
}
