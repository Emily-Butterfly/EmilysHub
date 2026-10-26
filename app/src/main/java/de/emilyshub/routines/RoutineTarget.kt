package de.emilyshub.routines

import java.io.Serializable

/**
 * What a routine aims at for the goal it hangs under (SPEC.md §2, §6): so many days done in a row, or done so many times.
 * Serializable, so that the editor keeps it across rotation and the app lock.
 */
sealed interface RoutineTarget : Serializable {
    /** The days in a row, or the times. */
    val count: Int

    /** The same kind of target, of [count] days in a row or times. */
    fun withCount(count: Int): RoutineTarget

    /** So many days done in a row, as the statistics count the streak (SPEC.md §8). */
    data class Streak(override val count: Int) : RoutineTarget {
        override fun withCount(count: Int) = copy(count = count)
    }

    /** Done so many times, each time it was due counting once. */
    data class TimesDone(override val count: Int) : RoutineTarget {
        override fun withCount(count: Int) = copy(count = count)
    }

    companion object {
        /** From once to as many as three digits take. */
        val COUNTS = 1..999
    }
}
