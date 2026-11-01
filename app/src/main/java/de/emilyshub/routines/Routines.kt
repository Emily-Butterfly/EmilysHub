package de.emilyshub.routines

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.places.Whereabouts
import de.emilyshub.reminders.Tone
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
 * that was already saved. After each write, [onChange] runs, for the reminders to follow. Every change of an
 * occurrence's status goes through [OccurrenceLifecycle], which keeps coupling and goals in step with it.
 */
class Routines(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val onChange: suspend () -> Unit = {},
) {
    private val dao get() = database().routines()

    /** Where an occurrence's status changes, with what hangs on it: coupling and goals. */
    private val lifecycle = OccurrenceLifecycle(database, clock)

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
                saveCoupling(id, coupling = null, cleaned.coupledWith)
                plan(today())
                lifecycle.settleCreated(id)
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

    /** The other routines of the group the routine is coupled with (SPEC.md §2), paused and archived ones too. */
    suspend fun coupledWith(routineId: Long): Set<Long> = withContext(io) { othersCoupledWith(routineId) }

    /**
     * The tones the routines have (SPEC.md §3), each once, for their reminders' channels; archived ones too, as they may
     * come back, unlike a to-do once done.
     */
    suspend fun tones(): Set<Tone> = withContext(io) { dao.tones().toSet() }

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
            val today = today()
            // Today's list as it was before the change, for the change to apply to.
            plan(today)
            val now = now()
            val rescheduled = changes.times != routine.times || changes.repetition != routine.repetition
            val goalId = database().wishes().stillThere(changes.goalId)
            val plannedFrom = if (rescheduled) now else routine.plannedFrom
            val changed = changes.copy(goalId = goalId).cleaned().toRoutine(id, plannedFrom, routine.status)
            lifecycle.settling(listOf(id)) {
                dao.update(changed.copy(coupling = routine.coupling))
                saveChecklist(id, changes.checklist)
                saveTags(id, changes.tagIds)
                savePlaces(id, changes.placeIds)
                saveCoupling(id, routine.coupling, changes.coupledWith)
            }
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
    suspend fun restore(id: Long) = reactivate(id, endingBefore = today())

    private suspend fun setAside(id: Long, status: RoutineStatus) = write {
        database().withTransaction {
            val routine = dao.get(id) ?: return@withTransaction
            val now = now()
            val (ended, open) = dao.openOccurrences(id).partition { routine.windowEnded(it, now, clock.zone) }
            lifecycle.move(ended.map { it.id }, OccurrenceStatus.Missed)
            dao.deleteOccurrences(open.map { it.id })
            dao.setRoutineStatus(id, status)
        }
    }

    /** Active again from now on; an end before [endingBefore] is dropped, so that it does not end again right away. */
    private suspend fun reactivate(id: Long, endingBefore: LocalDate? = null) = write {
        database().withTransaction {
            val now = now()
            dao.reactivate(id, from = now)
            val reactivated = dao.get(id) ?: return@withTransaction
            val routine = if (endingBefore != null && reactivated.endsOn?.isBefore(endingBefore) == true) {
                reactivated.copy(endsOn = null).also { dao.update(it) }
            } else {
                reactivated
            }
            val today = today()
            // Today may have occurrences of it from before, ticked off: the times still ahead are added to them.
            replan(routine, today, dao.occurrencesOn(id, today), now)
        }
    }

    /** Deletes the routine with all its occurrences; its goal is settled without it. */
    suspend fun delete(id: Long) = write {
        database().withTransaction {
            val coupling = dao.get(id)?.coupling
            lifecycle.delete(id)
            coupling?.let { dissolveIfAlone(it) }
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
            // One done that skipped others is due the same day, so it is among them.
            val names = all.associate { it.occurrence.id to it.routine.name }
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
                        endsOn = routine.endsOn,
                        coveredBy = occurrence.coveredBy?.let(names::get),
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
    suspend fun dropForToday(occurrenceId: Long) = write { database().withTransaction { drop(occurrenceId) } }

    /** "Heute nicht" for the routine of the occurrence, as [dropForToday] has it. */
    private suspend fun drop(occurrenceId: Long) {
        // From an occurrence still open only: one answered meanwhile, say in the app, leaves the day as it is.
        val occurrence = dao.occurrence(occurrenceId)?.takeIf { it.status == OccurrenceStatus.Open } ?: return
        val routine = dao.get(occurrence.routineId)?.takeIf { it.status == RoutineStatus.Active } ?: return
        val open = onHeute(routine).filter { it.status == OccurrenceStatus.Open }
        lifecycle.move(open.map { it.id }, OccurrenceStatus.Dropped)
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
            // One skipped by another of its group comes back with that one undone.
            val occurrence = dao.occurrence(occurrenceId)
                ?.takeIf { it.status != OccurrenceStatus.Missed && it.coveredBy == null } ?: return@withTransaction
            val routine = dao.get(occurrence.routineId)?.takeIf { it.status == RoutineStatus.Active } ?: return@withTransaction
            when (occurrence.status) {
                OccurrenceStatus.Dropped -> undoDrop(routine)
                OccurrenceStatus.Done -> {
                    dao.deleteTicks(occurrenceId)
                    reopen(occurrence, routine)
                }
                else -> reopen(occurrence, routine)
            }
        }
    }

    /**
     * Undoes "Heute nicht" of the [routine]: what it dropped today and yesterday is open again while its time window is
     * open, and missed once that has ended, as it would have been without.
     */
    private suspend fun undoDrop(routine: Routine) {
        val today = today()
        val dropped = (dao.occurrencesOn(routine.id, today.minusDays(1)) + dao.occurrencesOn(routine.id, today))
            .filter { it.status == OccurrenceStatus.Dropped }
        val (ended, open) = dropped.partition { routine.windowEnded(it, now(), clock.zone) }
        lifecycle.move(ended.map { it.id }, OccurrenceStatus.Missed)
        lifecycle.move(open.map { it.id }, OccurrenceStatus.Open)
        // Only once none is dropped any more, so that a time planned anew comes open.
        open.map { it.date }.distinct().forEach { date -> replan(routine, date, dao.occurrencesOn(routine.id, date), now()) }
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
            lifecycle.missNow(now)
            archiveEnded()
        }
    }

    /**
     * Archives the routines that have ended (SPEC.md §2): their last day is over, and nothing of it is still open, as
     * one whose time window runs past midnight may be; paused ones too. Their history stays, as for one archived by hand.
     */
    private suspend fun archiveEnded() {
        val today = today()
        (dao.withStatus(RoutineStatus.Active) + dao.withStatus(RoutineStatus.Paused))
            .filter { routine -> routine.endsOn?.isBefore(today) == true && dao.openOccurrences(routine.id).isEmpty() }
            .forEach { dao.setRoutineStatus(it.id, RoutineStatus.Archived) }
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
            joined(due.filter { (open) -> open.occurrence.id in shown }.map { (open) -> open })
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
        return joined(popping.sortedWith(compareBy(InputText.byName) { it.routine.name }))
    }

    /**
     * Of [occurrenceIds], the ones to remind of now, as [Routine.isDue] has it: their reminders may stay, as they are
     * now, each together with those coupled with it due at the same time as well, also of one no longer due itself.
     */
    suspend fun stillDue(occurrenceIds: Collection<Long>): List<Reminder> = withContext(io) {
        val now = now()
        val shown = dao.occurrencesWithRoutines(occurrenceIds)
        // Of one no longer due, say skipped on Heute, the others its joint reminder reminded of stay too.
        val due = shown.filter { it.isDue(now) } + shown.flatMap { dueTogetherWith(it, now) }
        joined(due.distinctBy { it.occurrence.id })
    }

    /** The occurrence's reminder as it changes while it is still due, as [stillDue] has it; none once it is not. */
    fun reminder(occurrenceId: Long): Flow<Reminder?> = flow {
        val reminders = dao.observeOccurrenceWithRoutine(occurrenceId).map { open ->
            open?.takeIf { it.isDue(now()) }?.let { joined(listOf(it) + dueTogetherWith(it, now())).firstOrNull() }
        }
        emitAll(reminders)
    }.flowOn(io)

    /**
     * Skips the occurrence and those coupled with it in their joint reminder, all at once (SPEC.md §3). One ticked off
     * meanwhile, say on Heute while the reminder still showed, takes along what it skipped of the reminder by being done,
     * so that all it reminded of are skipped, and none is left skipped by one no longer done.
     */
    suspend fun skipTogether(occurrenceId: Long) = write {
        database().withTransaction {
            lifecycle.move(togetherWith(occurrenceId) + coveredAtItsTime(occurrenceId), OccurrenceStatus.Skipped)
        }
    }

    /** What the occurrence, done, skipped of its group due at the same time: those its joint reminder reminded of too. */
    private suspend fun coveredAtItsTime(occurrenceId: Long): List<Long> {
        val done = dao.occurrencesWithRoutines(listOf(occurrenceId)).singleOrNull() ?: return emptyList()
        val coupling = done.routine.coupling ?: return emptyList()
        return dao.coupledOn(coupling, done.occurrence.date).map { it.occurrence }
            .filter { it.coveredBy == occurrenceId && it.time == done.occurrence.time }
            .map { it.id }
    }

    /** "Heute nicht" for the routines of the occurrence and those coupled with it in their joint reminder, at once. */
    suspend fun dropTogether(occurrenceId: Long) = write {
        database().withTransaction { togetherWith(occurrenceId).forEach { drop(it) } }
    }

    /** Puts the occurrence and those coupled with it in their joint reminder off ("Später") until [until], at once. */
    suspend fun putOffTogether(occurrenceId: Long, until: Instant) = write {
        database().withTransaction { togetherWith(occurrenceId).forEach { dao.putOff(it, until) } }
    }

    /**
     * The occurrence and those coupled with it due with it now, which its joint reminder reminds of; only it, if none.
     * None once it is gone.
     */
    private suspend fun togetherWith(occurrenceId: Long): List<Long> {
        val open = dao.occurrencesWithRoutines(listOf(occurrenceId)).singleOrNull() ?: return emptyList()
        return listOf(occurrenceId) + dueTogetherWith(open, now()).map { it.occurrence.id }
    }

    /** The open occurrences of the other active routines coupled with [open]'s due on the same day at the same time. */
    private suspend fun coupledAtTheSameTime(open: OccurrenceOfRoutine): List<OccurrenceOfRoutine> {
        val coupling = open.routine.coupling ?: return emptyList()
        val (occurrence) = open
        return dao.coupledOn(coupling, occurrence.date).filter {
            it.occurrence.id != occurrence.id && it.occurrence.time == occurrence.time && it.occurrence.status == OccurrenceStatus.Open
        }
    }

    /** Those [coupledAtTheSameTime] with [open] that are to be reminded of now too: not those waiting for a place. */
    private suspend fun dueTogetherWith(open: OccurrenceOfRoutine, now: Instant) =
        coupledAtTheSameTime(open).filter { it.isDue(now) && !it.occurrence.waiting }

    /**
     * The reminders of [due], in their order: one for each, but one for those of coupled routines due at the same time,
     * posted for the first of them (SPEC.md §3).
     */
    private fun joined(due: List<OccurrenceOfRoutine>): List<Reminder> {
        val jointKey = { open: OccurrenceOfRoutine -> open.routine.coupling?.let { Triple(it, open.occurrence.date, open.occurrence.time) } }
        val groups = due.filter { jointKey(it) != null }.groupBy(jointKey)
        return due.mapNotNull { open ->
            val group = groups[jointKey(open)]?.sortedBy { it.occurrence.id } ?: return@mapNotNull reminderOf(open)
            if (group.first().occurrence.id != open.occurrence.id) return@mapNotNull null
            val others = group.drop(1)
            reminderOf(open).copy(
                // One with a full-screen alarm makes it one; "Später" comes back before any of their windows end.
                fullScreen = group.any { it.routine.fullScreen },
                windowEnd = group.minOf { it.routine.windowEnd(it.occurrence, clock.zone) },
                together = others.map { JointOccurrence(it.occurrence.id, it.routine.name, it.routine.nameOnLockScreen) },
            )
        }
    }

    private fun OccurrenceOfRoutine.isDue(now: Instant) = routine.isDue(occurrence, now, clock.zone)

    private fun reminderOf(of: OccurrenceOfRoutine): Reminder {
        val (occurrence, routine) = of
        val dueAt = dueAt(occurrence.date, occurrence.time, clock.zone)
        val windowEnd = routine.windowEnd(occurrence, clock.zone)
        return Reminder(occurrence.id, routine.name, dueAt, routine.fullScreen, windowEnd, routine.nameOnLockScreen, routine.tone)
    }

    private suspend fun setStatus(occurrenceId: Long, status: OccurrenceStatus) = write {
        database().withTransaction { lifecycle.move(listOf(occurrenceId), status) }
    }

    /**
     * Couples the routine with [wanted] (SPEC.md §2), its group up to then [coupling]: those of them that have one time
     * a day, as it then has, make its group, taken out of any other they were in. Those of its group no longer wanted
     * are out of it; a group left with one routine is none.
     */
    private suspend fun saveCoupling(routineId: Long, coupling: Long?, wanted: Set<Long>) {
        val members = (wanted - routineId).filter { dao.get(it)?.times?.size == 1 }.toSet()
        val before = coupling?.let { dao.inCoupling(it).toSet() }.orEmpty()
        if (members.isEmpty()) {
            dao.setCoupling(listOf(routineId), null)
            coupling?.let { dissolveIfAlone(it) }
            return
        }
        val group = coupling ?: ((dao.lastCoupling() ?: 0) + 1)
        val left = members.mapNotNull { dao.get(it)?.coupling }.filter { it != group }.toSet()
        dao.setCoupling(before - members - routineId, null)
        dao.setCoupling(members + routineId, group)
        left.forEach { dissolveIfAlone(it) }
    }

    /** The group [coupling] is none once one routine is left in it. */
    private suspend fun dissolveIfAlone(coupling: Long) {
        val members = dao.inCoupling(coupling)
        if (members.size < 2) dao.setCoupling(members, null)
    }

    private suspend fun othersCoupledWith(routineId: Long): Set<Long> {
        val coupling = dao.get(routineId)?.coupling ?: return emptySet()
        return dao.inCoupling(coupling).toSet() - routineId
    }

    /**
     * Opens the [routine]'s occurrence again, as [reopen] describes, and what it skipped of its group as it was done:
     * open again, or missed with it once none of the group can be done that day any more, as [markMissed] has it.
     */
    private suspend fun reopen(occurrence: Occurrence, routine: Routine) {
        lifecycle.move(listOf(occurrence.id), OccurrenceStatus.Open)
        replan(routine, occurrence.date, dao.occurrencesOn(routine.id, occurrence.date), now())
    }

    /** Ticks the open occurrence off once every one of the checklist's [items] is ticked off on it. */
    private suspend fun tickOffIfAllTicked(occurrenceId: Long, items: List<Long>) {
        if (items.isNotEmpty() && dao.ticks(occurrenceId).containsAll(items)) lifecycle.move(listOf(occurrenceId), OccurrenceStatus.Done)
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
        // A time added to a day the routine was dropped for ("Heute nicht") is dropped too.
        val status = if (occurrences.any { it.status == OccurrenceStatus.Dropped }) OccurrenceStatus.Dropped else OccurrenceStatus.Open
        dao.insertOccurrences(changes.added.map { routine.occurrenceAt(date, it).copy(status = status) })
    }

    private fun checked(draft: RoutineDraft): RoutineDraft {
        // Not whether it ends before today: the editor tells, as the day can pass while it saves.
        val problems = draft.problems
        require(problems.isEmpty()) { "Invalid routine: $problems" }
        return draft.cleaned()
    }
}

/** Where the device is, as far as the places with the ids given go (SPEC.md §4). */
typealias Locate = suspend (placeIds: Set<Long>) -> Whereabouts
