package de.emilyshub.data

import androidx.room.TypeConverter
import de.emilyshub.notes.NoteSort
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Priority
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.routines.RoutineTarget
import de.emilyshub.todos.Recurrence
import de.emilyshub.wishes.WishStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay

/**
 * Dates and times are stored as ISO text ("2026-10-23", "08:00", "2026-10-23T06:00:00Z"); dates and times
 * of day sort and compare like the values.
 */
class Converters {
    @TypeConverter
    fun fromDate(date: LocalDate): String = date.toString()

    @TypeConverter
    fun toDate(text: String): LocalDate = LocalDate.parse(text)

    @TypeConverter
    fun fromTime(time: LocalTime): String = time.toString()

    @TypeConverter
    fun toTime(text: String): LocalTime = LocalTime.parse(text)

    @TypeConverter
    fun fromInstant(instant: Instant): String = instant.toString()

    @TypeConverter
    fun toInstant(text: String): Instant = Instant.parse(text)

    /** "08:00,20:00" */
    @TypeConverter
    fun fromTimes(times: List<LocalTime>): String = times.joinToString(",")

    @TypeConverter
    fun toTimes(text: String): List<LocalTime> = text.split(",").map(LocalTime::parse)

    /** "daily", "weekdays:1,3,5" (ISO day numbers, Monday is 1), "every:3:2026-10-23" or "once:2026-10-30". */
    @TypeConverter
    fun fromRepetition(repetition: Repetition): String = when (repetition) {
        Repetition.Daily -> "daily"
        is Repetition.Weekdays -> "weekdays:" + weekdaysText(repetition.days)
        is Repetition.EveryNDays -> "every:${repetition.days}:${repetition.from}"
        is Repetition.Once -> "once:${repetition.date}"
    }

    @TypeConverter
    fun toRepetition(text: String): Repetition {
        val parts = text.split(":")
        return when (parts.first()) {
            "daily" -> Repetition.Daily
            "weekdays" -> Repetition.Weekdays(weekdaysOf(parts[1]))
            "every" -> Repetition.EveryNDays(days = parts[1].toInt(), from = LocalDate.parse(parts[2]))
            "once" -> Repetition.Once(LocalDate.parse(parts[1]))
            else -> error("Unknown repetition: $text")
        }
    }

    /**
     * "daily", "weekdays:1,4" (ISO day numbers, Monday is 1), "every:14", "monthly:31" or "yearly:--02-29", the last
     * three with ":from-done" if it comes again counting from the day it was done. Anything else, or a number out of its
     * range, is refused, such as from a damaged backup (SPEC.md §9).
     */
    @TypeConverter
    fun fromRecurrence(recurrence: Recurrence): String {
        val rule = when (recurrence) {
            Recurrence.Daily -> "daily"
            is Recurrence.Weekdays -> "weekdays:" + weekdaysText(recurrence.days)
            is Recurrence.EveryNDays -> "every:${recurrence.days}"
            is Recurrence.Monthly -> "monthly:${recurrence.day}"
            is Recurrence.Yearly -> "yearly:${recurrence.date}"
        }
        return if (recurrence is Recurrence.WithInterval && recurrence.fromDone) "$rule:$FROM_DONE" else rule
    }

    @TypeConverter
    fun toRecurrence(text: String): Recurrence {
        val parts = text.split(":")
        val kind = parts.first()
        val fromDone = kind in WITH_INTERVAL && parts.size == 3 && parts[2] == FROM_DONE
        val size = when {
            kind == "daily" -> 1
            fromDone -> 3
            else -> 2
        }
        require(parts.size == size) { "Unknown recurrence: $text" }
        return when (kind) {
            "daily" -> Recurrence.Daily
            "weekdays" -> Recurrence.Weekdays(weekdaysOf(parts[1]))
            "every" -> Recurrence.EveryNDays(parts[1].toInt().also { require(it in RoutineDraft.INTERVAL_DAYS) }, fromDone)
            "monthly" -> Recurrence.Monthly(parts[1].toInt().also { require(it in 1..MAX_DAY_OF_MONTH) }, fromDone)
            "yearly" -> Recurrence.Yearly(MonthDay.parse(parts[1]), fromDone)
            else -> error("Unknown recurrence: $text")
        }
    }

    /** "1,3,5": ISO day numbers, Monday is 1. */
    private fun weekdaysText(days: Set<DayOfWeek>) = days.sorted().joinToString(",") { it.value.toString() }

    private fun weekdaysOf(text: String): Set<DayOfWeek> = text.split(",").map { DayOfWeek.of(it.toInt()) }.toSet()

    /** "streak:30" for so many days done in a row, "done:100" for done so many times; any other is refused. */
    @TypeConverter
    fun fromRoutineTarget(target: RoutineTarget): String = when (target) {
        is RoutineTarget.Streak -> "streak:${target.count}"
        is RoutineTarget.TimesDone -> "done:${target.count}"
    }

    @TypeConverter
    fun toRoutineTarget(text: String): RoutineTarget {
        val (kind, count) = text.split(":").also { require(it.size == 2) { "Unknown routine target: $text" } }
        val number = count.toInt().also { require(it in RoutineTarget.COUNTS) { "Routine target out of range: $text" } }
        return when (kind) {
            "streak" -> RoutineTarget.Streak(number)
            "done" -> RoutineTarget.TimesDone(number)
            else -> error("Unknown routine target: $text")
        }
    }

    @TypeConverter
    fun fromPriority(priority: Priority): String = priority.stored

    @TypeConverter
    fun toPriority(text: String): Priority = Priority.entries.first { it.stored == text }

    @TypeConverter
    fun fromRoutineStatus(status: RoutineStatus): String = status.stored

    @TypeConverter
    fun toRoutineStatus(text: String): RoutineStatus = RoutineStatus.entries.first { it.stored == text }

    @TypeConverter
    fun fromStatus(status: OccurrenceStatus): String = status.stored

    @TypeConverter
    fun toStatus(text: String): OccurrenceStatus = OccurrenceStatus.entries.first { it.stored == text }

    @TypeConverter
    fun fromWishStatus(status: WishStatus): String = status.stored

    @TypeConverter
    fun toWishStatus(text: String): WishStatus = WishStatus.entries.first { it.stored == text }

    @TypeConverter
    fun fromNoteSort(sort: NoteSort): String = sort.stored

    @TypeConverter
    fun toNoteSort(text: String): NoteSort = NoteSort.entries.first { it.stored == text }
}

private const val FROM_DONE = "from-done"
private const val MAX_DAY_OF_MONTH = 31
private val WITH_INTERVAL = setOf("every", "monthly", "yearly")
