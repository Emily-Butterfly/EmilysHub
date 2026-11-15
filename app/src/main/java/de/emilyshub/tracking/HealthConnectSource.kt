package de.emilyshub.tracking

import android.content.Context
import android.os.Build
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

/** Android's Health Connect, from Android 14, where Garmin Connect writes to it. */
class HealthConnectSource(private val context: Context) : HealthSource {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override fun availability(): HealthAvailability = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> HealthAvailability.Unavailable
        else -> when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NeedsUpdate
            else -> HealthAvailability.Unavailable
        }
    }

    override suspend fun granted(): Set<String> = client.permissionController.getGrantedPermissions()

    override suspend fun read(from: Instant, to: Instant): List<GarminSample> =
        listOf(HeartRateRecord::class, RestingHeartRateRecord::class, StepsRecord::class, SleepSessionRecord::class)
            .flatMap { readAll(it, from, to) }
            .flatMap(HealthRecords::samplesOf)

    override fun permissionRequest(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    /** All records of [type] from [from] to [to], page by page. */
    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val records = mutableListOf<T>()
        var page: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = page))
            records += response.records
            page = response.pageToken
        } while (page != null)
        return records
    }
}

/** How Health Connect's records become [GarminSample]s; apart, for the tests to give it records of their own. */
object HealthRecords {
    /** The values in [record]: one per heart rate of a [HeartRateRecord], one per night and per stage of a night's sleep. */
    fun samplesOf(record: Record): List<GarminSample> {
        val metadata = record.metadata
        fun sample(key: String, kind: SampleKind, start: Instant, end: Instant, value: Long) = GarminSample.of(
            key = key,
            kind = kind,
            start = start,
            end = end,
            value = value,
            sourceApp = metadata.dataOrigin.packageName,
            device = deviceOf(metadata),
            model = metadata.device?.model,
        )
        val id = metadata.id
        return when (record) {
            is HeartRateRecord -> record.samples.mapIndexed { index, it ->
                sample("$id#$index", SampleKind.HeartRate, it.time, it.time, it.beatsPerMinute)
            }
            is RestingHeartRateRecord -> listOf(sample(id, SampleKind.RestingHeartRate, record.time, record.time, record.beatsPerMinute))
            is StepsRecord -> listOf(sample(id, SampleKind.Steps, record.startTime, record.endTime, record.count))
            is SleepSessionRecord -> listOf(
                sample(id, SampleKind.Sleep, record.startTime, record.endTime, Duration.between(record.startTime, record.endTime).toMinutes()),
            ) + record.stages.mapIndexed { index, stage ->
                sample("$id#$index", SampleKind.SleepStage, stage.startTime, stage.endTime, stage.stage.toLong())
            }
            else -> emptyList()
        }
    }

    /** What measured it, as the writing app says: a watch, a chest strap, or unknown, also when it says nothing. */
    fun deviceOf(metadata: Metadata): DeviceKind = when (metadata.device?.type) {
        Device.TYPE_WATCH -> DeviceKind.Watch
        Device.TYPE_CHEST_STRAP -> DeviceKind.ChestStrap
        else -> DeviceKind.Unknown
    }
}
