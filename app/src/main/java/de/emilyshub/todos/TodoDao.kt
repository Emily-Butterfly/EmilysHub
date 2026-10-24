package de.emilyshub.todos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The to-dos in the database, with their checklists, tags and places; [Todos] is the way to them. */
@Dao
interface TodoDao {
    @Transaction
    @Query("SELECT * FROM todos")
    fun observeAll(): Flow<List<TodoWithDetails>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun get(id: Long): Todo?

    /** The open to-dos, each with its checklist, tags and places. */
    @Transaction
    @Query("SELECT * FROM todos WHERE done_at IS NULL")
    suspend fun open(): List<TodoWithDetails>

    /** The open to-dos waiting for their places, each with them. */
    @Transaction
    @Query("SELECT * FROM todos WHERE done_at IS NULL AND waiting")
    suspend fun openWaiting(): List<TodoWithDetails>

    @Transaction
    @Query("SELECT * FROM todos WHERE id IN (:ids)")
    suspend fun withDetails(ids: Collection<Long>): List<TodoWithDetails>

    /** Whether an open to-do waits for its places, so that arriving at one matters. */
    @Query("SELECT EXISTS (SELECT 1 FROM todos WHERE done_at IS NULL AND waiting)")
    suspend fun anyOpenWaiting(): Boolean

    @Insert
    suspend fun insert(todo: Todo): Long

    @Update
    suspend fun update(todo: Todo)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE todos SET done_at = :doneAt WHERE id = :id")
    suspend fun setDoneAt(id: Long, doneAt: Instant?)

    /** Sets its reminder anew, as it is saved: not put off or popped up, [waiting] for its places if it has any. */
    @Query("UPDATE todos SET reminds_at_due_time = :remindsAtDueTime, remind_at = NULL, waiting = :waiting WHERE id = :id")
    suspend fun rearm(id: Long, remindsAtDueTime: Boolean, waiting: Boolean)

    @Query("UPDATE todos SET remind_at = :until WHERE id = :id")
    suspend fun putOff(id: Long, until: Instant)

    /** They wait for their places no more, as they popped up there or are bound to none any more. */
    @Query("UPDATE todos SET waiting = 0 WHERE id IN (:ids)")
    suspend fun stopWaiting(ids: Collection<Long>)

    /** They pop up [at] then, on arrival or bound to no place any more, and wait no more. */
    @Query("UPDATE todos SET waiting = 0, remind_at = :at WHERE id IN (:ids)")
    suspend fun popUp(ids: Collection<Long>, at: Instant)

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

    @Query("SELECT place_id FROM todo_places WHERE todo_id = :todoId")
    suspend fun placeIdsOf(todoId: Long): List<Long>

    @Query("DELETE FROM todo_places WHERE todo_id = :todoId")
    suspend fun unbindAll(todoId: Long)

    /** Binds the to-do to those of [placeIds] that are still there: one deleted meanwhile is left out. */
    @Query("INSERT INTO todo_places (todo_id, place_id) SELECT :todoId, id FROM places WHERE id IN (:placeIds)")
    suspend fun bind(todoId: Long, placeIds: Collection<Long>)
}
