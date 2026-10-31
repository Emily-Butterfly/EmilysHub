package de.emilyshub.routines

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.places.Whereabouts
import de.emilyshub.wishes.GoalPart
import de.emilyshub.wishes.GoalRoutine
import de.emilyshub.wishes.settleGoals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.coroutines.CoroutineContext

/**
 * The routines and their occurrences (SPEC.md §2). [database] is only called on [io], since the first
 * call opens it. Writes run to the end once started, even if the screen that started them goes away:
 * otherwise a save cut short by the app lock or a rotation could leave the editor open over a routine
 * that was already saved. After each write, [onChange] runs, for the reminders to follow.
 */
class Routines(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val onChange: suspend () -> Unit = {},
) {
    private val dao get() = database().routines()

    /** Those in use, active or paused, by their first time, then by name; the archived ones are [archived]. */
    fun inUse(): Flow<List<Routine>> {
        val order = compareBy<Routine> { it.times.first() }.thenBy(InputText.byName, Routine::name)
        return observe(order) { it.status != RoutineStatus.Archived }
    }

    /**
     * How every occurrence of every routine, active, paused or archived, ended or that it is still open, for the
     * statistics (SPEC.md §8); those of a routine deleted go with it. Anew only once one of them changes, not as the
     * reminders note what they did.
     */
    fun history(): Flow<List<OccurrenceOutcome>> = flow { emitAll(dao.observeOutcomes().distinctUntilChanged()) }.flowOn(io)

    /** How the routine's occurrences ended, as [history] has it. */
    fun history(routineId: Long): Flow<List<OccurrenceOutcome>> =
        flow { emitAll(dao.observeOutcomesOf(routineId).distinctUntilChanged()) }.flowOn(io)

    /** Every routine: those in use, as [inUse] orders them, then the archived ones, as [archived] does. */
    fun all(): Flow<List<Routine>> = combine(inUse(), archived()) { inUse, archived -> inUse + archived }

    /** The archived routines (SPEC.md §2), by name. */
    fun archived(): Flow<List<Routine>> = observe(compareBy(InputText.byName, Routine::name)) { it.status == RoutineStatus.Archived }

    private fun observe(order: Comparator<Routine>, which: (Routine) -> Boolean): Flow<List<Routine>> = flow {
        emitAll(dao.observeAll().map { all -> all.filter(which).sortedWith(order) })
    }.flowOn(io)

    /**
     * Due from now on: a time already past today does not count today, so a routine never starts out
     * overdue. Refuses a draft with problems; the editor shows them before it gets here.
     */
    suspend fun create(draft: RoutineDraft): Long = write {
        val cleaned = checked(draft)
        database().withTransaction {
            // Under a goal deleted meanwhile, it aims at nothing either.
            val goalId = database().wishes().stillThere(cleaned.goalId)
            dao.insert(cleaned.copy(goalId = goalId).cleaned().toRoutine(plannedFrom = now())).also { id ->
                saveChecklist(id, cleaned.checklist)
                saveTags(id, cleaned.tagIds)
                savePlaces(id, cleaned.placeIds)
                plan(today())
                settleGoals(GoalPart.None, partOf(id))
            }
        }
    }

    suspend fun get(id: Long): Routine? = withContext(io) { dao.get(id) }

    /** The routine's checklist, in its order. */
    suspend fun checklist(routineId: Long): List<ChecklistItem> = withContext(io) { dao.checklist(routineId) }

    /** The routine's tags (SPEC.md §8). */
    suspend fun tagIdsOf(routineId: Long): Set<Long> = withContext(io) { dao.tagIdsOf(routineId).toSet() }

    /** The places the routine pops up at (SPEC.md §4); none: everywhere. */
    suspend fun placeIdsOf(routineId: Long): Set<Long> = withContext(io) { dao.placeIdsOf(routineId).toSet() }

    /** Whether an active routine is bound to a place, so that arriving at one matters. */
    suspend fun anyBoundToPlaces(): Boolean = withContext(io) { dao.anyActiveBound() }

    /** Whether an active routine is bound to a place, as it changes. */
    fun boundToPlaces(): Flow<Boolean> = flow { emitAll(dao.observeAnyActiveBound()) }.flowOn(io)

    /** Each tagged routine's tags, by the routine's id. */
    fun tagIdsByRoutine(): Flow<Map<Long, Set<Long>>> = flow {
        val byRoutine = dao.observeRoutineTags().map { all ->
            all.groupBy(RoutineTag::routineId, RoutineTag::tagId).mapValues { (_, tags) -> tags.toSet() }
        }
        emitAll(byRoutine)
    }.flowOn(io)

    /**
     * New times and repetition apply from now on, as [replanDay] describes: today's open occurrences
     * follow them where that makes nothing overdue, while the past and what was ticked off stay as they were.
     * Checklist items kept keep their ticks; those taken away take theirs along.
     */
    suspend fun update(id: Long, draft: RoutineDraft) = write {
        val changes = checked(draft)
        database().withTransaction {
            val routine = dao.get(id) ?: return@withTransaction
            val part = partOf(id)
            val today = today()
            // Today's list as it was before the change, for the change to apply to.
            plan(today)
            val now = now()
            val rescheduled = changes.times != routine.times || changes.repetition != routine.repetition
            val goalId = database().wishes().stillThere(changes.goalId)
            val plannedFrom = if (rescheduled) now else routine.plannedFrom
            val changed = changes.copy(goalId = goalId).cleaned().toRoutine(id, plannedFrom, routine.status)
            dao.update(changed)
            saveChecklist(id, changes.checklist)
            saveTags(id, changes.tagIds)
            savePlaces(id, changes.placeIds)
            settleGoals(part, partOf(id))
            // One paused or archived has nothing ahead to plan anew.
            if (changed.status != RoutineStatus.Active) return@withTransaction
            val planned = dao.occurrencesFrom(id, today).groupBy { it.date }
            (planned.keys + today).forEach { date -> replan(changed, date, planned[date].orEmpty(), now) }
        }
    }

    /**
     * Whether a routine with [times] and [repetition], planned from now on, would ever come due: one
     * on a single date would not once all its times there have gone by.
     */
    fun comesDue(times: List<LocalTime>, repetition: Repetition): Boolean =
        repetition !is Repetition.Once || times.any { isDueAtOrAfter(now(), repetition.date, it, clock.zone) }

    /**
     * Pauses the routine (SPEC.md §2): it leaves Heute, its open occurrences go, so that the paused days are not
     * missed, and no new ones come until it is [resume]d. What it has done, skipped or missed stays as it is,
     * and one whose time window ended before is missed first.
     */
    suspend fun pause(id: Long) = setAside(id, RoutineStatus.Paused)

    /** Makes a paused routine active again: due from now on, today too at its times still ahead, so nothing is overdue. */
    suspend fun resume(id: Long) = reactivate(id)

    /** Archives the routine (SPEC.md §2): out of the list and Heute, set aside like one paused, with what it did kept. */
    suspend fun archive(id: Long) = setAside(id, RoutineStatus.Archived)

    /** Brings an archived routine back, active again like one [resume]d. */
    suspend fun restore(id: Long) = reactivate(id)

    private suspend fun setAside(id: Long, status: RoutineStatus) = write {
        database().withTransaction {
            val routine = dao.get(id) ?: return@withTransaction
            val now = now()
            val (ended, open) = dao.openOccurrences(id).partition { routine.windowEnded(it, now, clock.zone) }
            settlingGoal(id) { dao.setStatuses(ended.map { it.id }, OccurrenceStatus.Missed) }
            dao.deleteOccurrences(open.map { it.id })
            dao.setRoutineStatus(id, status)
        }
    }

    private suspend fun reactivate(id: Long) = write {
        database().withTransaction {
            val now = now()
            dao.reactivate(id, from = now)
            val routine = dao.get(id) ?: return@withTransaction
            val today = today()
            // Today may have occurrences of it from before, ticked off: the times still ahead are added to them.
            replan(routine, today, dao.occurrencesOn(id, today), now)
        }
    }

    /** Deletes the routine with all its occurrences; its goal is settled without it. */
    suspend fun delete(id: Long) = write {
        database().withTransaction {
            val part = partOf(id)
            dao.delete(id)
            settleGoals(part, GoalPart.None)
        }
    }

    /**
     * The Heute list of [date] in the order it is due: the occurrences of the active routines, and while [date] is
     * today, those of the day before whose time window is still open, as they can still be caught up on.
     */
    fun dueOn(date: LocalDate): Flow<List<DueRoutine>> = flow {
        plan(date)
        val list = dao.observeOccurrencesWithChecklists(from = date.minusDays(1), to = date).map { all ->
            val now = now()
            val catchingUp = today(now, clock.zone) == date
            all.filter { (occurrence, routine) ->
                routine.status == RoutineStatus.Active &&
                    (occurrence.date == date || catchingUp && !routine.windowEnded(occurrence, now, clock.zone))
            }
                .map { due ->
                    val (occurrence, routine) = due
                    val dueAt = dueAt(occurrence.date, occurrence.time, clock.zone)
                    DueRoutine(
                        occurrence.id, routine.id, routine.name, dueAt, occurrence.status, routine.priority,
                        checklist = due.entries(),
                        waitingFor = due.waitingFor(),
                    )
                }
                .sortedWith(compareBy<DueRoutine> { it.dueAt.toInstant() }.thenBy(InputText.byName) { it.name })
        }
        emitAll(list)
    }.flowOn(io)

    suspend fun markDone(occurrenceId: Long) = setStatus(occurrenceId, OccurrenceStatus.Done)

    suspend fun skip(occurrenceId: Long) = setStatus(occurrenceId, OccurrenceStatus.Skipped)

    /**
     * "Heute nicht" (SPEC.md §2): drops the routine of the occurrence for the day. Every one of its occurrences still
     * open on Heute, the later ones of today and one of yesterday still in its time window too, is dropped; none of
     * them reminds any more. Those done, skipped or missed stay as they are.
     */
    suspend fun dropForToday(occurrenceId: Long) = write {
        database().withTransaction {
            val occurrence = dao.occurrence(occurrenceId) ?: return@withTransaction
            val routine = dao.get(occurrence.routineId)?.takeIf { it.status == RoutineStatus.Active } ?: return@withTransaction
            val open = onHeute(routine).filter { it.status == OccurrenceStatus.Open }
            settlingGoal(routine.id) { dao.setStatuses(open.map { it.id }, OccurrenceStatus.Dropped) }
        }
    }

    /**
     * Undoes "done" or "skipped" of an active routine; undoing "done" unticks the checklist too. If the routine's
     * times changed meanwhile, the occurrence is planned anew like an open one when they changed ([replanDay]): it
     * moves to the new time it stood in for while that is ahead, and goes if the routine no longer has a time for
     * it and it is still to come. Undoing "Heute nicht" ([dropForToday]) undoes it for every occurrence it dropped
     * on Heute: open again while their time window is open, missed once it has ended.
     */
    suspend fun reopen(occurrenceId: Long) = write {
        database().withTransaction {
            val occurrence = dao.occurrence(occurrenceId)?.takeIf { it.status != OccurrenceStatus.Missed } ?: return@withTransaction
            val routine = dao.get(occurrence.routineId)?.takeIf { it.status == RoutineStatus.Active } ?: return@withTransaction
            settlingGoal(routine.id) {
                when (occurrence.status) {
                    OccurrenceStatus.Dropped -> onHeute(routine).filter { it.status == OccurrenceStatus.Dropped }.forEach {
                        if (routine.windowEnded(it, now(), clock.zone)) {
                            dao.setStatus(it.id, OccurrenceStatus.Missed)
                        } else {
                            reopen(it, routine)
                        }
                    }
                    OccurrenceStatus.Done -> {
                        dao.deleteTicks(occurrenceId)
                        reopen(occurrence, routine)
                    }
                    else -> reopen(occurrence, routine)
                }
            }
        }
    }

    /** The [routine]'s occurrences on Heute: today's, and yesterday's still in their time window. */
    private suspend fun onHeute(routine: Routine): List<Occurrence> {
        val today = today()
        return dao.occurrencesOn(routine.id, today) +
            dao.occurrencesOn(routine.id, today.minusDays(1)).filterNot { routine.windowEnded(it, now(), clock.zone) }
    }

    /**
     * Ticks an item of the occurrence's checklist off, or unticks it (SPEC.md §2, Checkliste). With all its items
     * ticked off, the routine is done; unticking one of a routine done opens it again, the others still ticked off.
     * The checklist stays as it is once the occurrence was skipped or missed, or while its routine is set aside.
     */
    suspend fun tick(occurrenceId: Long, itemId: Long, ticked: Boolean) = write {
        database().withTransaction {
            val occurrence = dao.occurrence(occurrenceId)?.takeIf { it.status.checklistTickable } ?: return@withTransaction
            val routine = dao.get(occurrence.routineId)?.takeIf { it.status == RoutineStatus.Active } ?: return@withTransaction
            val items = dao.checklist(routine.id).map { it.id }
            if (itemId !in items) return@withTransaction
            val done = occurrence.status == OccurrenceStatus.Done
            settlingGoal(routine.id) {
                when {
                    ticked && !done -> {
                        dao.insertTicks(listOf(ChecklistTick(occurrenceId, itemId)))
                        tickOffIfAllTicked(occurrenceId, items)
                    }
                    // Done as a whole, every item counts as ticked off until one is unticked.
                    !ticked && done -> {
                        dao.insertTicks((items - itemId).map { ChecklistTick(occurrenceId, it) })
                        dao.deleteTick(occurrenceId, itemId)
                        reopen(occurrence, routine)
                    }
                    !ticked -> dao.deleteTick(occurrenceId, itemId)
                }
            }
        }
    }

    /** Puts the occurrence off ("Später"): it is reminded of again at [until]. */
    suspend fun putOff(occurrenceId: Long, until: Instant) = write { dao.putOff(occurrenceId, until) }

    /**
     * When the reminders have something to do next after [after]: remind of a routine, at its time or a
     * follow-up, or mark an occurrence missed, once its time window ends. None if nothing is ever due again.
     */
    suspend fun nextAlarm(after: Instant): Instant? = withContext(io) {
        val planned = dao.occurrencesToRemindFrom(firstDayStillOpen(after)).groupBy { it.routineId }
        dao.withStatus(RoutineStatus.Active).flatMap { routine ->
            val occurrences = planned[routine.id].orEmpty()
            listOfNotNull(routine.nextReminder(occurrences, after, clock.zone), routine.nextWindowEnd(occurrences, after, clock.zone))
        }.minOrNull()
    }

    /**
     * Marks the open occurrences whose time window has ended by now missed (SPEC.md §2, Tracking), after
     * planning the days since [since], so that the days the phone was off count too. Not reported as a
     * change: the reminders do this themselves, and take their own down then.
     */
    suspend fun markMissed(since: Instant) = withContext(io + NonCancellable) {
        val now = now()
        planDays(from = today(since, clock.zone), to = today(now, clock.zone))
        database().withTransaction {
            val missed = dao.allOpen(OccurrenceStatus.Open).filter { (occurrence, routine) ->
                routine.windowEnded(occurrence, now, clock.zone)
            }
            // A day missed now may undo a streak reached by another time that day, and so open its goal again.
            val parts = missed.map { it.routine.id }.distinct().associateWith { partOf(it) }
            dao.setStatuses(missed.map { it.occurrence.id }, OccurrenceStatus.Missed)
            parts.forEach { (routineId, part) -> settleGoals(part, partOf(routineId)) }
        }
    }

    /**
     * The open occurrences to remind of after [after] and up to [until], at their time or a follow-up before
     * their time window ends; the days in between are planned first.
     *
     * One bound to places (SPEC.md §4) pops up only where [locate] says the device is at one of them; elsewhere it
     * waits, and its follow-ups, until it has popped up once, only look again: it pops up at one of them should its
     * arrival have gone unnoticed. [locate] is only asked when that matters, about the places that do. Not reported as
     * a change: the reminders do this themselves.
     */
    suspend fun remindersDue(after: Instant, until: Instant, locate: Locate): List<Reminder> =
        withContext(io + NonCancellable) {
            val last = today(until, clock.zone)
            planDays(from = today(after, clock.zone), to = last)
            val due = dao.openToRemind(firstDayStillOpen(after), last, OccurrenceStatus.Open)
                .mapNotNull { open ->
                    val times = open.routine.reminderTimes(open.occurrence, clock.zone)
                    times.firstOrNull { it.isAfter(after) && !it.isAfter(until) }?.let { open to it }
                }
                .sortedWith(
                    compareBy<Pair<OccurrenceOfRoutine, Instant>> { (_, time) -> time }
                        .thenBy(InputText.byName) { (open) -> open.routine.name },
                )
            val (held, free) = due.partition { (open, time) -> open.isHeldForPlaces(time, clock.zone) }
            val whereabouts = if (held.isEmpty()) Whereabouts.Unknown else locate(held.flatMap { (open) -> open.placeIds }.toSet())
            val (popping, waiting) = held.partition { (open) -> whereabouts.allows(open.placeIds) }
            dao.setWaiting(waiting.map { (open) -> open.occurrence.id }, waiting = true)
            dao.setWaiting(popping.map { (open) -> open.occurrence.id }, waiting = false)
            val shown = (free + popping).map { (open) -> open.occurrence.id }.toSet()
            due.filter { (open) -> open.occurrence.id in shown }.map { (open) -> reminderOf(open) }
        }

    /**
     * Arrived at a place (SPEC.md §4): the occurrences waiting for one of the places [locate] says the device is at,
     * their time window still open, are to pop up now and wait no more, as [remindersDue] then has them; their
     * follow-ups count from now. Returns them. [locate] is only asked while something waits, about the places it waits
     * for. Not reported as a change: the reminders do this themselves.
     */
    suspend fun arrive(locate: Locate): List<Reminder> = withContext(io + NonCancellable) {
        val waiting = dao.openWaiting()
        if (waiting.isEmpty()) return@withContext emptyList()
        val whereabouts = locate(waiting.flatMap { it.placeIds }.toSet())
        popUp(waiting.filter { whereabouts.allows(it.placeIds) })
    }

    /**
     * The occurrences waiting for places their routine is bound to no more, as it was changed or its places were
     * deleted, are to pop up now, as on arrival. Not reported as a change: the reminders do this themselves.
     */
    suspend fun releaseFromPlaces() = withContext(io + NonCancellable) {
        popUp(dao.openWaiting().filter { it.placeIds.isEmpty() })
    }

    /** Those of [waiting] whose time window is still open pop up now, by name. */
    private suspend fun popUp(waiting: List<OccurrenceOfRoutine>): List<Reminder> {
        val now = now()
        val popping = waiting.filter { !it.routine.windowEnded(it.occurrence, now, clock.zone) }
        dao.popUp(popping.map { it.occurrence.id }, at = now)
        return popping.sortedWith(compareBy(InputText.byName) { it.routine.name }).map(::reminderOf)
    }

    /** Of [occurrenceIds], the ones to remind of now, as [Routine.isDue] has it: their reminders may stay, as they are now. */
    suspend fun stillDue(occurrenceIds: Collection<Long>): List<Reminder> = withContext(io) {
        val now = now()
        dao.occurrencesWithRoutines(occurrenceIds).filter { it.isDue(now) }.map(::reminderOf)
    }

    /** The occurrence's reminder as it changes while it is still due, as [stillDue] has it; none once it is not. */
    fun reminder(occurrenceId: Long): Flow<Reminder?> = flow {
        emitAll(dao.observeOccurrenceWithRoutine(occurrenceId).map { it?.takeIf { it.isDue(now()) }?.let(::reminderOf) })
    }.flowOn(io)

    private fun OccurrenceOfRoutine.isDue(now: Instant) = routine.isDue(occurrence, now, clock.zone)

    private fun reminderOf(of: OccurrenceOfRoutine): Reminder {
        val (occurrence, routine) = of
        val dueAt = dueAt(occurrence.date, occurrence.time, clock.zone)
        return Reminder(occurrence.id, routine.name, dueAt, routine.fullScreen, routine.windowEnd(occurrence, clock.zone))
    }

    private suspend fun setStatus(occurrenceId: Long, status: OccurrenceStatus) = write {
        database().withTransaction {
            val occurrence = dao.occurrence(occurrenceId) ?: return@withTransaction
            settlingGoal(occurrence.routineId) { dao.setStatus(occurrenceId, status) }
        }
    }

    /**
     * The routine as a part of its goal (SPEC.md §6): counting with a target, and open while it has not reached that; one
     * without a target only shows with its goal.
     */
    private suspend fun partOf(routineId: Long): GoalPart {
        val routine = dao.get(routineId) ?: return GoalPart.None
        if (routine.goalId == null || routine.target == null) return GoalPart(routine.goalId, open = false, counts = false)
        val reached = GoalRoutine.of(RoutineWithOutcomes(routine, dao.outcomesOf(routineId)))?.reached ?: false
        return GoalPart(routine.goalId, open = !reached)
    }

    /**
     * Runs [change] to occurrences of the routine [routineId], then settles the goal it is part of (SPEC.md §6), as
     * reaching its target, or no more, may finish that goal or open it again.
     */
    private suspend fun settlingGoal(routineId: Long, change: suspend () -> Unit) {
        val part = partOf(routineId)
        change()
        settleGoals(part, partOf(routineId))
    }

    private suspend fun settleGoals(before: GoalPart, after: GoalPart) = database().settleGoals(before, after, today())

    /** Opens the [routine]'s occurrence again, as [reopen] describes. */
    private suspend fun reopen(occurrence: Occurrence, routine: Routine) {
        dao.setStatus(occurrence.id, OccurrenceStatus.Open)
        replan(routine, occurrence.date, dao.occurrencesOn(routine.id, occurrence.date), now())
    }

    /** Ticks the open occurrence off once every one of the checklist's [items] is ticked off on it. */
    private suspend fun tickOffIfAllTicked(occurrenceId: Long, items: List<Long>) {
        if (items.isNotEmpty() && dao.ticks(occurrenceId).containsAll(items)) dao.setStatus(occurrenceId, OccurrenceStatus.Done)
    }

    /** Binds the routine to its places; bound to none, what waits for them pops up with [releaseFromPlaces]. */
    private suspend fun savePlaces(routineId: Long, placeIds: Set<Long>) {
        dao.unbindAll(routineId)
        dao.bind(routineId, placeIds)
    }

    private suspend fun saveTags(routineId: Long, tagIds: Set<Long>) {
        dao.untagAll(routineId)
        dao.tag(routineId, tagIds)
    }

    /**
     * Saves the routine's checklist as [items] has it, in that order: an item with the id of one saved changes
     * it and keeps its ticks; the others are new, and the saved ones not among them go. An open occurrence with
     * all that is left ticked off is done then.
     */
    private suspend fun saveChecklist(routineId: Long, items: List<ChecklistDraftItem>) {
        val unclaimed = dao.checklist(routineId).map { it.id }.toMutableSet()
        val checklist = items.mapIndexed { position, item ->
            ChecklistItem(id = item.id.takeIf(unclaimed::remove) ?: 0, routineId = routineId, position = position, text = item.text)
        }
        val (saved, new) = checklist.partition { it.id != 0L }
        dao.deleteChecklistItemsBut(routineId, kept = saved.map { it.id })
        dao.updateChecklistItems(saved)
        dao.insertChecklistItems(new)
        val ids = dao.checklist(routineId).map { it.id }
        dao.openOccurrences(routineId).forEach { tickOffIfAllTicked(it.id, ids) }
    }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block().also { onChange() } }

    /** The local date now; it changes at midnight in the phone's current time zone. */
    fun today(): LocalDate = today(clock.instant(), clock.zone)

    /** How long until the next local day begins. */
    fun timeUntilTomorrow(): Duration = Duration.between(clock.instant(), today().plusDays(1).atStartOfDay(clock.zone))

    private fun now(): Instant = clock.instant()

    /** The first day with an occurrence whose time window may still be open after [after]: it can run into the next day. */
    private fun firstDayStillOpen(after: Instant): LocalDate = today(after - TimeWindow.LONGEST, clock.zone)

    private suspend fun planDays(from: LocalDate, to: LocalDate) =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.forEach { plan(it) }

    /** Plans the occurrences on [date] of the routines that have none then yet, at their times from when they were planned. */
    private suspend fun plan(date: LocalDate) = database().withTransaction {
        val occurrences = dao.unplannedOn(date).flatMap { routine ->
            routine.plannedTimesOn(date, clock.zone).map { routine.occurrenceAt(date, it) }
        }
        dao.insertOccurrences(occurrences)
    }

    /** Brings the routine's [occurrences] on [date] in line with its times from [now] on. */
    private suspend fun replan(routine: Routine, date: LocalDate, occurrences: List<Occurrence>, now: Instant) {
        val changes = replanDay(occurrences, routine.timesOn(date)) { isDueAtOrAfter(now, date, it, clock.zone) }
        changes.moved.forEach { (id, time) -> dao.moveOccurrence(id, time) }
        dao.deleteOccurrences(changes.removed)
        dao.insertOccurrences(changes.added.map { routine.occurrenceAt(date, it) })
    }

    private fun checked(draft: RoutineDraft): RoutineDraft {
        require(draft.problems.isEmpty()) { "Invalid routine: ${draft.problems}" }
        return draft.cleaned()
    }
}

/** Where the device is, as far as the places with the ids given go (SPEC.md §4). */
typealias Locate = suspend (placeIds: Set<Long>) -> Whereabouts
