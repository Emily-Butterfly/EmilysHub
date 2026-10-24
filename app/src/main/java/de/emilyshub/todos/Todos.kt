package de.emilyshub.todos

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.routines.dueAt
import de.emilyshub.routines.today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.coroutines.CoroutineContext

/**
 * The to-dos (SPEC.md §5). [database] is only called on [io], since the first call opens it. Writes run to the end
 * once started, even if the screen that started them goes away, and [onChange] runs after each.
 */
class Todos(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val onChange: suspend () -> Unit = {},
) {
    private val dao get() = database().todos()

    /** The open to-dos, each with its checklist and tags, in no order: the list orders them as chosen ([TodoOrder]). */
    fun open(): Flow<List<TodoWithDetails>> = observe { all -> all.filter { it.todo.doneAt == null } }

    /** The to-dos ticked off, in the archive: the one done last first. */
    fun archived(): Flow<List<Todo>> =
        observe { all -> all.map { it.todo }.filter { it.doneAt != null }.sortedByDescending { it.doneAt } }

    /**
     * The to-dos on Heute of [date]: those due by then, still open or ticked off that day, each [DueTodo.overdue] as
     * [isOverdue] says at [now].
     */
    fun dueOn(date: LocalDate, now: Instant): Flow<List<DueTodo>> = observe { all ->
        all.filter { (todo) ->
            val due = todo.dueDate
            val done = todo.doneAt
            due != null && !due.isAfter(date) && (done == null || today(done, clock.zone) == date)
        }.map { details ->
            val todo = details.todo
            val overdue = isOverdue(todo, now)
            DueTodo(todo.id, todo.title, todo.due, todo.priority, done = todo.doneAt != null, overdue, details.checklist())
        }
    }

    /** The to-do, or null once it is deleted. */
    suspend fun get(id: Long): Todo? = withContext(io) { dao.get(id) }

    /** The to-do's checklist, in its order. */
    suspend fun checklist(id: Long): List<TodoChecklistItem> = withContext(io) { dao.checklist(id) }

    /** The to-do's tags (SPEC.md §8). */
    suspend fun tagIdsOf(id: Long): Set<Long> = withContext(io) { dao.tagIdsOf(id).toSet() }

    /** A new to-do, open; refuses a draft with problems, which the editor shows before. */
    suspend fun create(draft: TodoDraft): Long = write {
        val cleaned = checked(draft)
        database().withTransaction {
            dao.insert(cleaned.toTodo()).also { id ->
                saveChecklist(id, cleaned.checklist)
                saveTags(id, cleaned.tagIds)
            }
        }
    }

    /** Changes the to-do as [draft] has it; checklist items kept keep their ticks, as [create] checks it. */
    suspend fun update(id: Long, draft: TodoDraft) = write {
        val cleaned = checked(draft)
        database().withTransaction {
            val todo = dao.get(id) ?: return@withTransaction
            dao.update(cleaned.toTodo(id, doneAt = todo.doneAt))
            saveChecklist(id, cleaned.checklist)
            saveTags(id, cleaned.tagIds)
        }
    }

    /** Ticks the to-do off, into the archive (SPEC.md §5); its checklist counts as ticked off with it. */
    suspend fun markDone(id: Long) = write { dao.setDoneAt(id, clock.instant()) }

    /**
     * Opens the to-do again, out of the archive. What of its checklist was ticked off stays so, unless all of it was,
     * as that made it done.
     */
    suspend fun reopen(id: Long) = write {
        database().withTransaction {
            if (dao.checklist(id).allTicked()) dao.setAllTicked(id, ticked = false)
            dao.setDoneAt(id, null)
        }
    }

    /** Deletes the to-do for good, with its checklist and tags. */
    suspend fun delete(id: Long) = write { dao.delete(id) }

    /**
     * Ticks an item of the to-do's checklist off, or unticks it, and tells whether that ticked the to-do off: with all
     * of it ticked off, the to-do is done. Unticking one of a to-do done opens it again, the others still ticked off.
     */
    suspend fun tick(todoId: Long, itemId: Long, ticked: Boolean): Boolean = write {
        database().withTransaction {
            val todo = dao.get(todoId) ?: return@withTransaction false
            if (dao.checklist(todoId).none { it.id == itemId }) return@withTransaction false
            val done = todo.doneAt != null
            when {
                ticked && !done -> {
                    dao.setTicked(itemId, ticked = true)
                    doneIfAllTicked(todoId)
                }
                // Done as a whole, every item counts as ticked off until one is unticked.
                !ticked && done -> {
                    dao.setAllTicked(todoId, ticked = true)
                    dao.setTicked(itemId, ticked = false)
                    dao.setDoneAt(todoId, null)
                    false
                }
                !ticked -> {
                    dao.setTicked(itemId, ticked = false)
                    false
                }
                else -> false
            }
        }
    }

    /** The time now, for what changes as time goes by: a to-do turning overdue. */
    fun now(): Instant = clock.instant()

    /** The local date now; it changes at midnight in the phone's current time zone. */
    fun today(): LocalDate = dayOf(now())

    /** The local date of [moment], such as the day a to-do was ticked off. */
    fun dayOf(moment: Instant): LocalDate = today(moment, clock.zone)

    /** The next full hour, for a time due to start from: 08:00 at 07:10, and midnight of the next day at 23:10. */
    fun nextFullHour(): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS).plusHours(1)

    /** Whether [todo] is open at [now] after it was due: after its day, or after the minute of its time. */
    fun isOverdue(todo: Todo, now: Instant): Boolean {
        val due = todo.due
        return todo.doneAt == null && when (due) {
            Due.None -> false
            is Due.On -> due.date.isBefore(dayOf(now))
            is Due.At -> !now.isBefore(dueAt(due.date, due.time, clock.zone).toInstant().plus(1, ChronoUnit.MINUTES))
        }
    }

    /** Ticks the to-do off if all of its checklist is, and tells whether it did. */
    private suspend fun doneIfAllTicked(todoId: Long): Boolean =
        dao.checklist(todoId).allTicked().also { allTicked -> if (allTicked) dao.setDoneAt(todoId, clock.instant()) }

    /**
     * Saves the to-do's checklist as [items] has it, in that order: an item with the id of one saved changes it and
     * keeps its tick; the others are new, and the saved ones not among them go. An open to-do with all that is left
     * ticked off is done then.
     */
    private suspend fun saveChecklist(todoId: Long, items: List<ChecklistDraftItem>) {
        val saved = dao.checklist(todoId).associateBy { it.id }
        val checklist = items.mapIndexed { position, item ->
            val kept = saved[item.id]
            TodoChecklistItem(kept?.id ?: 0, todoId, position, item.text, ticked = kept?.ticked ?: false)
        }
        val (keeping, new) = checklist.partition { it.id != 0L }
        dao.deleteChecklistItemsBut(todoId, kept = keeping.map { it.id })
        dao.updateChecklistItems(keeping)
        dao.insertChecklistItems(new)
        if (dao.get(todoId)?.doneAt == null) doneIfAllTicked(todoId)
    }

    private suspend fun saveTags(todoId: Long, tagIds: Set<Long>) {
        dao.untagAll(todoId)
        dao.tag(todoId, tagIds)
    }

    private fun <T> observe(select: (List<TodoWithDetails>) -> T): Flow<T> = flow { emitAll(dao.observeAll().map(select)) }.flowOn(io)

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block().also { onChange() } }

    private fun checked(draft: TodoDraft): TodoDraft {
        require(draft.problems.isEmpty()) { "Invalid to-do: ${draft.problems}" }
        return draft.cleaned()
    }

    /** Whether there is a checklist and all of it is ticked off. */
    private fun List<TodoChecklistItem>.allTicked() = isNotEmpty() && all { it.ticked }
}
