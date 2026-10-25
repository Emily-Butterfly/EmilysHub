package de.emilyshub.wishes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** The wishes in the database, with their tags; [Wishes] is the way to them. */
@Dao
interface WishDao {
    @Transaction
    @Query("SELECT * FROM wishes")
    fun observeAll(): Flow<List<WishWithTags>>

    @Query("SELECT * FROM wishes WHERE id = :id")
    suspend fun get(id: Long): Wish?

    @Query("SELECT * FROM wishes")
    suspend fun all(): List<Wish>

    /** [id], unless that wish was deleted meanwhile, or none. */
    @Query("SELECT id FROM wishes WHERE id = :id")
    suspend fun stillThere(id: Long?): Long?

    /** The to-dos hanging under goals, each with how much of its checklist is ticked off. */
    @Query(
        "SELECT t.id, t.title, t.goal_id, t.done_at IS NOT NULL AS done, " +
            "(SELECT COUNT(*) FROM todo_checklist_items c WHERE c.todo_id = t.id) AS items, " +
            "(SELECT COUNT(*) FROM todo_checklist_items c WHERE c.todo_id = t.id AND c.ticked) AS ticked " +
            "FROM todos t WHERE t.goal_id IS NOT NULL",
    )
    fun observeGoalTodos(): Flow<List<GoalTodo>>

    /** Whether each part of the goal is done: its subgoals, then its to-dos. */
    @Query(
        "SELECT status = :done FROM wishes WHERE parent_id = :goalId " +
            "UNION ALL SELECT done_at IS NOT NULL FROM todos WHERE goal_id = :goalId",
    )
    suspend fun partsDone(goalId: Long, done: WishStatus = WishStatus.Done): List<Boolean>

    @Query("UPDATE wishes SET status = :status, done_on = :doneOn WHERE id = :id")
    suspend fun setStatus(id: Long, status: WishStatus, doneOn: LocalDate?)

    @Insert
    suspend fun insert(wish: Wish): Long

    @Update
    suspend fun update(wish: Wish)

    @Query("DELETE FROM wishes WHERE id = :id")
    suspend fun delete(id: Long)

    /** Hangs the subgoals of the goal [from] under the goal [to]; none: at the top. */
    @Query("UPDATE wishes SET parent_id = :to WHERE parent_id = :from")
    suspend fun moveSubgoals(from: Long, to: Long?)

    /** Hangs the to-dos of the goal [from] under the goal [to]; none: at no goal. */
    @Query("UPDATE todos SET goal_id = :to WHERE goal_id = :from")
    suspend fun moveTodos(from: Long, to: Long?)

    @Query("SELECT tag_id FROM wish_tags WHERE wish_id = :wishId")
    suspend fun tagIdsOf(wishId: Long): List<Long>

    @Query("DELETE FROM wish_tags WHERE wish_id = :wishId")
    suspend fun untagAll(wishId: Long)

    /** Tags the wish with those of [tagIds] that are still there: one deleted meanwhile is left out. */
    @Query("INSERT INTO wish_tags (wish_id, tag_id) SELECT :wishId, id FROM tags WHERE id IN (:tagIds)")
    suspend fun tag(wishId: Long, tagIds: Collection<Long>)
}
