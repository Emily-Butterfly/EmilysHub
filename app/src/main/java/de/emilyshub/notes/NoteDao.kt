package de.emilyshub.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.wishes.WishStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** The notes, their tabs and folders in the database; [Notes] is the way to them. */
@Dao
interface NoteDao {
    @Query("SELECT * FROM note_tabs ORDER BY position, id")
    suspend fun tabs(): List<NoteTab>

    @Insert
    suspend fun insertTab(tab: NoteTab): Long

    @Query("UPDATE note_tabs SET name = :name WHERE id = :id")
    suspend fun renameTab(id: Long, name: String)

    @Query("UPDATE note_tabs SET position = :position WHERE id = :id")
    suspend fun placeTab(id: Long, position: Int)

    @Query("DELETE FROM note_tabs WHERE id = :id")
    suspend fun deleteTab(id: Long)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notes WHERE tab_id = :tabId")
    suspend fun deleteNotesInTab(tabId: Long)

    @Query("UPDATE note_tabs SET sort = :sort WHERE id = :id")
    suspend fun sortTab(id: Long, sort: NoteSort)

    @Query("SELECT * FROM note_general_tab WHERE id = ${GeneralTab.ID}")
    suspend fun generalTab(): GeneralTab?

    @Upsert
    suspend fun putGeneralTab(tab: GeneralTab)

    @Query("SELECT * FROM notes")
    suspend fun notes(): List<Note>

    @Query("UPDATE notes SET position = :position WHERE id = :id")
    suspend fun placeNote(id: Long, position: Int)

    @Query("UPDATE notes SET tab_id = :tabId, folder_id = :folderId, position = :position WHERE id = :id")
    suspend fun moveNote(id: Long, tabId: Long?, folderId: Long?, position: Int)

    @Query("SELECT * FROM note_folders")
    suspend fun folders(): List<NoteFolder>

    @Insert
    suspend fun insertFolder(folder: NoteFolder): Long

    @Query("UPDATE note_folders SET name = :name, changed_at = :changedAt WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String, changedAt: Instant)

    @Query("UPDATE note_folders SET sort = :sort WHERE id = :id")
    suspend fun sortFolder(id: Long, sort: NoteSort)

    @Query("UPDATE note_folders SET position = :position WHERE id = :id")
    suspend fun placeFolder(id: Long, position: Int)

    @Query("UPDATE note_folders SET tab_id = :tabId, parent_id = :parentId, position = :position WHERE id = :id")
    suspend fun moveFolder(id: Long, tabId: Long?, parentId: Long?, position: Int)

    /** Puts the folders [ids] in the tab [tabId], each still in the folder it was in. */
    @Query("UPDATE note_folders SET tab_id = :tabId WHERE id IN (:ids)")
    suspend fun putFoldersInTab(ids: List<Long>, tabId: Long?)

    /** Puts the notes in the folders [folderIds] in the tab [tabId], each still in its folder. */
    @Query("UPDATE notes SET tab_id = :tabId WHERE folder_id IN (:folderIds)")
    suspend fun putNotesInTab(folderIds: List<Long>, tabId: Long?)

    @Query("DELETE FROM notes WHERE folder_id IN (:folderIds)")
    suspend fun deleteNotesIn(folderIds: List<Long>)

    /** Takes the folders in the folders [ids] out of them, as all are deleted next: so that the order they go in does not matter. */
    @Query("UPDATE note_folders SET parent_id = NULL WHERE parent_id IN (:ids)")
    suspend fun detachChildrenOf(ids: List<Long>)

    @Query("DELETE FROM note_folders WHERE id IN (:ids)")
    suspend fun deleteFolders(ids: List<Long>)

    /** Deletes all folders of the tab, of which none is in another tab, at once. */
    @Query("DELETE FROM note_folders WHERE tab_id = :tabId")
    suspend fun deleteFoldersInTab(tabId: Long)

    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun pinNote(id: Long, pinned: Boolean)

    @Query("UPDATE note_folders SET pinned = :pinned WHERE id = :id")
    suspend fun pinFolder(id: Long, pinned: Boolean)

    @Query("UPDATE notes SET on_today = :pinned WHERE id = :id")
    suspend fun pinOnToday(id: Long, pinned: Boolean)

    @Query("SELECT * FROM notes WHERE on_today ORDER BY changed_at DESC, id")
    fun observeOnToday(): Flow<List<Note>>

    @Query("SELECT goal_id FROM note_goals WHERE note_id = :noteId")
    suspend fun goalIdsOf(noteId: Long): List<Long>

    @Query("SELECT todo_id FROM note_todos WHERE note_id = :noteId")
    suspend fun todoIdsOf(noteId: Long): List<Long>

    @Query("SELECT note_id FROM note_todos WHERE todo_id = :todoId")
    suspend fun noteIdsOfTodo(todoId: Long): List<Long>

    /** The to-do that came again after the to-do [todoId], if any. */
    @Query("SELECT next_id FROM todos WHERE id = :todoId")
    suspend fun nextOf(todoId: Long): Long?

    /** Links the notes linked to the to-do [from] to the to-do [to] too, as one coming again does to its next. */
    @Query("INSERT OR IGNORE INTO note_todos (note_id, todo_id) SELECT note_id, :to FROM note_todos WHERE todo_id = :from")
    suspend fun copyTodoLinks(from: Long, to: Long)

    @Query("SELECT routine_id FROM note_routines WHERE note_id = :noteId")
    suspend fun routineIdsOf(noteId: Long): List<Long>

    @Query("DELETE FROM note_goals WHERE note_id = :noteId")
    suspend fun unlinkGoals(noteId: Long)

    @Query("DELETE FROM note_todos WHERE note_id = :noteId")
    suspend fun unlinkTodos(noteId: Long)

    @Query("DELETE FROM note_routines WHERE note_id = :noteId")
    suspend fun unlinkRoutines(noteId: Long)

    /** Links the note to those goals of [ids] that are still there: one deleted meanwhile is left out. */
    @Query("INSERT INTO note_goals (note_id, goal_id) SELECT :noteId, id FROM wishes WHERE id IN (:ids)")
    suspend fun linkGoals(noteId: Long, ids: Collection<Long>)

    /** Links the note to those to-dos of [ids] that are still there: one deleted meanwhile is left out. */
    @Query("INSERT INTO note_todos (note_id, todo_id) SELECT :noteId, id FROM todos WHERE id IN (:ids)")
    suspend fun linkTodos(noteId: Long, ids: Collection<Long>)

    /** Links the note to those routines of [ids] that are still there: one deleted meanwhile is left out. */
    @Query("INSERT INTO note_routines (note_id, routine_id) SELECT :noteId, id FROM routines WHERE id IN (:ids)")
    suspend fun linkRoutines(noteId: Long, ids: Collection<Long>)

    @Query("SELECT id, title AS name, status != :done AS open FROM wishes")
    fun observeGoalTargets(done: WishStatus = WishStatus.Done): Flow<List<LinkTarget>>

    /** The to-dos as they are now: of one coming again, the last, which those before it hand their links on to. */
    @Query("SELECT id, title AS name, done_at IS NULL AS open FROM todos WHERE next_id IS NULL")
    fun observeTodoTargets(): Flow<List<LinkTarget>>

    @Query("SELECT id, name, status != :archived AS open FROM routines")
    fun observeRoutineTargets(archived: RoutineStatus = RoutineStatus.Archived): Flow<List<LinkTarget>>

    @Query("SELECT n.* FROM notes n JOIN note_goals l ON l.note_id = n.id WHERE l.goal_id = :goalId")
    fun observeNotesOfGoal(goalId: Long): Flow<List<Note>>

    @Query("SELECT n.* FROM notes n JOIN note_todos l ON l.note_id = n.id WHERE l.todo_id = :todoId")
    fun observeNotesOfTodo(todoId: Long): Flow<List<Note>>

    @Query("SELECT n.* FROM notes n JOIN note_routines l ON l.note_id = n.id WHERE l.routine_id = :routineId")
    fun observeNotesOfRoutine(routineId: Long): Flow<List<Note>>
}
