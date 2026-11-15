package de.emilyshub.tracking

import androidx.activity.result.contract.ActivityResultContract
import java.time.Instant

/** Whether Health Connect can be read here. */
enum class HealthAvailability {
    /** It can, once its permissions are granted. */
    Available,

    /** Health Connect is there but too old: it is to be updated first. */
    NeedsUpdate,

    /** Before Android 14, where Garmin Connect writes nothing to Health Connect. Tracking works without. */
    TooOld,

    /** Without Health Connect. Tracking works without. */
    Missing,
}

/**
 * Health Connect as Tracking reads it (SPEC.md §17, §11): only to read, never in the background, what Garmin Connect
 * ([GARMIN_CONNECT]) wrote there, as [GarminSample]s. The port to Android's; the tests have one of their own.
 */
interface HealthSource {
    /** Whether it can be read here. */
    fun availability(): HealthAvailability

    /** Those of [PERMISSIONS] granted. */
    suspend fun granted(): Set<String>

    /** The values of [kinds] that start from [from] up to [to]. */
    suspend fun read(from: Instant, to: Instant, kinds: Set<SampleKind>): List<GarminSample>

    /** What asks for permissions: given those to ask, it ends in those granted. */
    fun permissionRequest(): ActivityResultContract<Set<String>, Set<String>>

    companion object {
        /** The app whose values Tracking reads: Garmin Connect. */
        const val GARMIN_CONNECT = "com.garmin.android.apps.connectmobile"

        /** The right to read further back than 30 days, asked once with the others. */
        const val HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

        /** What each permission lets Tracking read (SPEC.md §11): each read as far as it is granted. */
        val KINDS: Map<String, Set<SampleKind>> = mapOf(
            "android.permission.health.READ_HEART_RATE" to setOf(SampleKind.HeartRate),
            "android.permission.health.READ_RESTING_HEART_RATE" to setOf(SampleKind.RestingHeartRate),
            "android.permission.health.READ_STEPS" to setOf(SampleKind.Steps),
            "android.permission.health.READ_SLEEP" to setOf(SampleKind.Sleep, SampleKind.SleepStage),
        )

        /** Those of the kinds read. */
        val NEEDED: Set<String> = KINDS.keys

        /** All Tracking asks for: those of the kinds and the one for older values. */
        val PERMISSIONS: Set<String> = NEEDED + HISTORY

        /** The kinds [granted] lets Tracking read. */
        fun kindsOf(granted: Set<String>): Set<SampleKind> = KINDS.filterKeys { it in granted }.values.flatten().toSet()
    }
}
