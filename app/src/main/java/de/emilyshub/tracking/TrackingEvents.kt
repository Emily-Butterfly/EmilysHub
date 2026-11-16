package de.emilyshub.tracking

import de.emilyshub.languages.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** A heart rate of Garmin's curve with the [tier] it has there. */
data class TieredBeat(val at: Instant, val bpm: Int, val tier: Tier)

/**
 * An event on its page: the [event], the [intakes] of today before it (each medication with the minutes since), and
 * Garmin's heart rate from [EventCurve.BEFORE] before it to [EventCurve.AFTER] after its end as the [curve].
 */
data class EventReport(val event: TrackingEvent, val intakes: List<Pair<Medication, Int>>, val curve: List<TieredBeat>)

/** How far Garmin's curve of an event reaches (SPEC.md §17): from 15 minutes before to 30 minutes after. */
object EventCurve {
    val BEFORE: Duration = Duration.ofMinutes(15)
    val AFTER: Duration = Duration.ofMinutes(30)
}

/** The ranges a value of an event must lie in to be saved. */
object EventLimits {
    /** The oxygen saturation, in percent. */
    val SPO2 = 50..100

    /** The minutes of a duration, a fading or a recovery: up to a day. */
    val MINUTES = 0..24 * 60
}

/**
 * The events of Tracking (SPEC.md §17, "Ereignisse"): begun with their kind, filled in as they go and later, the pulse
 * timer started by their end. Read and written only while Tracking is open.
 */
class TrackingEvents(private val tracking: TrackingStore, private val clock: Clock) {

    /** The moment it is, for the timer. */
    fun now(): Instant = clock.instant()

    /** Where the moments are shown. */
    fun zone(): ZoneId = clock.zone

    /** A new event of [kind] now, with the minutes since today's intakes: its id; none once locked. */
    suspend fun create(kind: EventKind): Long? = tracking.changing { database ->
        val now = ZonedDateTime.now(clock)
        val id = database.events().insert(TrackingEvent(kind = kind, at = now.toInstant().truncatedTo(ChronoUnit.MINUTES)))
        putIntakes(database, id, now)
        id
    }

    /** Event [id]; none if gone, or once locked. */
    suspend fun get(id: Long): TrackingEvent? = tracking.withDatabase { it.events().get(id) }

    /** All events, the latest first; none once locked. */
    suspend fun list(): List<TrackingEvent>? = tracking.withDatabase { it.events().all() }

    /** Saves [event], as [write] does; false once locked. */
    suspend fun save(event: TrackingEvent): Boolean = tracking.changing { write(it, event) } ?: false

    /**
     * Writes [event] into [database] in place of the one of its id, values out of their range left out, a water
     * temperature only for water, its time to the minute; moved to another time, the intakes before it are those of
     * that time. False if it is gone.
     */
    suspend fun write(database: TrackingDatabase, event: TrackingEvent): Boolean {
        val stored = database.events().get(event.id) ?: return false
        val cleaned = event.withinLimits()
        database.events().update(cleaned)
        if (cleaned.at != stored.at) {
            database.events().clearIntakes(cleaned.id)
            putIntakes(database, cleaned.id, cleaned.at.atZone(clock.zone))
        }
        return true
    }

    private suspend fun putIntakes(database: TrackingDatabase, id: Long, at: ZonedDateTime) {
        database.events().insertIntakes(TodaysIntakes.before(database, at).map { (medication, minutes) -> EventIntake(id, medication, minutes) })
    }

    /** Ends event [id] now, which starts its timer: false if it ended already. */
    suspend fun end(id: Long): Boolean = tracking.changing { database ->
        val event = database.events().get(id)?.takeIf { it.endedAt == null } ?: return@changing false
        database.events().update(event.copy(endedAt = clock.instant()))
        true
    } ?: false

    /** Deletes event [id], and the files of its photos. */
    suspend fun delete(id: Long) {
        tracking.changingWithFiles { database, files ->
            val photos = database.photos().filesOf(id)
            database.events().delete(id)
            photos.forEach(files::delete)
        }
    }

    /**
     * Keeps [taken], a photo for event [id], shrunk and encoded anew ([EventPhotos]), with the moment it is now, in a file
     * of Tracking's own, encrypted as it is written; the file first, so that no photo is ever without one.
     */
    suspend fun addPhoto(id: Long, taken: ByteArray): PhotoAdd {
        if (taken.size > EventPhotos.MAX_TAKEN_BYTES) return PhotoAdd.Unreadable
        // Before the database is held, so that nothing waits for a large photo to be encoded anew.
        val jpeg = withContext(Dispatchers.Default) { Photos.shrink(taken, EventPhotos.MAX_EDGE) } ?: return PhotoAdd.Unreadable
        return tracking.changingWithFiles { database, files ->
            if (database.events().get(id) == null) return@changingWithFiles PhotoAdd.NoEvent
            val photos = database.photos()
            if (photos.count(id) >= EventPhotos.MAX_PER_EVENT) return@changingWithFiles PhotoAdd.TooMany
            val file = files.newName()
            files.write(file, jpeg)
            try {
                PhotoAdd.Added(photos.insert(EventPhoto(eventId = id, takenAt = clock.instant().truncatedTo(ChronoUnit.SECONDS), file = file)))
            } catch (e: Exception) {
                files.delete(file)
                throw e
            }
        } ?: PhotoAdd.Locked
    }

    /** The photos of event [id], the first taken first; none once locked. */
    suspend fun photos(id: Long): List<PhotoInfo>? = tracking.withDatabase { it.photos().of(id) }

    /** The JPEG of photo [id]; none if gone, or once locked. */
    suspend fun photo(id: Long): ByteArray? = tracking.withFiles { database, files -> database.photos().file(id)?.let(files::read) }

    /** Deletes photo [id], its file too. */
    suspend fun deletePhoto(id: Long) {
        tracking.changingWithFiles { database, files ->
            val file = database.photos().file(id)
            database.photos().delete(id)
            file?.let(files::delete)
        }
    }

    /** Event [id] for its page; none if gone, or once locked. */
    suspend fun report(id: Long): EventReport? = tracking.withDatabase { database ->
        val event = database.events().get(id) ?: return@withDatabase null
        val medications = database.medications().all().associateBy { it.id }
        val intakes = database.events().intakes(id).mapNotNull { intake -> medications[intake.medicationId]?.let { it to intake.minutesBefore } }
        EventReport(event, intakes.sortedBy { it.second }, curveOf(database, event))
    }

    /**
     * Garmin's heart rates from [EventCurve.BEFORE] before [event] to [EventCurve.AFTER] after its end (or, not ended,
     * after its time and duration), each with its tier by the steps around it.
     */
    private suspend fun curveOf(database: TrackingDatabase, event: TrackingEvent): List<TieredBeat> {
        val from = event.at.minus(EventCurve.BEFORE)
        val end = event.endedAt ?: event.at.plus(Duration.ofMinutes((event.durationMinutes ?: 0).toLong()))
        val to = end.plus(EventCurve.AFTER)
        val margin = GarminTiers.REST_MARGIN.toMillis()
        val garmin = database.garmin()
        val steps = garmin.overlapping(SampleKind.Steps, from.toEpochMilli() - margin, to.toEpochMilli() + margin)
        val stepsKnownUntil = garmin.stepsUntilMillis()?.let(Instant::ofEpochMilli)
        return garmin.overlapping(SampleKind.HeartRate, from.toEpochMilli() - 1, to.toEpochMilli() + 1)
            .filter { !it.start.isBefore(from) && !it.start.isAfter(to) }
            .map { TieredBeat(it.start, it.value.toInt(), GarminTiers.tierOf(it, steps, stepsKnownUntil)) }
    }

    private fun TrackingEvent.withinLimits() = copy(
        at = at.truncatedTo(ChronoUnit.MINUTES),
        water = water.takeIf { kind.water },
        durationMinutes = durationMinutes.within(EventLimits.MINUTES),
        pulseBefore = pulseBefore.within(EntryLimits.PULSE),
        pulseAfter = pulseAfter.within(EntryLimits.PULSE),
        pulseFive = pulseFive.within(EntryLimits.PULSE),
        pulseTen = pulseTen.within(EntryLimits.PULSE),
        spo2 = spo2.within(EventLimits.SPO2),
        breathlessness = breathlessness.within(EntryLimits.LEVEL),
        dizziness = dizziness.within(EntryLimits.LEVEL),
        satDownAfterMinutes = satDownAfterMinutes.within(EventLimits.MINUTES),
        skinFadeMinutes = skinFadeMinutes.within(EventLimits.MINUTES),
        recoveredMinutes = recoveredMinutes.within(EventLimits.MINUTES),
    )

    private fun Int?.within(range: IntRange): Int? = this?.takeIf { it in range }
}
