package de.emilyshub.routines

import java.time.LocalTime

/** What [replanDay] changes on a day: occurrences moved to another time, occurrences removed, times added. */
data class DayChanges(val moved: Map<Long, LocalTime>, val removed: Set<Long>, val added: List<LocalTime>)

/**
 * Brings a routine's [occurrences] on one day in line with the [times] it is due then. The past stays
 * as it was and nothing becomes overdue: only what [isAhead] says is still to come changes.
 *
 * - An occurrence at one of the times stays.
 * - What already happened, an occurrence done, skipped or overdue, takes the earliest of the remaining
 *   times and stands in for it: a routine done at 09:00 and moved to 18:00 is not due again that day.
 *   An overdue one moves along to its time if that is still ahead.
 * - Open occurrences still to come take the latest of the times left, which are the ones ahead: they move
 *   to a time ahead, or stay and stand in for one gone by. Any more of them are removed.
 * - Times ahead that nothing took are added.
 */
internal fun replanDay(occurrences: List<Occurrence>, times: List<LocalTime>, isAhead: (LocalTime) -> Boolean): DayChanges {
    val (toCome, happened) = occurrences
        .filter { it.time !in times }
        .sortedBy { it.time }
        .partition { it.status == OccurrenceStatus.Open && isAhead(it.time) }
    val freeTimes = times.filter { time -> occurrences.none { it.time == time } }.sorted()
    val left = freeTimes.drop(happened.size)
    val taken = happened.zip(freeTimes) + toCome.takeLast(left.size).zip(left.takeLast(toCome.size))
    return DayChanges(
        moved = taken
            .filter { (occurrence, time) -> occurrence.status == OccurrenceStatus.Open && isAhead(time) }
            .associate { (occurrence, time) -> occurrence.id to time },
        removed = toCome.dropLast(left.size).map { it.id }.toSet(),
        added = left.dropLast(toCome.size).filter(isAhead),
    )
}
