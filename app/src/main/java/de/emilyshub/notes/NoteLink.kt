package de.emilyshub.notes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.emilyshub.routines.Routine
import de.emilyshub.todos.Todo
import de.emilyshub.wishes.Wish
import java.io.Serializable

/**
 * What a note is linked to (SPEC.md §7): a goal, a to-do or a routine, which shows the note in turn. Serializable, so
 * that the editor keeps the links of its draft across rotation and the lock.
 */
sealed interface NoteLink : Serializable {
    val id: Long

    data class Goal(override val id: Long) : NoteLink

    data class Todo(override val id: Long) : NoteLink

    data class Routine(override val id: Long) : NoteLink
}

/** A note linked to a goal; either deleted takes the link along. */
@Entity(
    tableName = "note_wishes",
    primaryKeys = ["note_id", "wish_id"],
    foreignKeys = [
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Wish::class, parentColumns = ["id"], childColumns = ["wish_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["wish_id"])],
)
data class NoteWish(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "wish_id") val wishId: Long,
)

/** A note linked to a to-do; either deleted takes the link along. One coming again takes it on to the next. */
@Entity(
    tableName = "note_todos",
    primaryKeys = ["note_id", "todo_id"],
    foreignKeys = [
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Todo::class, parentColumns = ["id"], childColumns = ["todo_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["todo_id"])],
)
data class NoteTodo(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "todo_id") val todoId: Long,
)

/** A note linked to a routine; either deleted takes the link along. */
@Entity(
    tableName = "note_routines",
    primaryKeys = ["note_id", "routine_id"],
    foreignKeys = [
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["routine_id"])],
)
data class NoteRoutine(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "routine_id") val routineId: Long,
)

/** Something a note can be linked to, by its [name]; [open] unless done, or archived, so that it is offered no more. */
data class LinkCandidate(val link: NoteLink, val name: String, val open: Boolean)

/** A goal, to-do or routine as [LinkCandidate]s are read. */
data class LinkTarget(val id: Long, val name: String, val open: Boolean)
