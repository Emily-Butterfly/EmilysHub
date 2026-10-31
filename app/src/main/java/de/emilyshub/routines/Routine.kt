package de.emilyshub.routines

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.emilyshub.wishes.Wish
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A routine (SPEC.md §2): due at each of its [times] on the days its [repetition] says, reminding again of what
 * is left open as its [followUps] say, and missing what is still open once its time [window] ends; all that
 * while its [status] is active. Its checklist is kept apart, as [ChecklistItem]s. It can hang under a goal, its
 * [goalId] (SPEC.md §6), and count towards it by what it aims at, its [target].
 */
@Entity(
    tableName = "routines",
    foreignKeys = [
        ForeignKey(entity = Wish::class, parentColumns = ["id"], childColumns = ["goal_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["goal_id"])],
)
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
    /** Shown with it in the list and on Heute; it changes nothing else. */
    @ColumnInfo(defaultValue = "'normal'") val priority: Priority = Priority.Normal,
    /** Only an active routine is planned and reminded of; what it has done, skipped or missed stays all the same. */
    @ColumnInfo(defaultValue = "'active'") val status: RoutineStatus = RoutineStatus.Active,
    /** The goal it is part of, if any; deleting that goal leaves it hanging at none. */
    @ColumnInfo(name = "goal_id") val goalId: Long? = null,
    /**
     * What it aims at for its goal, if anything; saved only with a goal. One whose goal was deleted keeps it, counting
     * for nothing, until saved again. Without, it only shows with its goal.
     */
    val target: RoutineTarget? = null,
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

    /** Dropped for the day with "Heute nicht" (SPEC.md §2): counts like missed, but was decided. */
    Dropped("dropped"),
    ;

    /** Whether its occurrence's checklist can be ticked: not once it was skipped, missed or dropped. */
    val checklistTickable: Boolean get() = this == Open || this == Done
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
    /** When to remind of it instead of its due time, once it was put off ("Später") or arrived at its place. */
    @ColumnInfo(name = "remind_at") val remindAt: Instant? = null,
    /** Whether it waits for its routine's places: it came due elsewhere and pops up on arrival (SPEC.md §4). */
    @ColumnInfo(defaultValue = "0") val waiting: Boolean = false,
) {
    /** When it is reminded of: at its due time, or when it was put off to. */
    fun reminderTime(zone: ZoneId): Instant = remindAt ?: dueAt(date, time, zone).toInstant()
}

/** How an occurrence of a routine ended on its day, or that it is still open, as the statistics need it (SPEC.md §8). */
data class OccurrenceOutcome(
    @ColumnInfo(name = "routine_id") val routineId: Long,
    val date: LocalDate,
    val status: OccurrenceStatus,
)

/** A routine with how its occurrences ended, or that they are still open, read together so that the two always fit. */
data class RoutineWithOutcomes(
    @Embedded val routine: Routine,
    @Relation(parentColumn = "id", entityColumn = "routine_id", entity = Occurrence::class)
    val outcomes: List<OccurrenceOutcome>,
)

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

/**
 * An occurrence on the Heute list, with its routine's name, the moment it is due, its routine's checklist, and the
 * names of the places it waits for, if it waits.
 */
data class DueRoutine(
    val occurrenceId: Long,
    val routineId: Long,
    val name: String,
    val dueAt: ZonedDateTime,
    val status: OccurrenceStatus,
    val priority: Priority,
    val checklist: List<ChecklistEntry> = emptyList(),
    val waitingFor: List<String> = emptyList(),
)
