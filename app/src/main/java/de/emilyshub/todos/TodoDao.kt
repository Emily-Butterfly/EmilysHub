package de.emilyshub.todos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The to-dos in the database, with their checklists and tags; [Todos] is the way to them. */
@Dao
interface TodoDao {
    @Transaction
    @Query("SELECT * FROM todos")
    fun observeAll(): Flow<List<TodoWithDetails>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: Long): Todo?

    @Insert
    suspend fun insert(todo: Todo): Long

    @Update
    suspend fun update(todo: Todo)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE todos SET done_at = :doneAt WHERE id = :id")
    suspend fun setDoneAt(id: Long, doneAt: Instant?)

    @Query("SELECT * FROM todo_checklist_items WHERE todo_id = :todoId ORDER BY position")
    suspend fun checklist(todoId: Long): List<TodoChecklistItem>

    /** Deletes the items of the to-do's checklist, but those [kept]. */
    @Query("DELETE FROM todo_checklist_items WHERE todo_id = :todoId AND id NOT IN (:kept)")
    suspend fun deleteChecklistItemsBut(todoId: Long, kept: Collection<Long>)

    @Update
    suspend fun updateChecklistItems(items: List<TodoChecklistItem>)

    @Insert
    suspend fun insertChecklistItems(items: List<TodoChecklistItem>)

    @Query("UPDATE todo_checklist_items SET ticked = :ticked WHERE id = :itemId")
    suspend fun setTicked(itemId: Long, ticked: Boolean)

    @Query("UPDATE todo_checklist_items SET ticked = :ticked WHERE todo_id = :todoId")
    suspend fun setAllTicked(todoId: Long, ticked: Boolean)

    @Query("SELECT tag_id FROM todo_tags WHERE todo_id = :todoId")
    suspend fun tagIdsOf(todoId: Long): List<Long>

    @Query("DELETE FROM todo_tags WHERE todo_id = :todoId")
    suspend fun untagAll(todoId: Long)

    /** Tags the to-do with those of [tagIds] that are still there: one deleted meanwhile is left out. */
    @Query("INSERT INTO todo_tags (todo_id, tag_id) SELECT :todoId, id FROM tags WHERE id IN (:tagIds)")
    suspend fun tag(todoId: Long, tagIds: Collection<Long>)
}
