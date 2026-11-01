package de.emilyshub.stats

import java.time.LocalDate

/**
 * The flame atop Heute (SPEC.md §1): the [days] of the overall streak, as the statistics count it (§8), grey while
 * today is still open ([todayOpen]): routines due, none done yet and nothing missed.
 */
data class StreakFlame(val days: Int, val todayOpen: Boolean)

/** The flame for [today] over these occurrences, all routines' together; none without a streak. */
fun RoutineHistory.flame(today: LocalDate): StreakFlame? {
    val current = streaks().current
    if (current == 0) return null
    return StreakFlame(days = current, todayOpen = days[today] == DayOutcome.Open)
}
