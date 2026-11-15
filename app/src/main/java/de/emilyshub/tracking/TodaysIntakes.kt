package de.emilyshub.tracking

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * "Stunden seit den heute eingetragenen Einnahmen" (SPEC.md §17), for a Stehtest and an event: the medications taken
 * today with the minutes since each was last taken before a moment, by the times planned, or as today's evening entry
 * says otherwise, a forgotten one left out.
 */
object TodaysIntakes {
    /** The medications of today taken before [now], each by id with the minutes since. */
    suspend fun before(database: TrackingDatabase, now: ZonedDateTime): List<Pair<Long, Int>> {
        val today = now.toLocalDate()
        val deviations = database.entries().deviations(today).associateBy { it.medicationId }
        return database.medications().all().filter { it.isActiveOn(today) }.mapNotNull { medication ->
            val times: List<LocalTime> = when (val deviation = deviations[medication.id]) {
                null -> medication.times
                else -> when (deviation.deviation) {
                    Deviation.Forgotten -> emptyList()
                    Deviation.OtherTime -> listOfNotNull(deviation.time)
                }
            }
            val last = times.filter { !it.isAfter(now.toLocalTime()) }.maxOrNull() ?: return@mapNotNull null
            medication.id to Duration.between(today.atTime(last).atZone(now.zone), now).toMinutes().toInt()
        }
    }
}
