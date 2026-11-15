package de.emilyshub.tracking

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Which day an entry of Tracking is for (SPEC.md §17, "Tag" and "Nachtragen"): a night belongs to the day before until
 * [DAY_START], by the clock on the wall, also on the nights the clocks change; entries go back [BACKFILL_DAYS] days.
 */
object EntryDay {
    /** Until this time a new entry is for the day before. */
    val DAY_START: LocalTime = LocalTime.of(4, 0)

    /** How many days back an entry can be made up. */
    const val BACKFILL_DAYS = 7L

    /** The day a new entry made [now] is for, to change in the entry. */
    fun suggested(now: ZonedDateTime): LocalDate =
        if (now.toLocalTime().isBefore(DAY_START)) now.toLocalDate().minusDays(1) else now.toLocalDate()

    /** The earliest day an entry can be made for [today]. */
    fun earliest(today: LocalDate): LocalDate = today.minusDays(BACKFILL_DAYS)

    /** Whether an entry can be made for [day] on [today]: not for one to come, nor more than [BACKFILL_DAYS] back. */
    fun canRecord(day: LocalDate, today: LocalDate): Boolean = !day.isAfter(today) && !day.isBefore(earliest(today))

    /** Whether an entry for [day] first recorded on [recordedOn] was made up: later than the day after. */
    fun isBackfilled(day: LocalDate, recordedOn: LocalDate): Boolean = recordedOn.isAfter(day.plusDays(1))
}
