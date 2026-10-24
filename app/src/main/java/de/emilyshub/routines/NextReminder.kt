package de.emilyshub.routines

import java.time.Instant
import java.time.ZoneId

/**
 * When to remind of this routine next after [after] (SPEC.md §3): at a reminder time of one of its
 * [planned] occurrences that is still open, its first or a follow-up before its time window ends, or at a
 * time on the first day not planned yet. [planned] are its occurrences from the day of [after] on, those of
 * the day before, whose windows may still be open, and those put off, whenever they were due.
 */
fun Routine.nextReminder(planned: List<Occurrence>, after: Instant, zone: ZoneId): Instant? {
    val fromPlanned = planned
        .filter { it.status == OccurrenceStatus.Open }
        .flatMap { reminderTimes(it, zone) }
        .filter { it.isAfter(after) }
    val plannedDays = planned.map { it.date }.toSet()
    val fromSchedule = generateSequence(repetition.firstDueFrom(today(after, zone))) { repetition.firstDueFrom(it.plusDays(1)) }
        .filter { it !in plannedDays }
        .take(DAYS_TO_LOOK_AT)
        .map { date ->
            plannedTimesOn(date, zone).flatMap { reminderTimes(occurrenceAt(date, it), zone) }.filter { it.isAfter(after) }
        }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
    return (fromPlanned + fromSchedule).minOrNull()
}

/**
 * When the time window of one of its [planned] occurrences still open ends next after [after]: it is missed
 * then. [planned] are as for [nextReminder]; a day not planned yet has a reminder to come before its windows end.
 */
fun Routine.nextWindowEnd(planned: List<Occurrence>, after: Instant, zone: ZoneId): Instant? =
    planned
        .filter { it.status == OccurrenceStatus.Open }
        .map { windowEnd(it, zone) }
        .filter { it.isAfter(after) }
        .minOrNull()

// Today's times may all be past, and a day with the clocks changing may lose one; a day after that has them.
private const val DAYS_TO_LOOK_AT = 3
