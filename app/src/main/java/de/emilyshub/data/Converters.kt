package de.emilyshub.data

import androidx.room.TypeConverter
import de.emilyshub.routines.OccurrenceStatus
import java.time.LocalDate
import java.time.LocalTime

/** Dates and times are stored as ISO text ("2026-10-23", "08:00"), which sorts and compares like the values. */
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
    fun fromStatus(status: OccurrenceStatus): String = status.stored

    @TypeConverter
    fun toStatus(text: String): OccurrenceStatus = OccurrenceStatus.entries.first { it.stored == text }
}
