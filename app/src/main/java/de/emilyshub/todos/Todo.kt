package de.emilyshub.todos

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.emilyshub.data.InputText
import de.emilyshub.places.Place
import de.emilyshub.routines.ChecklistEntry
import de.emilyshub.routines.Priority
import de.emilyshub.tags.Tag
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** When a to-do is due (SPEC.md §5): not at all, on a day, or on a day at a time. */
sealed interface Due : Serializable {
    /** The day it is due on, if any. */
    val date: LocalDate?

    data object None : Due {
        override val date: LocalDate? get() = null
    }

    data class On(override val date: LocalDate) : Due

    data class At(override val date: LocalDate, val time: LocalTime) : Due
}

/**
 * A to-do (SPEC.md §5): a task done once, [due] or not. Ticked off, it is [doneAt] then and in the archive, from where
 * it can be brought back. Its checklist is kept apart, as [TodoChecklistItem]s, its tags as [TodoTag]s, and the places
 * it reminds at as [TodoPlace]s; how far it has reminded is its [reminding].
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
    @Embedded val reminding: Reminding = Reminding(),
) {
    /** [dueDate] and [dueTime] as one. */
    val due: Due
        get() = when {
            dueDate == null -> Due.None
            dueTime == null -> Due.On(dueDate)
            else -> Due.At(dueDate, dueTime)
        }
}

/**
 * Where a to-do's reminder stands (SPEC.md §3, §4). It pops up at its due time if it had one still ahead when that was
 * saved ([remindsAtDueTime]), so that a time gone by already does not pop up. Bound to places, it is [waiting] for them until
 * it has popped up: at its time if there, otherwise on arriving at one, from its time or day on, or from the start
 * without either. It pops up anywhere at [remindAt]: when it was put off to ("Später"), or on arrival, by the alarm.
 * Looking around, the app notes when it has seen the device surely [away] from its places: surely at one again after
 * that, it has arrived.
 */
data class Reminding(
    @ColumnInfo(name = "reminds_at_due_time", defaultValue = "0") val remindsAtDueTime: Boolean = false,
    @ColumnInfo(name = "remind_at") val remindAt: Instant? = null,
    @ColumnInfo(defaultValue = "0") val waiting: Boolean = false,
    @ColumnInfo(defaultValue = "0") val away: Boolean = false,
)

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

/** A place a to-do reminds at (SPEC.md §4, §5): it pops up there, at any of its places, and waits elsewhere. */
@Entity(
    tableName = "todo_places",
    primaryKeys = ["todo_id", "place_id"],
    foreignKeys = [
        ForeignKey(entity = Todo::class, parentColumns = ["id"], childColumns = ["todo_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Place::class, parentColumns = ["id"], childColumns = ["place_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["place_id"])],
)
data class TodoPlace(
    @ColumnInfo(name = "todo_id") val todoId: Long,
    @ColumnInfo(name = "place_id") val placeId: Long,
)

/** A to-do with its checklist, tags and places. */
data class TodoWithDetails(
    @Embedded val todo: Todo,
    @Relation(parentColumn = "id", entityColumn = "todo_id") val checklistItems: List<TodoChecklistItem>,
    @Relation(parentColumn = "id", entityColumn = "todo_id") val tags: List<TodoTag>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(TodoPlace::class, parentColumn = "todo_id", entityColumn = "place_id"),
    )
    val places: List<Place>,
) {
    /** The ids of its tags, as the editor and the tag filter take them. */
    val tagIds: Set<Long> get() = tags.map { it.tagId }.toSet()

    /** The ids of the places it reminds at; none: it reminds by its time only. */
    val placeIds: Set<Long> get() = places.map { it.id }.toSet()

    /** The names of its places, in alphabetical order. */
    val placeNames: List<String> get() = places.map { it.name }.sortedWith(InputText.byName)

    /** Whether it reminds at all: at its time, or at its places. */
    val reminds: Boolean get() = todo.due is Due.At || places.isNotEmpty()

    /** Whether it waits for its places, to pop up on arriving at one; with none left, it waits for nothing. */
    val waitsForPlaces: Boolean get() = todo.reminding.waiting && places.isNotEmpty()

    /** Whether the app has seen the device surely away from its places, looking around, since it began waiting. */
    val seenAway: Boolean get() = todo.reminding.away

    /** Its reminder, as it pops up. */
    fun toReminder() = TodoReminder(todo.id, todo.title, todo.due, placeNames)

    /** The checklist in its order, each item ticked off on its own or with all of it once the to-do is done. */
    fun checklist(): List<ChecklistEntry> = checklistItems.sortedBy { it.position }.map {
        ChecklistEntry(it.id, it.text, ticked = todo.doneAt != null || it.ticked)
    }
}

/**
 * A to-do on Heute: due by then, open or ticked off that day, [overdue] while still open after its time, and with the
 * names of the places it waits for, if it waits.
 */
data class DueTodo(
    val todoId: Long,
    val title: String,
    val due: Due,
    val priority: Priority,
    val done: Boolean,
    val overdue: Boolean,
    val checklist: List<ChecklistEntry> = emptyList(),
    val waitingFor: List<String> = emptyList(),
)

/** A to-do to remind of (SPEC.md §3): its [title], when it is [due], and the names of the [places] it reminds at. */
data class TodoReminder(val todoId: Long, val title: String, val due: Due, val places: List<String>)

/** How the To-dos list is ordered (SPEC.md §5): by when they are due, or by priority. */
enum class TodoOrder(val comparator: Comparator<TodoWithDetails>) {
    /**
     * The soonest due first, the overdue ones thus at the top: a day's to-dos with a time by it, then those due by the
     * end of the day, without one; those due whenever last. Due at the same time, the higher priority first, then by
     * title.
     */
    ByDue(byDue().thenByDescending { it.todo.priority }.thenBy(InputText.byName) { it.todo.title }),

    /** The higher priority first, then as [ByDue]. */
    ByPriority(compareByDescending<TodoWithDetails> { it.todo.priority }.then(byDue()).thenBy(InputText.byName) { it.todo.title }),
}

private fun byDue(): Comparator<TodoWithDetails> =
    compareBy<TodoWithDetails, LocalDate?>(nullsLast()) { it.todo.dueDate }.thenBy(nullsLast()) { it.todo.dueTime }
