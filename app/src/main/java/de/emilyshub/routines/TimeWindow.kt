package de.emilyshub.routines

import androidx.room.ColumnInfo
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * How long an occurrence can be caught up on (SPEC.md §2, Nachhol-Zeitfenster): [hours] from its due time, or
 * [untilMidnight], the end of its day. Once its window ends it is missed. Serializable, so that the editor keeps
 * it across rotation and the app lock.
 */
data class TimeWindow(
    @ColumnInfo(defaultValue = "4") val hours: Int = 4,
    @ColumnInfo(name = "until_midnight", defaultValue = "0") val untilMidnight: Boolean = false,
) : Serializable {

    /**
     * When the window of an occurrence due at [time] on [date] ends: [hours] as they pass, so across a clock
     * change the clock shows one more or one less; or at the start of the next local day.
     */
    fun end(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        if (untilMidnight) {
            date.plusDays(1).atStartOfDay(zone).toInstant()
        } else {
            dueAt(date, time, zone).toInstant() + Duration.ofHours(hours.toLong())
        }

    companion object {
        /** From one hour to a whole day. */
        val HOURS = 1..24

        /** The longest a window can be open: a whole day, which is 25 hours when the clocks fall back. */
        val LONGEST: Duration = Duration.ofHours(25)
    }
}
