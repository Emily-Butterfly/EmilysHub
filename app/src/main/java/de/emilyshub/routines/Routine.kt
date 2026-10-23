package de.emilyshub.routines

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** A routine (SPEC.md §2). For now due daily at one [time]; later slices add more rules and times. */
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val time: LocalTime,
    /** The first day it is due: the day it was created. */
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
)

/** How an occurrence ended (SPEC.md §2, Tracking); open until then. */
enum class OccurrenceStatus(val stored: String) {
    Open("open"),
    Done("done"),
    Skipped("skipped"),
    Missed("missed"),
}

/**
 * One time a routine is due (a "Fälligkeit"): on a [date] at a local [time]. Kept apart from the
 * routine, so that its status stays as it was when the routine changes later.
 */
@Entity(
    tableName = "occurrences",
    foreignKeys = [
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["routine_id", "date", "time"], unique = true), Index(value = ["date"])],
)
data class Occurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    val date: LocalDate,
    val time: LocalTime,
    val status: OccurrenceStatus,
)

/** An occurrence on the Heute list, with its routine's name and the moment it is due. */
data class DueRoutine(
    val occurrenceId: Long,
    val routineId: Long,
    val name: String,
    val dueAt: ZonedDateTime,
    val status: OccurrenceStatus,
)
