package de.emilyshub.tracking

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import java.time.Instant

/**
 * Stands in for Health Connect: as [availability] says, with the permissions [grantedNow], and the [samples] a test puts
 * there, given for the time asked. Asked for permissions, it grants [grantOnRequest].
 */
class FakeHealthSource : HealthSource {
    var availability = HealthAvailability.Available
    var grantedNow: Set<String> = emptySet()
    var grantOnRequest: Set<String> = HealthSource.PERMISSIONS
    val samples = mutableListOf<GarminSample>()

    /** The times asked for, from and to, each time it was read. */
    val reads = mutableListOf<Pair<Instant, Instant>>()

    /** The kinds asked for, each time it was read. */
    val kindsRead = mutableListOf<Set<SampleKind>>()

    /** If set, reading fails once it was read so many more times, as Health Connect's rate limit would. */
    var failAfter: Int? = null

    /** What it was asked for, each time. */
    val requests = mutableListOf<Set<String>>()

    override fun availability() = availability

    override suspend fun granted() = grantedNow

    override suspend fun read(from: Instant, to: Instant, kinds: Set<SampleKind>): List<GarminSample> {
        failAfter?.let { left ->
            check(left > 0) { "Rate limit" }
            failAfter = left - 1
        }
        reads += from to to
        kindsRead += kinds
        return samples.filter { it.kind in kinds && it.start.isBefore(to) && !it.start.isBefore(from) }
    }

    override fun permissionRequest() = object : ActivityResultContract<Set<String>, Set<String>>() {
        override fun createIntent(context: Context, input: Set<String>): Intent {
            requests += input
            return Intent(ACTION)
        }

        override fun getSynchronousResult(context: Context, input: Set<String>): SynchronousResult<Set<String>> {
            requests += input
            grantedNow = grantedNow + grantOnRequest
            return SynchronousResult(grantedNow)
        }

        override fun parseResult(resultCode: Int, intent: Intent?) = grantedNow
    }

    private companion object {
        const val ACTION = "de.emilyshub.test.HEALTH_PERMISSIONS"
    }
}
