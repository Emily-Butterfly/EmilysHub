package de.emilyshub.tracking

import androidx.activity.result.contract.ActivityResultContract
import java.time.Instant

/** Whether Health Connect can be read here. */
enum class HealthAvailability {
    /** It can, once its permissions are granted. */
    Available,

    /** Health Connect is there but too old: it is to be updated first. */
    NeedsUpdate,

    /** Not here: before Android 14, or without Health Connect. Tracking works without. */
    Unavailable,
}

/**
 * Health Connect as Tracking reads it (SPEC.md §17, §11): only to read, never in the background, its values as
 * [GarminSample]s. The port to Android's; the tests have one of their own.
 */
interface HealthSource {
    /** Whether it can be read here. */
    fun availability(): HealthAvailability

    /** Those of [PERMISSIONS] granted. */
    suspend fun granted(): Set<String>

    /** The values from [from] to [to], of every kind Tracking reads. */
    suspend fun read(from: Instant, to: Instant): List<GarminSample>

    /** What asks for permissions: given those to ask, it ends in those granted. */
    fun permissionRequest(): ActivityResultContract<Set<String>, Set<String>>

    companion object {
        /** All Tracking reads (SPEC.md §11), and the right to read further back than 30 days, asked once. */
        val PERMISSIONS: Set<String> = setOf(
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_RESTING_HEART_RATE",
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_HEALTH_DATA_HISTORY",
        )

        /** Those without which nothing is read; the one for older values only reaches further back. */
        val NEEDED: Set<String> = PERMISSIONS - "android.permission.health.READ_HEALTH_DATA_HISTORY"
    }
}
