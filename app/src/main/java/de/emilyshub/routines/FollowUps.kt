package de.emilyshub.routines

import androidx.room.ColumnInfo
import java.io.Serializable
import java.time.Duration
import java.time.Instant

/**
 * How a routine reminds again of an occurrence left open (SPEC.md §3, "Ignoriert"): [count] more times,
 * [everyMinutes] apart. After the last one the reminder stays up without a sound. Serializable, so that
 * the editor keeps it across rotation and the app lock.
 */
data class FollowUps(
    @ColumnInfo(defaultValue = "3") val count: Int = 3,
    @ColumnInfo(name = "minutes", defaultValue = "30") val everyMinutes: Int = 30,
) : Serializable {

    /** A reminder at [first] and the follow-ups after it. */
    fun from(first: Instant): List<Instant> =
        (0..count).map { first + Duration.ofMinutes(everyMinutes.toLong()).multipliedBy(it.toLong()) }

    companion object {
        /** None, or up to five (SPEC.md §2). */
        val COUNTS = 0..5

        /** From every 5 minutes to every 2 hours. */
        val MINUTES = 5..120

        /** The longest a reminder can keep coming back after its first time. */
        val LONGEST: Duration = Duration.ofMinutes(COUNTS.last.toLong() * MINUTES.last)
    }
}
