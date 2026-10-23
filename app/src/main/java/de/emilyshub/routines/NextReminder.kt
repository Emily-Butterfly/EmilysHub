package de.emilyshub.routines

import java.time.Instant
import java.time.ZoneId

/**
 * When to remind of this routine next after [after] (SPEC.md §3): at a reminder time of one of its
 * [planned] occurrences that is still open, its first or a follow-up, or at a time on the first day not
 * planned yet. [planned] are its occurrences from the day of [after] on, those of the day before, whose
 * follow-ups may still come, and those put off, whenever they were due.
 */
fun Routine.nextReminder(planned: List<Occurrence>, after: Instant, zone: ZoneId): Instant? {
    val fromPlanned = planned
        .filter { it.status == OccurrenceStatus.Open }
        .flatMap { it.reminderTimes(followUps, zone) }
        .filter { it.isAfter(after) }
    val plannedDays = planned.map { it.date }.toSet()
    val fromSchedule = generateSequence(repetition.firstDueFrom(today(after, zone))) { repetition.firstDueFrom(it.plusDays(1)) }
        .filter { it !in plannedDays }
        .take(DAYS_TO_LOOK_AT)
        .map { date ->
            plannedTimesOn(date, zone).flatMap { followUps.from(dueAt(date, it, zone).toInstant()) }.filter { it.isAfter(after) }
        }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
    return (fromPlanned + fromSchedule).minOrNull()
}

// Today's times may all be past, and a day with the clocks changing may lose one; a day after that has them.
private const val DAYS_TO_LOOK_AT = 3
