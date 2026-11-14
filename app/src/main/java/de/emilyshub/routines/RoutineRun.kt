package de.emilyshub.routines

import de.emilyshub.stats.RoutineHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A run through the checklist of an occurrence of the routine [routineId] (SPEC.md §2, Ablauf-Modus), as [RunStore]
 * keeps it: [startedAt], the step [stepItemId] that shows, since [stepStartedAt], whether its time running out has
 * [alerted], the items [tickedHere] in the order "Fertig" ticked them off, for "Schritt zurück" to take back, and when
 * the last of them did the routine ([finishedAt]). It holds nothing of the checklist itself, which stays the
 * occurrence's, so that Heute and the run always agree.
 */
data class Run(
    val occurrenceId: Long,
    val routineId: Long,
    val startedAt: Instant,
    val tickedHere: List<Long> = emptyList(),
    val finishedAt: Instant? = null,
    val stepItemId: Long? = null,
    val stepStartedAt: Instant = startedAt,
    val alerted: Boolean = false,
) {
    /** How long it took, until it was finished, or until [now] while it is not. */
    fun took(now: Instant): Duration = Duration.between(startedAt, finishedAt ?: now)

    /** The item "Schritt zurück" unticks with [checklist] as it stands: the last ticked off in the run that still is. */
    internal fun lastTicked(checklist: List<ChecklistEntry>): Long? {
        val ticked = checklist.filter { it.ticked }.map { it.itemId }.toSet()
        return tickedHere.lastOrNull { it in ticked }
    }

    /** At the step [item], shown since [at]: the time of a step begins when it shows. */
    internal fun at(item: Long, at: Instant) = copy(stepItemId = item, stepStartedAt = at, alerted = false)
}

/** What a run shows, as its occurrence stands (SPEC.md §2, Ablauf-Modus). */
sealed interface RunView {

    /**
     * Step [number] of [count]: the routine's [name], and its first [item] not ticked off, of its whole [checklist]
     * as it stands, what is ticked off already passed over; shown since [startedAt], the run since [runStartedAt].
     * [canGoBack] if "Schritt zurück" has something to take back; [nameOnLockScreen] if the routine allows its name there.
     */
    data class Step(
        val name: String,
        val item: ChecklistEntry,
        val number: Int,
        val count: Int,
        val checklist: List<ChecklistEntry>,
        val startedAt: Instant,
        val runStartedAt: Instant,
        val canGoBack: Boolean = false,
        val nameOnLockScreen: Boolean = false,
    ) : RunView {
        /** Whether ticking [item] off does the routine: the only one left. */
        val last: Boolean get() = left.size == 1

        /** What is left of the checklist, this step included. */
        val left: List<ChecklistEntry> get() = checklist.filter { !it.ticked }

        /** When the time of this step runs out; none without minutes. */
        val endsAt: Instant? get() = item.minutes?.let { startedAt + Duration.ofMinutes(it.toLong()) }

        /** How this step's time stands at [now]: running down, over, or without minutes the run's stopwatch. */
        fun time(now: Instant): StepTime {
            val end = endsAt ?: return StepTime.Stopwatch(Duration.between(runStartedAt, now))
            return if (now.isBefore(end)) {
                StepTime.Countdown(left = Duration.between(now, end), of = Duration.between(startedAt, end))
            } else {
                StepTime.Overtime(Duration.between(end, now))
            }
        }

        /**
         * How many steps are left at [now], this one included, and when they are done by, if all of them have minutes:
         * this one at its end, or now once its time is over, and the others one after the other.
         */
        fun outlook(now: Instant): Outlook {
            val end = endsAt
            val others = left.filter { it.itemId != item.itemId }
            if (end == null || others.any { it.minutes == null }) return Outlook(left.size, doneBy = null)
            val doneBy = maxOf(end, now) + Duration.ofMinutes(others.sumOf { it.minutes!!.toLong() })
            return Outlook(left.size, doneBy)
        }
    }

    /** The routine done, its [name], its [streak] of days done in a row now, and how long the run [took], if known. */
    data class Done(val name: String, val streak: Int, val took: Duration? = null) : RunView

    /** The occurrence ended otherwise while the run was open, and [why]: there is nothing left to run through. */
    data class Ended(val why: RunEnd) : RunView
}

/** How a step's time stands (SPEC.md §2, Ablauf-Modus, Zeit). */
sealed interface StepTime {
    /** With minutes, [left] of the step's time, which is [of]. */
    data class Countdown(val left: Duration, val of: Duration) : StepTime

    /** With minutes, how far [over] the step's time it is: it counts on until "Fertig". */
    data class Overtime(val over: Duration) : StepTime

    /** Without minutes, how long the run has [run], from its first start. */
    data class Stopwatch(val run: Duration) : StepTime
}

/** The [steps] left, and when they are done by, if all of them have minutes ([doneBy]). */
data class Outlook(val steps: Int, val doneBy: Instant?)

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

    /** Ended with "Beenden", or by starting another: the ticks stay as they are. */
    data object Stopped : RunEnd
}

/**
 * The Ablauf-Modus (SPEC.md §2): runs through the checklist of an open occurrence of a routine, one step at a time,
 * ticking off through [Routines.tick], as Heute does. One run at most, kept in [store], so that it outlives the app;
 * after each change of it, [onChange] runs, for its notification to follow.
 */
class RoutineRuns(
    private val routines: Routines,
    private val store: RunStore,
    private val clock: Clock,
    private val onChange: suspend () -> Unit = {},
) {
    /**
     * The run running through another occurrence than [occurrenceId], whose occurrence is still open, which starting a run
     * through [occurrenceId] would end (SPEC.md §2: one at a time); none if there is none.
     */
    suspend fun other(occurrenceId: Long): Run? {
        val run = store.current()?.takeIf { it.occurrenceId != occurrenceId && it.finishedAt == null } ?: return null
        return run.takeIf { view(run.occurrenceId, run.routineId).first() is RunView.Step }
    }

    /**
     * A run through the occurrence's checklist, started now, in the place of any other; one started before is taken up
     * where its checklist is, its stopwatch counting on from its first start. A run still running through it goes on as
     * it is, its step's time too; what one ended before ticked off, "Schritt zurück" leaves.
     */
    suspend fun start(occurrenceId: Long, routineId: Long) {
        val running = store.current()?.takeIf { it.occurrenceId == occurrenceId && it.finishedAt == null }
        if (running == null) {
            val startedAt = routines.startRun(occurrenceId)
            store.replace(Run(occurrenceId, routineId, startedAt, stepStartedAt = clock.instant()))
        }
        onChange()
    }

    /** The run kept, running or finished; none while none is. */
    fun running(): Flow<Run?> = store.observe()

    /** The run kept, running or finished; none while none is. */
    suspend fun current(): Run? = store.current()

    /**
     * What the run through the occurrence of [routineId] shows, anew as the run, its occurrence, its routine and its
     * history change, and as its time window ends.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun view(occurrenceId: Long, routineId: Long): Flow<RunView> =
        combine(store.observe(), routines.forRun(occurrenceId)) { run, occurrence ->
            run?.takeIf { it.occurrenceId == occurrenceId } to occurrence
        }.flatMapLatest { (run, occurrence) ->
            when {
                // Setting a routine aside takes its open occurrences away.
                occurrence == null -> flow { emit(RunView.Ended(goneBecause(routines.get(routineId)))) }
                occurrence.occurrence.status == OccurrenceStatus.Done -> routines.history(occurrence.routine.id).map {
                    RunView.Done(occurrence.routine.name, RoutineHistory(it).streaks().current, run?.let { r -> r.took(clock.instant()) })
                }
                else -> untilWindowEnds(run, occurrence)
            }
        }

    /**
     * "Fertig": ticks the step [itemId] off, if the run is at it, and the run moves on, the next step's time beginning
     * now; with the last of them, the routine is done. Nothing if it is at another, as after a second tap.
     */
    suspend fun done(occurrenceId: Long, itemId: Long) {
        val (run, step) = atStep(occurrenceId) ?: return
        if (step.item.itemId != itemId) return
        routines.tick(occurrenceId, itemId, ticked = true)
        val now = clock.instant()
        val next = step.left.firstOrNull { it.itemId != itemId }
        val moved = if (next == null) run.copy(finishedAt = now) else run.at(next.itemId, now)
        store.save(moved.copy(tickedHere = run.tickedHere + itemId))
        onChange()
    }

    /**
     * "Schritt zurück": unticks the item last ticked off in the run that still is, and shows it again, its time beginning
     * anew; nothing if there is none.
     */
    suspend fun back(occurrenceId: Long) {
        val (run, step) = atStep(occurrenceId) ?: return
        val item = run.lastTicked(step.checklist) ?: return
        routines.tick(occurrenceId, item, ticked = false)
        store.save(run.at(item, clock.instant()).copy(tickedHere = run.tickedHere.take(run.tickedHere.lastIndexOf(item))))
        onChange()
    }

    /** "Beenden": no run is running any more; the ticks stay as they are. */
    suspend fun stop() {
        store.clear()
        onChange()
    }

    /** The step's time running out was told of, once. */
    suspend fun alerted(occurrenceId: Long) {
        val run = store.current()?.takeIf { it.occurrenceId == occurrenceId } ?: return
        store.save(run.copy(alerted = true))
    }

    /** The run through the occurrence, while it runs, and the step it is at. */
    private suspend fun atStep(occurrenceId: Long): Pair<Run, RunView.Step>? {
        val run = store.current()?.takeIf { it.occurrenceId == occurrenceId && it.finishedAt == null } ?: return null
        val step = view(occurrenceId, run.routineId).first() as? RunView.Step ?: return null
        // As the view had it: at this step, since it showed.
        return (store.current() ?: run) to step
    }

    /**
     * What the open [occurrence] shows of its [run], and once its time window ends, that it was missed, though nothing
     * marked it so yet: the alarm that does may come later. A step other than the run's, as one ticked off on Heute
     * meanwhile, begins now.
     */
    private fun untilWindowEnds(run: Run?, occurrence: RunOccurrence): Flow<RunView> = flow {
        val view = viewOf(run, occurrence)
        if (run != null && view is RunView.Step && run.stepItemId != view.item.itemId) {
            // The kept run follows, which shows the step anew.
            store.save(run.at(view.item.itemId, clock.instant()))
            return@flow
        }
        emit(view)
        if (view !is RunView.Step) return@flow
        val end = occurrence.routine.windowEnd(occurrence.occurrence, clock.zone)
        delay(Duration.between(clock.instant(), end).toMillis().coerceAtLeast(0))
        emit(RunView.Ended(RunEnd.Missed))
    }

    /** Why the occurrence of [routine] is gone: it was set aside, or deleted with it. */
    private fun goneBecause(routine: Routine?): RunEnd = when (routine?.status) {
        RoutineStatus.Paused -> RunEnd.Paused
        RoutineStatus.Archived -> RunEnd.Archived
        RoutineStatus.Active, null -> RunEnd.Gone
    }

    /** What an occurrence not done shows of its [run]: its next step while both run, why it ended otherwise. */
    private fun viewOf(run: Run?, open: RunOccurrence): RunView {
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
            run == null || run.finishedAt != null -> RunEnd.Stopped
            else -> null
        }
        if (why != null) return RunView.Ended(why)
        val next = checklist.indexOfFirst { !it.ticked }
        if (next < 0) return RunView.Ended(RunEnd.Gone)
        return RunView.Step(
            routine.name, checklist[next], number = next + 1, count = checklist.size, checklist = checklist,
            startedAt = run!!.stepStartedAt, runStartedAt = run.startedAt, canGoBack = run.lastTicked(checklist) != null,
            nameOnLockScreen = routine.style.nameOnLockScreen,
        )
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
