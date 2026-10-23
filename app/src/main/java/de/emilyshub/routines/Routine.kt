package de.emilyshub.routines

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** A routine (SPEC.md §2): due at each of its [times] on the days its [repetition] says. */
@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    /** One or more, in the order of the day. */
    val times: List<LocalTime>,
    val repetition: Repetition,
    /**
     * When its times and repetition took effect: on creation or their last change. Occurrences are only
     * planned from then on, so neither a new routine nor a change makes anything overdue.
     */
    @ColumnInfo(name = "planned_from") val plannedFrom: Instant,
) {
    /** The times it is due on [date]; none if its repetition skips that day. */
    fun timesOn(date: LocalDate): List<LocalTime> = if (repetition.isDueOn(date)) times else emptyList()

    /** The times on [date] that are due from [plannedFrom] on, the routine's day as it is first planned. */
    fun plannedTimesOn(date: LocalDate, zone: ZoneId): List<LocalTime> =
        timesOn(date).filter { isDueAtOrAfter(plannedFrom, date, it, zone) }
}

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
