package de.emilyshub.tracking

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneOffset

/** How Health Connect's records become Tracking's values, from records made up as Garmin Connect would write them. */
@RunWith(AndroidJUnit4::class)
class HealthRecordsTest {

    private val start = Instant.parse("2026-11-15T10:00:00Z")
    private val offset = ZoneOffset.ofHours(1)

    /** Health Connect sets the app a record came from itself; the public factories leave it empty. */
    private fun metadata(id: String, device: Device?) =
        if (device == null) Metadata.unknownRecordingMethodWithId(id) else Metadata.autoRecordedWithId(id, device)

    @Test
    fun `each heart rate of a record is a value of its own, keyed by the record and its place`() {
        val record = HeartRateRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = start.plusSeconds(120),
            endZoneOffset = offset,
            samples = listOf(HeartRateRecord.Sample(start, 64), HeartRateRecord.Sample(start.plusSeconds(60), 66)),
            metadata = metadata("hr-1", Device(Device.TYPE_WATCH, "Garmin", "Uhr A")),
        )

        val samples = HealthRecords.samplesOf(record)

        assertEquals(listOf("hr-1#0", "hr-1#1"), samples.map { it.key })
        assertEquals(listOf(64L, 66L), samples.map { it.value })
        assertEquals(start.plusSeconds(60), samples[1].start)
        assertEquals(DeviceKind.Watch, samples[0].device)
        assertEquals("Uhr A", samples[0].model)
        assertEquals("", samples[0].sourceApp)
    }

    @Test
    fun `a chest strap is told by the device type, and missing metadata are unknown`() {
        val strap = RestingHeartRateRecord(start, offset, 58, metadata("rhr", Device(Device.TYPE_CHEST_STRAP, null, null)))
        val none = StepsRecord(start, offset, start.plusSeconds(900), offset, 120, metadata("steps", null))
        val phone = StepsRecord(start, offset, start.plusSeconds(900), offset, 120, metadata("steps-2", Device(Device.TYPE_PHONE, null, null)))

        assertEquals(DeviceKind.ChestStrap, HealthRecords.samplesOf(strap).single().device)
        assertEquals(DeviceKind.Unknown, HealthRecords.samplesOf(none).single().device)
        assertEquals(DeviceKind.Unknown, HealthRecords.samplesOf(phone).single().device)
    }

    @Test
    fun `steps keep their span and count, a night its length in minutes and each stage`() {
        val steps = StepsRecord(start, offset, start.plusSeconds(900), offset, 120, metadata("steps", null))
        val end = start.plusSeconds(7 * 3600 + 30 * 60)
        val night = SleepSessionRecord(
            startTime = start,
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = offset,
            metadata = metadata("night", Device(Device.TYPE_WATCH, null, null)),
            stages = listOf(
                SleepSessionRecord.Stage(start, start.plusSeconds(3600), SleepSessionRecord.STAGE_TYPE_LIGHT),
                SleepSessionRecord.Stage(start.plusSeconds(3600), end, SleepSessionRecord.STAGE_TYPE_DEEP),
            ),
        )

        val counted = HealthRecords.samplesOf(steps).single()
        assertEquals(SampleKind.Steps, counted.kind)
        assertEquals(120L, counted.value)
        assertEquals(start.plusSeconds(900), counted.end)
        val slept = HealthRecords.samplesOf(night)
        assertEquals(listOf(SampleKind.Sleep, SampleKind.SleepStage, SampleKind.SleepStage), slept.map { it.kind })
        assertEquals(450L, slept[0].value)
        assertEquals(listOf("night", "night#0", "night#1"), slept.map { it.key })
        assertEquals(SleepSessionRecord.STAGE_TYPE_DEEP.toLong(), slept[2].value)
    }
}
