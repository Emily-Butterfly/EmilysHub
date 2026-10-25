package de.emilyshub.todos

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.places.Whereabouts
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.routines.Locate
import de.emilyshub.routines.dueAt
import de.emilyshub.routines.today
import de.emilyshub.wishes.GoalPart
import de.emilyshub.wishes.settleGoals
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

    /** The open to-dos, each with its checklist, tags and places, in no order: the list orders them as chosen ([TodoOrder]). */
    fun open(): Flow<List<TodoWithDetails>> = observe { all -> all.filter { it.todo.doneAt == null } }

    /** The to-dos ticked off, in the archive: the one done last first. */
    fun archived(): Flow<List<Todo>> =
        observe { all -> all.map { it.todo }.filter { it.doneAt != null }.sortedByDescending { it.doneAt } }

    /**
     * The to-dos on Heute of [date]: those due by then, still open or ticked off that day, each [DueTodo.overdue] as
     * [isOverdue] says at [now], and waiting for its places once its time or day has come, as [arrive] has it.
     */
    fun dueOn(date: LocalDate, now: Instant): Flow<List<DueTodo>> = observe { all ->
        all.filter { (todo) ->
            val due = todo.dueDate
            val done = todo.doneAt
            due != null && !due.isAfter(date) && (done == null || today(done, clock.zone) == date)
        }.map { details ->
            val todo = details.todo
            val overdue = isOverdue(todo, now)
            val waiting = todo.doneAt == null && todo.reminding.waiting && waitsFrom(todo.due, now)
            val waitingFor = if (waiting) details.placeNames else emptyList()
            val done = todo.doneAt != null
            DueTodo(todo.id, todo.title, todo.due, todo.priority, done, overdue, details.checklist(), waitingFor)
        }
    }

    /** The to-do, or null once it is deleted. */
    suspend fun get(id: Long): Todo? = withContext(io) { dao.get(id) }

    /** The to-do's checklist, in its order. */
    suspend fun checklist(id: Long): List<TodoChecklistItem> = withContext(io) { dao.checklist(id) }

    /** The to-do's tags (SPEC.md §8). */
    suspend fun tagIdsOf(id: Long): Set<Long> = withContext(io) { dao.tagIdsOf(id).toSet() }

    /** The places the to-do reminds at (SPEC.md §4); none: by its time only. */
    suspend fun placeIdsOf(id: Long): Set<Long> = withContext(io) { dao.placeIdsOf(id).toSet() }

    /**
     * A new to-do, open, reminding as [Reminding] describes; refuses a draft with problems, which the editor shows
     * before. Under a goal, it is a part of it, which opens that goal again if it was done.
     */
    suspend fun create(draft: TodoDraft): Long = write {
        val cleaned = checked(draft)
        database().withTransaction {
            val goalId = database().wishes().stillThere(cleaned.goalId)
            dao.insert(cleaned.copy(goalId = goalId).toTodo()).also { id ->
                saveChecklist(id, cleaned.checklist)
                saveTags(id, cleaned.tagIds)
                savePlaces(id, cleaned.placeIds)
                rearm(id, cleaned.due)
                settleGoals(GoalPart.None, GoalPart(goalId, open = true))
            }
        }
    }

    /**
     * Changes the to-do as [draft] has it, as [create] checks it; checklist items kept keep their ticks. A new due or new
     * places set its reminder anew, as for one just created; other changes leave it as it is.
     */
    suspend fun update(id: Long, draft: TodoDraft) = write {
        val cleaned = checked(draft)
        database().withTransaction {
            val todo = dao.get(id) ?: return@withTransaction
            val placesBefore = dao.placeIdsOf(id).toSet()
            val goalId = database().wishes().stillThere(cleaned.goalId)
            dao.update(cleaned.copy(goalId = goalId).toTodo(id, doneAt = todo.doneAt, reminding = todo.reminding))
            saveChecklist(id, cleaned.checklist)
            saveTags(id, cleaned.tagIds)
            savePlaces(id, cleaned.placeIds)
            if (cleaned.due != todo.due || dao.placeIdsOf(id).toSet() != placesBefore) rearm(id, cleaned.due)
            settleGoals(todo)
        }
    }

    /**
     * Ticks the to-do off, into the archive (SPEC.md §5); its checklist counts as ticked off with it, and its goal is
     * done if that was the last part open.
     */
    suspend fun markDone(id: Long) = write {
        database().withTransaction {
            val todo = dao.get(id) ?: return@withTransaction
            dao.setDoneAt(id, clock.instant())
            settleGoals(todo)
        }
    }

    /**
     * Opens the to-do again, out of the archive. What of its checklist was ticked off stays so, unless all of it was,
     * as that made it done. It reminds anew, as one just saved.
     */
    suspend fun reopen(id: Long) = write {
        database().withTransaction {
            val todo = dao.get(id) ?: return@withTransaction
            if (dao.checklist(id).allTicked()) dao.setAllTicked(id, ticked = false)
            dao.setDoneAt(id, null)
            rearm(id, todo.due)
            settleGoals(todo)
        }
    }

    /** Deletes the to-do for good, with its checklist, tags and places; its goal is settled without it. */
    suspend fun delete(id: Long) = write {
        database().withTransaction {
            val todo = dao.get(id) ?: return@withTransaction
            dao.delete(id)
            settleGoals(todo)
        }
    }

    /** Puts the to-do's reminder off ("Später"): it pops up again at [until], anywhere. */
    suspend fun putOff(id: Long, until: Instant) = write { dao.putOff(id, until) }

    /**
     * The open to-dos to remind of after [after] and up to [until] (SPEC.md §3), at their time or when put off to, by
     * that time and their title. One waiting for its places pops up at its time only where [locate] says the device is
     * at one of them, and waits on elsewhere, for [arrive]; [locate] is only asked when that matters, about the places
     * that do. Not reported as a change: the reminders do this themselves.
     */
    suspend fun remindersDue(after: Instant, until: Instant, locate: Locate): List<TodoReminder> =
        withContext(io + NonCancellable) {
            val due = dao.open()
                .mapNotNull { details -> nextReminder(details.todo)?.let { time -> details to time } }
                .filter { (_, time) -> time.isAfter(after) && !time.isAfter(until) }
                .sortedWith(compareBy<Pair<TodoWithDetails, Instant>> { it.second }.thenBy(InputText.byName) { it.first.todo.title })
                .map { it.first }
            val held = due.filter { it.waitsForPlaces }
            val whereabouts = if (held.isEmpty()) Whereabouts.Unknown else locate(held.flatMap { it.placeIds }.toSet())
            val shown = due.filter { it !in held || whereabouts.allows(it.placeIds) }
            dao.stopWaiting(shown.map { it.todo.id })
            // Held back as surely elsewhere: arriving at one of its places from now on is arriving.
            dao.setAway((held - shown.toSet()).map { it.todo.id }, away = true)
            shown.map(TodoWithDetails::toReminder)
        }

    /**
     * Arrived at a place (SPEC.md §4): the open to-dos waiting for one of the places [locate] says the device is at, once
     * their time or day has come or without either, are to pop up now and wait no more, as [remindersDue] then has them.
     * Returns them. [locate] is only asked while one waits, about the places it waits for. Not reported as a change: the
     * reminders do this themselves.
     */
    suspend fun arrive(locate: Locate): List<TodoReminder> = withContext(io + NonCancellable) {
        val now = now()
        val waiting = dao.openWaiting().filter { it.waitsForPlaces && waitsFrom(it.todo.due, now) }
        if (waiting.isEmpty()) return@withContext emptyList()
        val whereabouts = locate(waiting.flatMap { it.placeIds }.toSet())
        val (arrived, elsewhere) = waiting.partition { whereabouts.allows(it.placeIds) }
        dao.setAway(elsewhere.map { it.todo.id }, away = true)
        popUp(arrived, now)
    }

    /**
     * The to-dos waiting for places they are bound to no more, as those were deleted, wait no more: those waiting already
     * pop up now, as on arrival; the others remind at their time, anywhere. Not reported as a change: the reminders do
     * this themselves.
     */
    suspend fun releaseFromPlaces() = withContext(io + NonCancellable) {
        val now = now()
        val (waitingAlready, ahead) = dao.openWaiting().filter { it.placeIds.isEmpty() }.partition { waitsFrom(it.todo.due, now) }
        dao.stopWaiting(ahead.map { it.todo.id })
        popUp(waitingAlready, now)
    }

    /**
     * Looked around, as the reminders do every so often to notice arriving (SPEC.md §4): an open to-do waiting for its
     * places is seen away from them where [locate] says the device is surely elsewhere, and has arrived once it is surely
     * at one of them after that. Then it pops up, as on [arrive], if its time or day has come; arriving before is not
     * arriving then. Being at one of them already, or maybe, is not arriving. Returns those popping up. [locate] is only
     * asked while one waits, about the places it waits for. Not reported as a change: the reminders do this themselves.
     */
    suspend fun lookAround(locate: Locate): List<TodoReminder> = withContext(io + NonCancellable) {
        val waiting = dao.openWaiting().filter { it.waitsForPlaces }
        if (waiting.isEmpty()) return@withContext emptyList()
        val whereabouts = locate(waiting.flatMap { it.placeIds }.toSet())
        val there = waiting.filter { whereabouts.surelyAt(it.placeIds) }
        val arrived = there.filter { it.seenAway }
        dao.setAway(waiting.filter { !whereabouts.allows(it.placeIds) }.map { it.todo.id }, away = true)
        dao.setAway(there.map { it.todo.id }, away = false)
        val now = now()
        popUp(arrived.filter { waitsFrom(it.todo.due, now) }, now)
    }

    /** Whether an open to-do waits for its places, so that arriving at one matters. */
    suspend fun anyWaiting(): Boolean = withContext(io) { dao.anyOpenWaiting() }

    /**
     * Whether looking around is needed: an open to-do waits, its time or day come, for a place where only looking tells
     * arriving, one with a GPS area. Joining a WLAN tells arriving at the others.
     */
    suspend fun needsLookingAround(): Boolean = withContext(io) {
        val now = now()
        dao.openWaiting().any { it.waitsForPlaces && waitsFrom(it.todo.due, now) && it.places.any { place -> place.area != null } }
    }

    /** Of [ids], the to-dos to remind of now: open and popped up, not put off or set anew since. */
    suspend fun stillDue(ids: Collection<Long>): List<TodoReminder> = withContext(io) {
        val now = now()
        dao.withDetails(ids).filter { isReminding(it.todo, now) }.map(TodoWithDetails::toReminder)
    }

    /**
     * When the reminders have something to do for a to-do next after [after]: pop it up, at its time or when put off to,
     * or begin to look around for one waiting for its places, as its day begins; none if never.
     */
    suspend fun nextAlarm(after: Instant): Instant? = withContext(io) {
        dao.open().flatMap { listOfNotNull(nextReminder(it.todo), waitBegins(it)) }.filter { it.isAfter(after) }.minOrNull()
    }

    /** When a to-do due on a day, waiting for its places, begins to wait: as its day begins. */
    private fun waitBegins(details: TodoWithDetails): Instant? =
        (details.todo.due as? Due.On)?.takeIf { details.waitsForPlaces }?.date?.atStartOfDay(clock.zone)?.toInstant()

    /**
     * Ticks an item of the to-do's checklist off, or unticks it, and tells whether that ticked the to-do off: with all
     * of it ticked off, the to-do is done. Unticking one of a to-do done opens it again, the others still ticked off,
     * and it reminds anew, as [reopen] has it.
     */
    suspend fun tick(todoId: Long, itemId: Long, ticked: Boolean): Boolean = write {
        database().withTransaction {
            val todo = dao.get(todoId) ?: return@withTransaction false
            if (dao.checklist(todoId).none { it.id == itemId }) return@withTransaction false
            val done = todo.doneAt != null
            when {
                ticked && !done -> {
                    dao.setTicked(itemId, ticked = true)
                    doneIfAllTicked(todoId).also { settleGoals(todo) }
                }
                // Done as a whole, every item counts as ticked off until one is unticked.
                !ticked && done -> {
                    dao.setAllTicked(todoId, ticked = true)
                    dao.setTicked(itemId, ticked = false)
                    dao.setDoneAt(todoId, null)
                    rearm(todoId, todo.due)
                    settleGoals(todo)
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
            is Due.At -> !now.isBefore(instantOf(due).plus(1, ChronoUnit.MINUTES))
        }
    }

    /** The moment of [due] in the phone's current time zone. */
    private fun instantOf(due: Due.At): Instant = dueAt(due.date, due.time, clock.zone).toInstant()

    /** When [todo] pops up next, anywhere or waiting for its places: when put off to, or at its time. */
    private fun nextReminder(todo: Todo): Instant? =
        todo.reminding.remindAt ?: (todo.due as? Due.At)?.takeIf { todo.reminding.remindsAtDueTime }?.let(::instantOf)

    /** Whether [todo] is open and its reminder has popped up by [now], not put off since or set anew to come. */
    private fun isReminding(todo: Todo, now: Instant): Boolean =
        todo.doneAt == null && !todo.reminding.waiting && nextReminder(todo)?.isAfter(now) == false

    /** Whether a to-do [due] then waits for its places at [now]: from its time on, from its day on, or always without. */
    private fun waitsFrom(due: Due, now: Instant): Boolean = when (due) {
        Due.None -> true
        is Due.On -> !due.date.isAfter(dayOf(now))
        is Due.At -> !instantOf(due).isAfter(now)
    }

    /** [todos] pop up [now], by the alarm, and wait no more; returns their reminders, by title. */
    private suspend fun popUp(todos: List<TodoWithDetails>, now: Instant): List<TodoReminder> {
        dao.popUp(todos.map { it.todo.id }, at = now)
        return todos.sortedWith(compareBy(InputText.byName) { it.todo.title }).map(TodoWithDetails::toReminder)
    }

    /** Sets the to-do's reminder anew, as [Reminding] has it for one just saved [due] then. */
    private suspend fun rearm(id: Long, due: Due) {
        val remindsAtDueTime = due is Due.At && instantOf(due).isAfter(now())
        dao.rearm(id, remindsAtDueTime, waiting = dao.placeIdsOf(id).isNotEmpty())
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

    /** Settles the goals of the to-do (SPEC.md §6) after a change to it, as it was [before]. */
    private suspend fun settleGoals(before: Todo) = settleGoals(before.asPart(), dao.get(before.id)?.asPart() ?: GoalPart.None)

    private suspend fun settleGoals(before: GoalPart, after: GoalPart) = database().settleGoals(before, after, today())

    private fun Todo.asPart() = GoalPart(goalId, open = doneAt == null)

    private suspend fun saveTags(todoId: Long, tagIds: Set<Long>) {
        dao.untagAll(todoId)
        dao.tag(todoId, tagIds)
    }

    private suspend fun savePlaces(todoId: Long, placeIds: Set<Long>) {
        dao.unbindAll(todoId)
        dao.bind(todoId, placeIds)
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
