package de.emilyshub.stats

import de.emilyshub.routines.Occurrence
import de.emilyshub.routines.OccurrenceStatus
import java.time.LocalDate
import java.util.SortedMap
import kotlin.math.roundToInt

/** How a routine's due day ended, as the statistics count it (SPEC.md §8). */
enum class DayOutcome {
    /** Something done and nothing missed: the day counts towards the streak. */
    Done,

    /** All of it skipped: the day neither counts towards the streak nor breaks it. */
    Skipped,

    /** Something missed: the day breaks the streak. */
    Missed,

    /** Nothing done or missed yet while something is still open: so far, like a day skipped. */
    Open,
    ;

    companion object {
        /** The outcome of a day whose occurrences ended as [statuses]: one missed decides it, then one done. */
        fun of(statuses: Collection<OccurrenceStatus>): DayOutcome = when {
            OccurrenceStatus.Missed in statuses -> Missed
            OccurrenceStatus.Done in statuses -> Done
            OccurrenceStatus.Open in statuses -> Open
            else -> Skipped
        }
    }
}

/** The days done in a row up to now ([current]), and the most there ever were ([longest]). */
data class Streaks(val current: Int, val longest: Int)

/** Of the occurrences [counted], done or missed, those [done]; skipped ones and those still open are not counted. */
data class DoneRate(val done: Int, val counted: Int) {
    /** In whole percent; none while nothing counts. */
    val percent: Int? get() = if (counted == 0) null else (done * PERCENT / counted.toDouble()).roundToInt()

    private companion object {
        const val PERCENT = 100
    }
}

/**
 * The statistics of a routine, or of all routines together, by their [occurrences] (SPEC.md §8). Only the days with an
 * occurrence count: those the routine was due on. Days it was not due, or paused, have none.
 */
class RoutineHistory(private val occurrences: List<Occurrence>) {

    /** How each day with an occurrence ended, by date. */
    val days: SortedMap<LocalDate, DayOutcome> =
        occurrences.groupBy(Occurrence::date, Occurrence::status).mapValues { (_, statuses) -> DayOutcome.of(statuses) }.toSortedMap()

    /** The days done in a row: a day missed breaks the streak, one skipped or still open neither breaks nor counts. */
    fun streaks(): Streaks {
        var current = 0
        var longest = 0
        days.values.forEach { outcome ->
            when (outcome) {
                DayOutcome.Done -> longest = maxOf(longest, ++current)
                DayOutcome.Missed -> current = 0
                DayOutcome.Skipped, DayOutcome.Open -> Unit
            }
        }
        return Streaks(current, longest)
    }

    /** The done rate of the occurrences in the last [days] days up to [today], today included. */
    fun doneRate(today: LocalDate, days: Int): DoneRate {
        val from = today.minusDays(days - 1L)
        val inPeriod = occurrences.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
        return DoneRate(
            done = inPeriod.count { it.status == OccurrenceStatus.Done },
            counted = inPeriod.count { it.status == OccurrenceStatus.Done || it.status == OccurrenceStatus.Missed },
        )
    }

    companion object {
        /** The periods of the done rate, in days (SPEC.md §8). */
        val PERIODS = listOf(7, 30, 90)
    }
}
