package de.emilyshub.wishes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** The wishes in the database, with their tags; [Wishes] is the way to them. */
@Dao
interface WishDao {
    @Transaction
    @Query("SELECT * FROM wishes")
    fun observeAll(): Flow<List<WishWithTags>>

    @Query("SELECT * FROM wishes WHERE id = :id")
    suspend fun get(id: Long): Wish?

    @Insert
    suspend fun insert(wish: Wish): Long

    @Update
    suspend fun update(wish: Wish)

    @Query("DELETE FROM wishes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT tag_id FROM wish_tags WHERE wish_id = :wishId")
    suspend fun tagIdsOf(wishId: Long): List<Long>

    @Query("DELETE FROM wish_tags WHERE wish_id = :wishId")
    suspend fun untagAll(wishId: Long)

    /** Tags the wish with those of [tagIds] that are still there: one deleted meanwhile is left out. */
    @Query("INSERT INTO wish_tags (wish_id, tag_id) SELECT :wishId, id FROM tags WHERE id IN (:tagIds)")
    suspend fun tag(wishId: Long, tagIds: Collection<Long>)
}
