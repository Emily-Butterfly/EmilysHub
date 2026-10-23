package de.emilyshub.routines

import java.time.LocalTime

/** What [replanDay] changes on a day: occurrences moved to another time, occurrences removed, times added. */
data class DayChanges(
    val moved: Map<Long, LocalTime> = emptyMap(),
    val removed: Set<Long> = emptySet(),
    val added: List<LocalTime> = emptyList(),
)

/**
 * Brings a routine's [occurrences] on one day in line with the [times] it is due then. The past stays
 * as it was and nothing becomes overdue: only what [isAhead] says is still to come changes.
 *
 * - An occurrence at one of the times stays.
 * - The other occurrences take the remaining times, in the order of the day. One that is done or
 *   skipped stays as it was and fills its time: a routine done at 09:00 and then moved to 18:00 is not
 *   due again that day. An open one moves to its time if that is ahead, and otherwise stays.
 * - Of the occurrences left over, open ones still ahead are removed. Of the times left over, those
 *   ahead are added.
 */
internal fun replanDay(occurrences: List<Occurrence>, times: List<LocalTime>, isAhead: (LocalTime) -> Boolean): DayChanges {
    val others = occurrences.filter { it.time !in times }.sortedBy { it.time }
    val freeTimes = times.filter { time -> occurrences.none { it.time == time } }.sorted()
    return DayChanges(
        moved = others.zip(freeTimes)
            .filter { (occurrence, time) -> occurrence.status == OccurrenceStatus.Open && isAhead(time) }
            .associate { (occurrence, time) -> occurrence.id to time },
        removed = others.drop(freeTimes.size)
            .filter { it.status == OccurrenceStatus.Open && isAhead(it.time) }
            .map { it.id }
            .toSet(),
        added = freeTimes.drop(others.size).filter(isAhead),
    )
}
