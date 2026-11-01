package de.emilyshub.routines

import de.emilyshub.data.HubDatabase
import de.emilyshub.wishes.GoalPart
import de.emilyshub.wishes.GoalRoutine
import de.emilyshub.wishes.settleGoals
import java.time.Clock
import java.time.Instant

/**
 * Where an occurrence's status changes (SPEC.md §2, §6), and the only place that writes one: whatever [Routines] does
 * to an occurrence, ticking it off, skipping, undoing, "Heute nicht", missing it, comes here as a [move], which keeps
 * what hangs on a status:
 *
 * - one done skips what of the other active routines coupled with its routine is open on its day ("covers" it), also
 *   once their time window is over, and one no longer done gives that back;
 * - each routine touched has the goal it is part of settled, once, as reaching its target, or no more, may finish that
 *   goal or open it again.
 *
 * It also has which open occurrences are missed now ([missNow]), and what a routine takes along when it is deleted
 * ([removing]). Every call runs in the caller's transaction.
 */
internal class OccurrenceLifecycle(private val database: () -> HubDatabase, private val clock: Clock) {

    private val dao get() = database().routines()

    /**
     * Moves the occurrences [ids] to [status], as the class describes; one moved is skipped by no other any more. Those
     * gone are left out, and those missed too: once missed, an occurrence stays so.
     */
    suspend fun move(ids: Collection<Long>, status: OccurrenceStatus) {
        val occurrences = ids.distinct().mapNotNull { dao.occurrence(it) }.filter { it.status != OccurrenceStatus.Missed }
        if (occurrences.isEmpty()) return
        settling(occurrences.map { it.routineId }) {
            // No longer done, what it skipped of its group by being done comes back.
            occurrences.filter { it.status == OccurrenceStatus.Done && status != OccurrenceStatus.Done }
                .forEach { dao.uncover(by = it.id) }
            dao.setStatuses(occurrences.map { it.id }, status)
            if (status == OccurrenceStatus.Done) occurrences.forEach { cover(it) }
        }
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
     * Runs [delete], which deletes the routine [routineId] with its occurrences, after giving back what they skipped
     * of its group by being done, as there is nothing to undo then; its goal is settled without it.
     */
    suspend fun removing(routineId: Long, delete: suspend () -> Unit) {
        val part = partOf(routineId)
        dao.uncoverAllBy(routineId = routineId)
        delete()
        settleGoals(part, GoalPart.None)
    }

    /** Runs [change] to the routines [routineIds], then settles the goals they are part of. */
    suspend fun <T> settling(routineIds: Collection<Long>, change: suspend () -> T): T {
        val parts = routineIds.distinct().associateWith { partOf(it) }
        return change().also { parts.forEach { (routineId, part) -> settleGoals(part, partOf(routineId)) } }
    }

    /** Settles the goal of the routine [routineId], just created, as a part new to it. */
    suspend fun settleCreated(routineId: Long) = settleGoals(GoalPart.None, partOf(routineId))

    /**
     * The [done] occurrence covers what of the other active routines coupled with its routine is open on its day
     * (SPEC.md §2): skipped by it, and reminding no more.
     */
    private suspend fun cover(done: Occurrence) {
        val coupling = dao.get(done.routineId)?.coupling ?: return
        val others = dao.coupledOn(coupling, done.date)
            .map { it.occurrence }
            .filter { it.routineId != done.routineId && it.status == OccurrenceStatus.Open }
        dao.cover(others.map { it.id }, by = done.id)
    }

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

    private suspend fun settleGoals(before: GoalPart, after: GoalPart) =
        database().settleGoals(before, after, today(clock.instant(), clock.zone))
}
