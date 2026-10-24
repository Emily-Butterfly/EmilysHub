package de.emilyshub.data

import androidx.room.TypeConverter
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Priority
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

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
        is Repetition.Weekdays -> "weekdays:" + repetition.days.sorted().joinToString(",") { it.value.toString() }
        is Repetition.EveryNDays -> "every:${repetition.days}:${repetition.from}"
        is Repetition.Once -> "once:${repetition.date}"
    }

    @TypeConverter
    fun toRepetition(text: String): Repetition {
        val parts = text.split(":")
        return when (parts.first()) {
            "daily" -> Repetition.Daily
            "weekdays" -> Repetition.Weekdays(parts[1].split(",").map { DayOfWeek.of(it.toInt()) }.toSet())
            "every" -> Repetition.EveryNDays(days = parts[1].toInt(), from = LocalDate.parse(parts[2]))
            "once" -> Repetition.Once(LocalDate.parse(parts[1]))
            else -> error("Unknown repetition: $text")
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
}
