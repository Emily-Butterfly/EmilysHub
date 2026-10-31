package de.emilyshub.backup

import androidx.room.Dao
import androidx.room.Query
import de.emilyshub.ledger.Booking
import de.emilyshub.ledger.LedgerCategory
import de.emilyshub.ledger.RecurringBooking
import de.emilyshub.notes.GeneralTab
import de.emilyshub.notes.Note
import de.emilyshub.notes.NoteFolder
import de.emilyshub.notes.NoteGoal
import de.emilyshub.notes.NoteRoutine
import de.emilyshub.notes.NoteTab
import de.emilyshub.notes.NoteTag
import de.emilyshub.notes.NoteTodo
import de.emilyshub.places.Place
import de.emilyshub.places.PlaceWifi
import de.emilyshub.routines.ChecklistItem
import de.emilyshub.routines.ChecklistTick
import de.emilyshub.routines.Occurrence
import de.emilyshub.routines.Routine
import de.emilyshub.routines.RoutinePlace
import de.emilyshub.routines.RoutineTag
import de.emilyshub.tags.Tag
import de.emilyshub.todos.Todo
import de.emilyshub.todos.TodoChecklistItem
import de.emilyshub.todos.TodoPlace
import de.emilyshub.todos.TodoTag
import de.emilyshub.wishes.Wish
import de.emilyshub.wishes.WishTag

/**
 * Every row of every data table, read as the app reads them, through its type converters: whatever a backup brings
 * that the app could not read fails here, before the backup counts as fit to restore. Blocking, to run within the
 * transaction that puts the backup in place.
 */
@Dao
interface BackupDao {
    @Query("SELECT * FROM routines")
    fun routines(): List<Routine>

    @Query("SELECT * FROM occurrences")
    fun occurrences(): List<Occurrence>

    @Query("SELECT * FROM checklist_items")
    fun checklistItems(): List<ChecklistItem>

    @Query("SELECT * FROM checklist_ticks")
    fun checklistTicks(): List<ChecklistTick>

    @Query("SELECT * FROM tags")
    fun tags(): List<Tag>

    @Query("SELECT * FROM routine_tags")
    fun routineTags(): List<RoutineTag>

    @Query("SELECT * FROM places")
    fun places(): List<Place>

    @Query("SELECT * FROM place_wifis")
    fun placeWifis(): List<PlaceWifi>

    @Query("SELECT * FROM routine_places")
    fun routinePlaces(): List<RoutinePlace>

    @Query("SELECT * FROM todos")
    fun todos(): List<Todo>

    @Query("SELECT * FROM todo_checklist_items")
    fun todoChecklistItems(): List<TodoChecklistItem>

    @Query("SELECT * FROM todo_tags")
    fun todoTags(): List<TodoTag>

    @Query("SELECT * FROM todo_places")
    fun todoPlaces(): List<TodoPlace>

    @Query("SELECT * FROM wishes")
    fun wishes(): List<Wish>

    @Query("SELECT * FROM wish_tags")
    fun wishTags(): List<WishTag>

    @Query("SELECT * FROM note_tabs")
    fun noteTabs(): List<NoteTab>

    @Query("SELECT * FROM notes")
    fun notes(): List<Note>

    @Query("SELECT * FROM note_folders")
    fun noteFolders(): List<NoteFolder>

    @Query("SELECT * FROM note_general_tab")
    fun noteGeneralTab(): List<GeneralTab>

    @Query("SELECT * FROM note_goals")
    fun noteGoals(): List<NoteGoal>

    @Query("SELECT * FROM note_todos")
    fun noteTodos(): List<NoteTodo>

    @Query("SELECT * FROM note_routines")
    fun noteRoutines(): List<NoteRoutine>

    @Query("SELECT * FROM note_tags")
    fun noteTags(): List<NoteTag>

    @Query("SELECT * FROM ledger_categories")
    fun ledgerCategories(): List<LedgerCategory>

    @Query("SELECT * FROM bookings")
    fun bookings(): List<Booking>

    @Query("SELECT * FROM recurring_bookings")
    fun recurringBookings(): List<RecurringBooking>
}
