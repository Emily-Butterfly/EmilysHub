package de.emilyshub.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** The notes and their tabs in the database; [Notes] is the way to them. */
@Dao
interface NoteDao {
    @Query("SELECT * FROM note_tabs ORDER BY position, id")
    fun observeTabs(): Flow<List<NoteTab>>

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

    /** [id], unless that tab was deleted meanwhile, or none. */
    @Query("SELECT id FROM note_tabs WHERE id = :id")
    suspend fun tabStillThere(id: Long?): Long?

    @Query("SELECT * FROM notes")
    fun observeNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM notes WHERE tab_id = :tabId")
    suspend fun deleteInTab(tabId: Long)
}
