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

    /** What it was asked for, each time. */
    val requests = mutableListOf<Set<String>>()

    override fun availability() = availability

    override suspend fun granted() = grantedNow

    override suspend fun read(from: Instant, to: Instant): List<GarminSample> {
        reads += from to to
        return samples.filter { it.start.isBefore(to) && !it.end.isBefore(from) }
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
