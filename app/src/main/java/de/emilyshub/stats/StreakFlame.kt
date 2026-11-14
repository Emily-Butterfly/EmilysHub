package de.emilyshub.stats

/**
 * The flame atop Heute (SPEC.md §1): the [days] of the overall streak, as the statistics count it (§8), grey while
 * today does not count yet ([todayOpen]), which it does once it keeps the rule of the overall streak (#109). Without a
 * streak it stands at 0, grey, and knows the [last] one, if there was one (#99).
 */
data class StreakFlame(val days: Int, val todayOpen: Boolean, val last: LastStreak? = null) {
    /** Whether it is lit: with a streak, once today counts. */
    val lit: Boolean get() = days > 0 && !todayOpen
}
