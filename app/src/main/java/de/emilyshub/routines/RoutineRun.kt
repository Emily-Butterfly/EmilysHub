package de.emilyshub.routines

import de.emilyshub.stats.RoutineHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.Serializable
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A run through the checklist of an occurrence of the routine [routineId] (SPEC.md §2, Ablauf-Modus): [startedAt], the items [tickedHere]
 * in the order "Fertig" ticked them off, for "Schritt zurück" to take back, and when the last of them did the routine
 * ([finishedAt]). It holds nothing of the checklist itself, which stays the occurrence's, so that Heute and the run
 * always agree. A value, kept as Android keeps a screen's state, so that it outlives rotation, the lock and the process.
 */
data class Run(
    val occurrenceId: Long,
    val routineId: Long,
    val startedAt: Instant,
    val tickedHere: List<Long> = emptyList(),
    val finishedAt: Instant? = null,
) : Serializable {

    /** Whether "Schritt zurück" has something to take back at [step]: an item ticked off in the run, still ticked off. */
    fun canGoBack(step: RunView.Step): Boolean = lastTicked(step) != null

    /** How long it took, until it was finished, or until [now] while it is not. */
    fun took(now: Instant): Duration = Duration.between(startedAt, finishedAt ?: now)

    /** The item "Schritt zurück" unticks at [step]: the last ticked off in the run that still is. */
    internal fun lastTicked(step: RunView.Step): Long? {
        val ticked = step.checklist.filter { it.ticked }.map { it.itemId }.toSet()
        return tickedHere.lastOrNull { it in ticked }
    }

    private companion object {
        /** Kept runs read back as long as what is in them is the same. */
        private const val serialVersionUID = 1L
    }
}

/** What a run shows, as its occurrence stands (SPEC.md §2, Ablauf-Modus). */
sealed interface RunView {

    /**
     * Step [number] of [count]: the routine's [name], and its first [item] not ticked off, of its whole [checklist]
     * as it stands; what is ticked off already is passed over.
     */
    data class Step(val name: String, val item: ChecklistEntry, val number: Int, val count: Int, val checklist: List<ChecklistEntry>) :
        RunView {
        /** Whether ticking [item] off does the routine: the only one left. */
        val last: Boolean get() = checklist.count { !it.ticked } == 1
    }

    /** The routine done, its [name] and its [streak] of days done in a row now. */
    data class Done(val name: String, val streak: Int) : RunView

    /** The occurrence ended otherwise while the run was open, and [why]: there is nothing left to run through. */
    data class Ended(val why: RunEnd) : RunView
}

/** Why a run ended before its routine was done (SPEC.md §2, Ablauf-Modus). */
sealed interface RunEnd {
    /** Its time window is over. */
    data object Missed : RunEnd

    /** Skipped, on Heute or from its reminder. */
    data object Skipped : RunEnd

    /** Done by another routine of its group, the one [by] (SPEC.md §2, Gekoppelte Routinen). */
    data class Covered(val by: String) : RunEnd

    /** Dropped for the day ("Heute nicht"). */
    data object Dropped : RunEnd

    /** Its routine paused. */
    data object Paused : RunEnd

    /** Its routine archived. */
    data object Archived : RunEnd

    /** Its routine deleted, and the occurrence with it, or one with no checklist left to run through. */
    data object Gone : RunEnd
}

/**
 * The Ablauf-Modus (SPEC.md §2): runs through the checklist of an open occurrence of a routine, one step at a time,
 * ticking off through [Routines.tick], as Heute does. A run has no data of its own but what [Run] keeps.
 */
class RoutineRuns(private val routines: Routines, private val clock: Clock) {

    /** A run through the occurrence's checklist, started now; one started before is taken up where its checklist is. */
    fun start(occurrenceId: Long, routineId: Long): Run = Run(occurrenceId, routineId, startedAt = clock.instant())

    /** What the [run] shows, anew as its occurrence, its routine and its history change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun view(run: Run): Flow<RunView> = routines.forRun(run.occurrenceId).flatMapLatest { occurrence ->
        when {
            // Setting a routine aside takes its open occurrences away.
            occurrence == null -> flow { emit(RunView.Ended(goneBecause(routines.get(run.routineId)))) }
            occurrence.occurrence.status == OccurrenceStatus.Done -> routines.history(occurrence.routine.id).map {
                RunView.Done(occurrence.routine.name, RoutineHistory(it).streaks().current)
            }
            else -> untilWindowEnds(occurrence)
        }
    }

    /**
     * What the open [occurrence] shows, and once its time window ends, that it was missed, though nothing marked it so
     * yet: the alarm that does may come later.
     */
    private fun untilWindowEnds(occurrence: RunOccurrence): Flow<RunView> = flow {
        val view = viewOf(occurrence)
        emit(view)
        if (view !is RunView.Step) return@flow
        val end = occurrence.routine.windowEnd(occurrence.occurrence, clock.zone)
        delay(Duration.between(clock.instant(), end).toMillis().coerceAtLeast(0))
        emit(RunView.Ended(RunEnd.Missed))
    }

    /** "Fertig": ticks the [step] off, and the run moves on; with the last of them, the routine is done. */
    suspend fun done(run: Run, step: RunView.Step): Run {
        // Tapped again before the next step shows.
        if (step.item.itemId in run.tickedHere) return run
        routines.tick(run.occurrenceId, step.item.itemId, ticked = true)
        return run.copy(tickedHere = run.tickedHere + step.item.itemId, finishedAt = if (step.last) clock.instant() else null)
    }

    /** "Schritt zurück": unticks the item last ticked off in the run that still is, at [step]; nothing if there is none. */
    suspend fun back(run: Run, step: RunView.Step): Run {
        val item = run.lastTicked(step) ?: return run
        routines.tick(run.occurrenceId, item, ticked = false)
        return run.copy(tickedHere = run.tickedHere.take(run.tickedHere.lastIndexOf(item)))
    }

    /** Why the occurrence of [routine] is gone: it was set aside, or deleted with it. */
    private fun goneBecause(routine: Routine?): RunEnd = when (routine?.status) {
        RoutineStatus.Paused -> RunEnd.Paused
        RoutineStatus.Archived -> RunEnd.Archived
        RoutineStatus.Active, null -> RunEnd.Gone
    }

    /** What an occurrence not done shows: its next step while it is open, why it ended otherwise. */
    private fun viewOf(open: RunOccurrence): RunView {
        val (occurrence, routine, checklist) = open
        val why = when {
            routine.status == RoutineStatus.Paused -> RunEnd.Paused
            routine.status == RoutineStatus.Archived -> RunEnd.Archived
            open.coveredBy != null -> RunEnd.Covered(open.coveredBy)
            occurrence.status == OccurrenceStatus.Skipped -> RunEnd.Skipped
            occurrence.status == OccurrenceStatus.Dropped -> RunEnd.Dropped
            occurrence.status == OccurrenceStatus.Missed -> RunEnd.Missed
            // Over, though the alarm that marks it missed has not come yet.
            routine.windowEnded(occurrence, clock.instant(), clock.zone) -> RunEnd.Missed
            else -> null
        }
        if (why != null) return RunView.Ended(why)
        val next = checklist.indexOfFirst { !it.ticked }
        if (next < 0) return RunView.Ended(RunEnd.Gone)
        return RunView.Step(routine.name, checklist[next], number = next + 1, count = checklist.size, checklist = checklist)
    }
}

/**
 * An occurrence as a run sees it: with its routine, its [checklist] as it stands on it, and the name of the routine of
 * its group whose being done skipped it, if one did ([coveredBy]).
 */
data class RunOccurrence(
    val occurrence: Occurrence,
    val routine: Routine,
    val checklist: List<ChecklistEntry>,
    val coveredBy: String? = null,
)
