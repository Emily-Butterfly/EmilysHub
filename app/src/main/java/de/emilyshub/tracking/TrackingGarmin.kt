package de.emilyshub.tracking

import android.util.Log
import de.emilyshub.logged
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** How reading went: how many values were read, or why none. */
sealed interface GarminRead {
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
 * without doubling. The first reading goes back [FIRST_READ]; later ones from the last, with [OVERLAP], for values
 * written late.
 */
class TrackingGarmin(private val tracking: TrackingStore, private val health: HealthSource, private val clock: Clock) {

    /** Whether Health Connect can be read here. */
    fun availability(): HealthAvailability = health.availability()

    /** What asks Health Connect for its permissions. */
    fun permissionRequest() = health.permissionRequest()

    /** Whether Tracking can read: Health Connect is there and what it needs is granted. */
    suspend fun isSetUp(): Boolean =
        health.availability() == HealthAvailability.Available && grantedOrNone().containsAll(HealthSource.NEEDED)

    /** Reads what is new since the last reading, if Tracking is open and set up to. */
    suspend fun read(): GarminRead {
        if (!isSetUp()) return GarminRead.NotSetUp
        val now = clock.instant()
        // Empty before the first reading; null only once locked.
        val from = tracking.withDatabase { it.settings().get(LAST_READ).orEmpty() } ?: return GarminRead.Locked
        val since = from.takeIf { it.isNotEmpty() }?.let { Instant.parse(it).minus(OVERLAP) } ?: now.minus(FIRST_READ)
        val samples = try {
            health.read(since, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reading Health Connect failed: ${e.logged}")
            return GarminRead.Failed
        }
        return tracking.changing { database ->
            database.garmin().put(samples)
            database.settings().put(TrackingSetting(LAST_READ, now.toString()))
            GarminRead.Read(samples.size)
        } ?: GarminRead.Locked
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
        GarminTiers.tierOf(sample, steps)
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

        private const val LAST_READ = "garmin.last_read"
        private const val TAG = "EmilysHub"
    }
}
