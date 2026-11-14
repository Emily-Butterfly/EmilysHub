package de.emilyshub.stats

import de.emilyshub.routines.OccurrenceOutcome
import de.emilyshub.routines.OccurrenceStatus
import java.time.LocalDate
import java.util.SortedMap

/**
 * From when a day counts for the overall streak (SPEC.md §8; #109), as the settings have it, on this device only: from
 * a number of things done ([Count]), or from a share of what counts for or against a day ([Share]).
 */
sealed interface StreakRule {
    /** How it is kept in the settings. */
    val stored: String

    /** Whether a day with [done] things done and [against] missed or dropped counts. */
    fun counts(done: Int, against: Int): Boolean

    /** From [count] things done, routines and to-dos together, whatever was missed or dropped. */
    data class Count(val count: Int) : StreakRule {
        override val stored get() = "count:$count"

        override fun counts(done: Int, against: Int) = done >= count
    }

    /** With something done, from [percent] of what counts for or against the day done; at 100 %, nothing missed. */
    data class Share(val percent: Int) : StreakRule {
        override val stored get() = "share:$percent"

        override fun counts(done: Int, against: Int) = done > 0 && done * FULL >= percent * (done + against)
    }

    companion object {
        /** From the first thing done. */
        val DEFAULT: StreakRule = Count(1)

        /** The numbers of things done to choose. */
        val COUNTS = 1..30

        /** The shares to choose, in percent: in tens. */
        val SHARES = (10..100 step 10).toList()

        /** The share offered first. */
        const val SHARE_DEFAULT = 50

        private const val FULL = 100

        /** The rule kept as [stored]; anything unknown, or out of range, is the default. */
        fun of(stored: String?): StreakRule {
            val (kind, value) = stored?.split(':')?.takeIf { it.size == 2 } ?: return DEFAULT
            val number = value.toIntOrNull() ?: return DEFAULT
            return when {
                kind == "count" && number in COUNTS -> Count(number)
                kind == "share" && number in SHARES -> Share(number)
                else -> DEFAULT
            }
        }
    }
}

/**
 * The overall streak (SPEC.md §8; #109) over the occurrences of all [routines] and the days the [todosDone] were ticked
 * off on, by [rule]: what was done, occurrences done and to-dos ticked off, counts for a day; only occurrences missed
 * or dropped count against it; what was skipped or is still open counts neither way, and a to-do never against one. A
 * past day that something counts for or against, and that does not keep the rule, breaks the streak; one that nothing
 * counts for or against neither counts nor breaks it. [today] counts once it keeps the rule, and does not break the
 * streak before it is over. A routine's own streak is [RoutineHistory]'s.
 */
class OverallHistory(
    routines: List<OccurrenceOutcome>,
    todosDone: List<LocalDate>,
    private val rule: StreakRule,
    private val today: LocalDate,
) {
    private val done: Map<LocalDate, Int> =
        (routines.filter { it.status == OccurrenceStatus.Done }.map { it.date } + todosDone).groupingBy { it }.eachCount()
    private val missed: Map<LocalDate, Int> = routines.countBy(OccurrenceStatus.Missed)
    private val dropped: Map<LocalDate, Int> = routines.countBy(OccurrenceStatus.Dropped)

    /** Whether anything ever counted for or against a day. */
    private val anything = done.isNotEmpty() || missed.isNotEmpty() || dropped.isNotEmpty()

    /**
     * How each day ended for the overall streak, as the calendar of the statistics shows it: done where it counts;
     * missed, or dropped with nothing missed, where it broke the streak; skipped where nothing counted for or against
     * it, and today, not counting yet, still open.
     */
    val days: SortedMap<LocalDate, DayOutcome> =
        (routines.map { it.date } + todosDone).distinct().associateWith(::outcomeOf).toSortedMap()

    private fun outcomeOf(date: LocalDate): DayOutcome {
        val done = done[date] ?: 0
        val missed = missed[date] ?: 0
        val dropped = dropped[date] ?: 0
        return when {
            rule.counts(done, missed + dropped) -> DayOutcome.Done
            // Until midnight, something may still be ticked off.
            !date.isBefore(today) -> DayOutcome.Open
            done == 0 && missed + dropped == 0 -> DayOutcome.Skipped
            missed > 0 -> DayOutcome.Missed
            dropped > 0 -> DayOutcome.Dropped
            // Done, but not enough: nothing was missed.
            else -> DayOutcome.Short
        }
    }

    /** The overall streak now, and the longest ever. */
    fun streaks(): Streaks = streaksOf(days)

    /** While no overall streak runs, the one before (#99). */
    fun lastStreak(): LastStreak? = lastStreakOf(days)

    /** The flame for [today]; none while nothing ever counted for or against a day. The longest streak it leaves out. */
    fun flame(): StreakFlame? {
        if (!anything) return null
        val todayCounts = days[today] == DayOutcome.Done
        return StreakFlame(days = currentStreakOf(days), todayOpen = !todayCounts, last = lastStreak())
    }

    /**
     * Whether these days, read from one on only, hold all the [flame] needs (#108): a day that breaks the streak, and
     * one done after it. Then the streak now began after that break, or the last one did, should none run now.
     */
    internal val holdsFlame: Boolean
        get() {
            var broken = false
            days.values.forEach { outcome ->
                when (outcome) {
                    DayOutcome.Missed, DayOutcome.Dropped, DayOutcome.Short -> broken = true
                    DayOutcome.Done -> if (broken) return true
                    DayOutcome.Skipped, DayOutcome.Open -> Unit
                }
            }
            return false
        }
}

/** The days the flame first reads back from today (#108); then twice as many each time, as [flameOf] has it. */
internal const val FLAME_WINDOW = 32L

/**
 * The flame for [today], as [OverallHistory.flame] has it over the whole history, but [read] from only as far back as
 * it needs (#108): from a day ever further back, until what was read [OverallHistory.holdsFlame], or reaches back to
 * the [first] day anything is on, if there is one.
 */
internal suspend fun flameOf(
    today: LocalDate,
    first: LocalDate?,
    read: suspend (from: LocalDate) -> OverallHistory,
): StreakFlame? {
    var days = FLAME_WINDOW
    while (true) {
        val from = today.minusDays(days)
        val history = read(from)
        if (first == null || !first.isBefore(from) || history.holdsFlame) return history.flame()
        days *= 2
    }
}

/** How many of these occurrences ended as [status], by day. */
private fun List<OccurrenceOutcome>.countBy(status: OccurrenceStatus): Map<LocalDate, Int> =
    filter { it.status == status }.groupingBy { it.date }.eachCount()
