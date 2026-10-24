package de.emilyshub.routines

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A routine (SPEC.md §2): due at each of its [times] on the days its [repetition] says, reminding again of what
 * is left open as its [followUps] say, and missing what is still open once its time [window] ends.
 */
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
    /** How often and how far apart it reminds again of an occurrence left open; takes effect at once. */
    @Embedded(prefix = "follow_up_") val followUps: FollowUps = FollowUps(),
    /** Whether its reminders fill the screen like an alarm clock, also on the lock screen, where Android allows it. */
    @ColumnInfo(name = "full_screen", defaultValue = "0") val fullScreen: Boolean = false,
    /** How long each occurrence can be caught up on before it is missed. */
    @Embedded(prefix = "window_") val window: TimeWindow = TimeWindow(),
    @ColumnInfo(defaultValue = "'normal'") val priority: Priority = Priority.Normal,
    /** Only an active routine is planned and reminded of; what it has done, skipped or missed stays all the same. */
    @ColumnInfo(defaultValue = "'active'") val status: RoutineStatus = RoutineStatus.Active,
) {
    /** The times it is due on [date]; none if its repetition skips that day. */
    fun timesOn(date: LocalDate): List<LocalTime> = if (repetition.isDueOn(date)) times else emptyList()

    /** The times on [date] that are due from [plannedFrom] on, the routine's day as it is first planned. */
    fun plannedTimesOn(date: LocalDate, zone: ZoneId): List<LocalTime> =
        timesOn(date).filter { isDueAtOrAfter(plannedFrom, date, it, zone) }

    /** Its occurrence at [time] on [date] as it is planned: open. */
    fun occurrenceAt(date: LocalDate, time: LocalTime) =
        Occurrence(routineId = id, date = date, time = time, status = OccurrenceStatus.Open)

    /** When the time window of its [occurrence] ends: if still open then, it is missed. */
    fun windowEnd(occurrence: Occurrence, zone: ZoneId): Instant = window.end(occurrence.date, occurrence.time, zone)

    /** Whether the time window of its [occurrence] has ended at [now]. */
    fun windowEnded(occurrence: Occurrence, now: Instant, zone: ZoneId): Boolean = !windowEnd(occurrence, zone).isAfter(now)

    /**
     * When its [occurrence] is reminded of while it stays open: at its reminder time and at the follow-ups
     * after it, before its window ends.
     */
    fun reminderTimes(occurrence: Occurrence, zone: ZoneId): List<Instant> {
        val windowEnd = windowEnd(occurrence, zone)
        return followUps.from(occurrence.reminderTime(zone)).filter { it.isBefore(windowEnd) }
    }

    /** Whether its [occurrence] is to be reminded of at [now]: still open, its reminder time come, its window not ended. */
    fun isDue(occurrence: Occurrence, now: Instant, zone: ZoneId): Boolean =
        occurrence.status == OccurrenceStatus.Open &&
            !occurrence.reminderTime(zone).isAfter(now) &&
            !windowEnded(occurrence, now, zone)
}

/** Whether a routine is in use (SPEC.md §2, Status): paused for a while, or archived, out of the way. */
enum class RoutineStatus(val stored: String) {
    Active("active"),
    Paused("paused"),
    Archived("archived"),
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
    /** When to remind of it instead of its due time, once it was put off ("Später"). */
    @ColumnInfo(name = "remind_at") val remindAt: Instant? = null,
) {
    /** When it is reminded of: at its due time, or when it was put off to. */
    fun reminderTime(zone: ZoneId): Instant = remindAt ?: dueAt(date, time, zone).toInstant()
}

/**
 * An open occurrence to remind of (SPEC.md §3): its routine's [name], when it is due, whether with a
 * [fullScreen] alarm, and when its time window ends.
 */
data class Reminder(
    val occurrenceId: Long,
    val name: String,
    val dueAt: ZonedDateTime,
    val fullScreen: Boolean,
    val windowEnd: Instant,
)

/** An occurrence on the Heute list, with its routine's name, the moment it is due, and its routine's checklist. */
data class DueRoutine(
    val occurrenceId: Long,
    val routineId: Long,
    val name: String,
    val dueAt: ZonedDateTime,
    val status: OccurrenceStatus,
    val priority: Priority,
    val checklist: List<ChecklistEntry> = emptyList(),
)
