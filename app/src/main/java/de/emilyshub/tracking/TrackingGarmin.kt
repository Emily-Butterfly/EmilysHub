package de.emilyshub.tracking

import android.util.Log
import de.emilyshub.logged
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** How reading went: how many values were read, or why none. */
sealed interface GarminRead {
    /** [count] values were read, all there were. */
    data class Read(val count: Int) : GarminRead

    /** Health Connect cannot be read here, or its permissions are not granted. */
    data object NotSetUp : GarminRead

    /** Tracking locked meanwhile: what was read is not kept. */
    data object Locked : GarminRead

    /** Health Connect failed. */
    data object Failed : GarminRead
}

/**
 * Garmin's values from Health Connect in Tracking (SPEC.md §17): read only while Tracking is open, as it opens and by
 * "Jetzt lesen", never in the background; kept as copies in its database, so that they are in the backup, and read again
 * without doubling.
 */
class TrackingGarmin(private val tracking: TrackingStore, private val health: HealthSource, private val clock: Clock) {

    /** Whether Health Connect can be read here; asks Android, so not on every frame. */
    fun availability(): HealthAvailability = health.availability()

    /** What asks Health Connect for its permissions. */
    fun permissionRequest() = health.permissionRequest()

    /** Whether Tracking can read: Health Connect is there and at least one kind is granted. */
    suspend fun isSetUp(): Boolean =
        health.availability() == HealthAvailability.Available && HealthSource.kindsOf(grantedOrNone()).isNotEmpty()

    /**
     * Reads what is new, if Tracking is open and set up to: the kinds granted, in windows of [WINDOW], each kept as soon
     * as it is read, so that a reading cut off goes on from there next time. The first reading, and the first once the
     * permission for older values is granted, go back [FIRST_READ]; the others from the last, less [OVERLAP].
     */
    suspend fun read(): GarminRead {
        if (health.availability() != HealthAvailability.Available) return GarminRead.NotSetUp
        val granted = grantedOrNone()
        val kinds = HealthSource.kindsOf(granted)
        if (kinds.isEmpty()) return GarminRead.NotSetUp
        val now = clock.instant()
        val withHistory = HealthSource.HISTORY in granted
        val since = tracking.withDatabase { database -> sinceFor(database, now, withHistory) } ?: return GarminRead.Locked
        var start = since
        var count = 0
        while (start.isBefore(now)) {
            val end = minOf(start.plus(WINDOW), now)
            val samples = try {
                health.read(start, end, kinds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Reading Health Connect failed: ${e.logged}")
                return GarminRead.Failed
            }
            tracking.changing { database ->
                database.garmin().put(samples)
                database.settings().put(TrackingSetting(LAST_READ, end.toString()))
                if (withHistory) database.settings().put(TrackingSetting(HISTORY_READ, if (end == now) DONE else end.toString()))
            } ?: return GarminRead.Locked
            count += samples.size
            start = end
        }
        return GarminRead.Read(count)
    }

    /**
     * Where a reading [now] starts: where a reading of the older values stopped, if [withHistory] and it is not done;
     * else [FIRST_READ] back the first time, or the last reading less [OVERLAP].
     */
    private suspend fun sinceFor(database: TrackingDatabase, now: Instant, withHistory: Boolean): Instant {
        val history = database.settings().get(HISTORY_READ)
        val last = database.settings().get(LAST_READ)?.takeIf(String::isNotEmpty)?.let(Instant::parse)
        return when {
            withHistory && history != DONE -> history?.let(Instant::parse) ?: now.minus(FIRST_READ)
            last == null -> now.minus(FIRST_READ)
            else -> last.minus(OVERLAP)
        }
    }

    /** When it was last read; none before, or once locked. */
    suspend fun lastRead(): Instant? =
        tracking.withDatabase { it.settings().get(LAST_READ)?.takeIf(String::isNotEmpty)?.let(Instant::parse) }

    /** "Woher kommen meine Werte?": by kind, each app and what measured there, how many, and the last value; none once locked. */
    suspend fun sources(): List<Pair<SampleSource, GarminSample?>>? = tracking.withDatabase { database ->
        database.garmin().sources().map { it to database.garmin().last(it.kind, it.sourceApp, it.device) }
    }

    /** The tier of [sample], heart rates by the steps around them; none once locked. */
    suspend fun tierOf(sample: GarminSample): Tier? = tracking.withDatabase { database ->
        val margin = GarminTiers.REST_MARGIN.toMillis()
        val steps = database.garmin().overlapping(SampleKind.Steps, sample.startMillis - margin, sample.endMillis + margin)
        GarminTiers.tierOf(sample, steps, database.garmin().stepsUntilMillis()?.let(Instant::ofEpochMilli))
    }

    private suspend fun grantedOrNone(): Set<String> = try {
        health.granted()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Asking Health Connect for its permissions failed: ${e.logged}")
        emptySet()
    }

    companion object {
        /** How far back the first reading goes: as far as Health Connect gives, with the permission for older values. */
        val FIRST_READ: Duration = Duration.ofDays(365)

        /** How far before the last reading the next one starts, for values written there since. */
        val OVERLAP: Duration = Duration.ofDays(1)

        /** How much is read at once, and kept, before the next. */
        val WINDOW: Duration = Duration.ofDays(30)

        private const val LAST_READ = "garmin.last_read"

        /** How far the reading of the older values got, once their permission was granted, or [DONE]. */
        private const val HISTORY_READ = "garmin.history_read"
        private const val DONE = "done"
        private const val TAG = "EmilysHub"
    }
}
