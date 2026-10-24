package de.emilyshub.todos

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.emilyshub.data.InputText
import de.emilyshub.routines.ChecklistEntry
import de.emilyshub.routines.Priority
import de.emilyshub.tags.Tag
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** When a to-do is due (SPEC.md §5): not at all, on a day, or on a day at a time. */
sealed interface Due : Serializable {
    data object None : Due

    data class On(val date: LocalDate) : Due

    data class At(val date: LocalDate, val time: LocalTime) : Due
}

/**
 * A to-do (SPEC.md §5): a task done once, [due] or not. Ticked off, it is [doneAt] then and in the archive, from where
 * it can be brought back. Its checklist is kept apart, as [TodoChecklistItem]s, and its tags as [TodoTag]s.
 */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate? = null,
    /** Only with a [dueDate]. */
    @ColumnInfo(name = "due_time") val dueTime: LocalTime? = null,
    val priority: Priority = Priority.Normal,
    @ColumnInfo(name = "done_at") val doneAt: Instant? = null,
) {
    val due: Due
        get() = when {
            dueDate == null -> Due.None
            dueTime == null -> Due.On(dueDate)
            else -> Due.At(dueDate, dueTime)
        }
}

/** A sub-item of a to-do's checklist, at its [position] among the others, [ticked] off or not. */
@Entity(
    tableName = "todo_checklist_items",
    foreignKeys = [
        ForeignKey(entity = Todo::class, parentColumns = ["id"], childColumns = ["todo_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["todo_id"])],
)
data class TodoChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "todo_id") val todoId: Long,
    val position: Int,
    val text: String,
    val ticked: Boolean = false,
)

/** A tag given to a to-do: the to-dos' link to the shared tags (SPEC.md §8). */
@Entity(
    tableName = "todo_tags",
    primaryKeys = ["todo_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = Todo::class, parentColumns = ["id"], childColumns = ["todo_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class TodoTag(
    @ColumnInfo(name = "todo_id") val todoId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

/** A to-do with its checklist and tags. */
data class TodoWithDetails(
    @Embedded val todo: Todo,
    @Relation(parentColumn = "id", entityColumn = "todo_id") val checklistItems: List<TodoChecklistItem>,
    @Relation(parentColumn = "id", entityColumn = "todo_id") val tags: List<TodoTag>,
) {
    val tagIds: Set<Long> get() = tags.map { it.tagId }.toSet()

    /** The checklist in its order, each item ticked off on its own or with all of it once the to-do is done. */
    fun checklist(): List<ChecklistEntry> = checklistItems.sortedBy { it.position }.map {
        ChecklistEntry(it.id, it.text, ticked = todo.doneAt != null || it.ticked)
    }
}

/** A to-do on Heute: due by then, open or ticked off that day, [overdue] while still open past its time. */
data class DueTodo(
    val todoId: Long,
    val title: String,
    val due: Due,
    val priority: Priority,
    val done: Boolean,
    val overdue: Boolean,
    val checklist: List<ChecklistEntry> = emptyList(),
)

/** How the To-dos list is ordered (SPEC.md §5): by when they are due, or by priority. */
enum class TodoOrder(val comparator: Comparator<TodoWithDetails>) {
    /**
     * The soonest first, overdue ones thus at the top, a day before its times, those due whenever last; at the same
     * time, the higher priority first, then by title.
     */
    ByDue(byDue().thenByDescending { it.todo.priority }.thenBy(InputText.byName) { it.todo.title }),

    /** The higher priority first, then as [ByDue]. */
    ByPriority(compareByDescending<TodoWithDetails> { it.todo.priority }.then(byDue()).thenBy(InputText.byName) { it.todo.title }),
}

private fun byDue(): Comparator<TodoWithDetails> =
    compareBy<TodoWithDetails, LocalDate?>(nullsLast()) { it.todo.dueDate }.thenBy(nullsFirst()) { it.todo.dueTime }
