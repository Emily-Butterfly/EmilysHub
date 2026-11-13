package de.emilyshub.stats

import java.time.LocalDate

/**
 * The flame atop Heute (SPEC.md §1): the [days] of the overall streak, as the statistics count it (§8), grey while
 * today is still open ([todayOpen]): routines due, none done yet and nothing missed. Without a streak it stands at 0,
 * grey, and knows the [last] one, if there was one (#99).
 */
data class StreakFlame(val days: Int, val todayOpen: Boolean, val last: LastStreak? = null) {
    /** Whether it is lit: with a streak, once today counts or there is nothing to do today. */
    val lit: Boolean get() = days > 0 && !todayOpen
}

/** The flame for [today] over these occurrences, all routines' together; none without anything ever due. */
fun RoutineHistory.flame(today: LocalDate): StreakFlame? {
    if (days.isEmpty()) return null
    return StreakFlame(days = streaks().current, todayOpen = days[today] == DayOutcome.Open, last = lastStreak())
}
