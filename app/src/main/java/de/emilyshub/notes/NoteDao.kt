package de.emilyshub.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
    suspend fun deleteInTab(tabId: Long)

    @Query("UPDATE note_tabs SET sort = :sort WHERE id = :id")
    suspend fun sortTab(id: Long, sort: NoteSort)

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

    /** Takes the folders [ids], all deleted next, out of each other first, so that the order they go in does not matter. */
    @Query("UPDATE note_folders SET parent_id = NULL WHERE parent_id IN (:ids)")
    suspend fun takeOutOf(ids: List<Long>)

    @Query("DELETE FROM note_folders WHERE id IN (:ids)")
    suspend fun deleteFolders(ids: List<Long>)

    /** Deletes all folders of the tab, of which none is in another tab, at once. */
    @Query("DELETE FROM note_folders WHERE tab_id = :tabId")
    suspend fun deleteFoldersInTab(tabId: Long)
}
