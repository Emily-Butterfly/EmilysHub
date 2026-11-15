package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The tiers of Garmin's values (SPEC.md §17, "Stufen"), as pure logic. */
class GarminTiersTest {

    private val at = Instant.parse("2026-11-15T10:00:00Z")

    /** Steps came in up to well after the heart rate. */
    private val known = at.plusSeconds(3600)

    private fun heartRate(device: DeviceKind = DeviceKind.Watch) =
        GarminSample.of("hr", SampleKind.HeartRate, at, at, 72, "com.garmin.android.apps.connectmobile", device)

    private fun steps(from: String, to: String, count: Long = 30) =
        GarminSample.of("steps $from", SampleKind.Steps, Instant.parse(from), Instant.parse(to), count, "com.garmin.android.apps.connectmobile", DeviceKind.Watch)

    @Test
    fun `a heart rate without steps in the minute before and after is at rest, A`() {
        assertEquals(Tier.A, GarminTiers.tierOf(heartRate(), emptyList(), known))
    }

    @Test
    fun `steps within the minute before or after make a heart rate from the wrist B`() {
        assertEquals(Tier.B, GarminTiers.tierOf(heartRate(), listOf(steps("2026-11-15T09:58:30Z", "2026-11-15T09:59:01Z")), known))
        assertEquals(Tier.B, GarminTiers.tierOf(heartRate(), listOf(steps("2026-11-15T10:00:59Z", "2026-11-15T10:05:00Z")), known))
        assertEquals(Tier.B, GarminTiers.tierOf(heartRate(DeviceKind.Unknown), listOf(steps("2026-11-15T09:59:30Z", "2026-11-15T10:00:30Z")), known))
    }

    @Test
    fun `at the boundaries of the minute the steps count no more`() {
        assertEquals(Tier.A, GarminTiers.tierOf(heartRate(), listOf(steps("2026-11-15T09:55:00Z", "2026-11-15T09:59:00Z")), known))
        assertEquals(Tier.A, GarminTiers.tierOf(heartRate(), listOf(steps("2026-11-15T10:01:00Z", "2026-11-15T10:05:00Z")), known))
        assertEquals("no steps counted", Tier.A, GarminTiers.tierOf(heartRate(), listOf(steps("2026-11-15T09:59:30Z", "2026-11-15T10:00:30Z", count = 0)), known))
    }

    @Test
    fun `where no steps came in yet for its time, a heart rate from the wrist stays B`() {
        assertEquals(Tier.B, GarminTiers.tierOf(heartRate(), emptyList(), stepsKnownUntil = null))
        assertEquals(Tier.B, GarminTiers.tierOf(heartRate(), emptyList(), stepsKnownUntil = at.plusSeconds(59)))
        assertEquals(Tier.A, GarminTiers.tierOf(heartRate(), emptyList(), stepsKnownUntil = at.plusSeconds(60)))
        assertEquals("a chest strap all the same", Tier.A, GarminTiers.tierOf(heartRate(DeviceKind.ChestStrap), emptyList(), null))
    }

    @Test
    fun `a heart rate from a chest strap is A also in motion`() {
        assertEquals(Tier.A, GarminTiers.tierOf(heartRate(DeviceKind.ChestStrap), listOf(steps("2026-11-15T09:59:30Z", "2026-11-15T10:00:30Z")), known))
    }

    @Test
    fun `steps are A, Garmin's resting heart rate and the length of sleep B, the stages of sleep C`() {
        assertEquals(Tier.A, GarminTiers.ruleOf(SampleKind.Steps).tier)
        assertEquals(Tier.B, GarminTiers.ruleOf(SampleKind.RestingHeartRate).tier)
        assertEquals(Tier.B, GarminTiers.ruleOf(SampleKind.Sleep).tier)
        assertEquals(Tier.C, GarminTiers.ruleOf(SampleKind.SleepStage).tier)
    }

    @Test
    fun `each rule names its sources`() {
        SampleKind.entries.forEach { kind -> assert(GarminTiers.ruleOf(kind).sources.isNotEmpty()) { "$kind has no source" } }
        assertEquals(listOf("GARMIN-HF"), GarminTiers.HEART_RATE_AT_REST.sources.map { it.code })
        assertEquals(listOf("GARMIN-HRV"), GarminTiers.ruleOf(SampleKind.RestingHeartRate).sources.map { it.code })
    }
}
