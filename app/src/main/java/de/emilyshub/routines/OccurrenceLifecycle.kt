package de.emilyshub.routines

import de.emilyshub.data.HubDatabase
import de.emilyshub.wishes.GoalWatch
import de.emilyshub.wishes.watchingGoals
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Where an occurrence's status changes (SPEC.md §2, §6), and the only place that writes one: whatever [Routines] does
 * to an occurrence, ticking it off, skipping, undoing, "Heute nicht", missing it, comes here as a [move], which keeps
 * what hangs on a status:
 *
 * - on a day one of a coupled group is done, what of the other active routines of the group is open or missed that day
 *   is skipped by it ("covered"), also once their time window is over, and one undone then comes back so too; one no
 *   longer done gives back what it covered, open again, to be missed as [missNow] has it;
 * - each routine touched, those of its group too, has the goal it is part of settled, once, as reaching its target, or
 *   no more, may finish that goal or open it again.
 *
 * It also has which open occurrences are missed now ([missNow]), and what a routine takes along when it is [delete]d.
 * It is the routines' way to the goal watch, for routines changed in the editor too ([settling], [settleCreated]).
 * Every call runs in the caller's transaction.
 */
internal class OccurrenceLifecycle(private val database: () -> HubDatabase, private val clock: Clock) {

    private val dao get() = database().routines()

    /**
     * Moves the occurrences [ids] to [status], as the class describes (SPEC.md §2, §6); one moved is skipped by no
     * other any more. Those gone are left out, and those missed too: once missed, an occurrence stays so.
     */
    suspend fun move(ids: Collection<Long>, status: OccurrenceStatus) {
        val moving = dao.occurrencesWithRoutines(ids.distinct()).filter { it.occurrence.status != OccurrenceStatus.Missed }
        if (moving.isEmpty()) return
        val movingIds = moving.map { it.occurrence.id }
        val days = moving.mapNotNull { (occurrence, routine) -> routine.coupling?.let { CoupledDay(it, occurrence.date) } }.distinct()
        settling(moving.map { it.routine.id } + days.flatMap { dao.inCoupling(it.coupling) }) {
            // No longer done, what they skipped of their group by being done comes back; asked of every one, not only
            // those done, so that one left skipped by an occurrence no longer done, as an older version did, is too.
            if (status != OccurrenceStatus.Done) dao.uncover(by = movingIds)
            dao.setStatuses(movingIds, status)
            days.forEach { cover(it) }
        }
    }

    /**
     * Covers, on [date], what is open or missed of the coupled routines while another of their group is done, as [move]
     * does: once occurrences were planned there, which come open, or a routine set aside is active again, whose
     * occurrence done there counts again.
     */
    suspend fun coverOn(date: LocalDate) {
        val couplings = dao.withStatus(RoutineStatus.Active).mapNotNull { it.coupling }.distinct()
        if (couplings.isEmpty()) return
        settling(couplings.flatMap { dao.inCoupling(it) }) { couplings.forEach { cover(CoupledDay(it, date)) } }
    }

    /**
     * Marks the open occurrences whose time window has ended by [now] missed (SPEC.md §2, Tracking); but one coupled
     * not while another of its group can still be done that day.
     */
    suspend fun missNow(now: Instant) {
        val missed = dao.allOpen(OccurrenceStatus.Open).filter { open ->
            open.routine.windowEnded(open.occurrence, now, clock.zone) && !stillCovered(open, now)
        }
        // A day missed now may undo a streak reached by another time that day, and so open its goal again.
        move(missed.map { it.occurrence.id }, OccurrenceStatus.Missed)
    }

    /**
     * Deletes the routine [routineId] with its occurrences, after giving back what they skipped of its group by being
     * done, as there is nothing to undo then; its goal is settled without it (SPEC.md §6).
     */
    suspend fun delete(routineId: Long) = watchingGoals { watch ->
        watch.routine(routineId)
        dao.uncoverAllBy(routineId = routineId)
        dao.delete(routineId)
    }

    /** Runs [change] to the routines [routineIds], then settles the goals they are part of (SPEC.md §6). */
    suspend fun <T> settling(routineIds: Collection<Long>, change: suspend () -> T): T = watchingGoals { watch ->
        routineIds.distinct().forEach { watch.routine(it) }
        change()
    }

    /** Settles the goal of the routine [routineId], just created, as a part new to it. */
    suspend fun settleCreated(routineId: Long) = watchingGoals { watch -> watch.routineAdded(routineId) }

    /**
     * On the [day] one of the group is done, what of its other active routines is open or missed is skipped by it
     * (SPEC.md §2), and reminds no more; nothing while none is done.
     */
    private suspend fun cover(day: CoupledDay) {
        val occurrences = dao.coupledOn(day.coupling, day.date).map { it.occurrence }
        val done = occurrences.firstOrNull { it.status == OccurrenceStatus.Done } ?: return
        val others = occurrences.filter { it.routineId != done.routineId && it.status in COVERABLE }
        dao.cover(others.map { it.id }, by = done.id)
    }

    /** A day of a coupled group, the [coupling]. */
    private data class CoupledDay(val coupling: Long, val date: LocalDate)

    /**
     * Whether the [open] occurrence, its time window over, can still be done for by another active routine coupled
     * with it (SPEC.md §2): one open on its day whose time window is not. Until none can, it is not missed.
     */
    private suspend fun stillCovered(open: OccurrenceOfRoutine, now: Instant): Boolean {
        val coupling = open.routine.coupling ?: return false
        return dao.coupledOn(coupling, open.occurrence.date).any { other ->
            other.occurrence.id != open.occurrence.id && other.occurrence.status == OccurrenceStatus.Open &&
                !other.routine.windowEnded(other.occurrence, now, clock.zone)
        }
    }

    /** Runs [change], which names the routines it changes to the watch, and settles their goals at the end (SPEC.md §6). */
    private suspend fun <T> watchingGoals(change: suspend (GoalWatch) -> T): T =
        database().watchingGoals(today(clock.instant(), clock.zone), change)

    private companion object {
        /** What one done covers of its group: missed too, as the day was done after all. */
        val COVERABLE = setOf(OccurrenceStatus.Open, OccurrenceStatus.Missed)
    }
}
