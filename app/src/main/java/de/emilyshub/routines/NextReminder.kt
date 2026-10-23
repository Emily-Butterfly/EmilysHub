package de.emilyshub.routines

import java.time.Instant
import java.time.ZoneId

/**
 * When to remind of this routine next after [now] (SPEC.md §3): at the reminder time of one of its
 * [planned] occurrences that is still open, or at a time on the first day not planned yet. [planned]
 * are its occurrences from yesterday on, since one put off before midnight can come due today.
 */
fun Routine.nextReminder(planned: List<Occurrence>, now: Instant, zone: ZoneId): Instant? {
    val fromPlanned = planned
        .filter { it.status == OccurrenceStatus.Open }
        .map { it.reminderAt(zone) }
        .filter { it.isAfter(now) }
    val plannedDays = planned.map { it.date }.toSet()
    val fromSchedule = generateSequence(repetition.firstDueFrom(today(now, zone))) { repetition.firstDueFrom(it.plusDays(1)) }
        .filter { it !in plannedDays }
        .take(DAYS_TO_LOOK_AT)
        .map { date -> plannedTimesOn(date, zone).map { dueAt(date, it, zone).toInstant() }.filter { it.isAfter(now) } }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
    return (fromPlanned + fromSchedule).minOrNull()
}

// Today's times may all be past, and a day with the clocks changing may lose one; a day after that has them.
private const val DAYS_TO_LOOK_AT = 3
