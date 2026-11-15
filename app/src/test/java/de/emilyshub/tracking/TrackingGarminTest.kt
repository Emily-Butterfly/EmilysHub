package de.emilyshub.tracking

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/** Garmin's values read into Tracking (SPEC.md §17): only while open, as copies, again without doubling. Made-up values. */
@RunWith(AndroidJUnit4::class)
class TrackingGarminTest {

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext())
    private val health = fixture.health
    private val garmin = fixture.garmin
    private val now = Instant.parse("2026-11-15T10:00:00Z")

    @After
    fun close() = fixture.close()

    private fun heartRate(key: String, at: Instant, value: Long = 70) =
        GarminSample.of(key, SampleKind.HeartRate, at, at, value, GARMIN, DeviceKind.Watch)

    private fun count() = checkNotNull(runBlocking { fixture.tracking.withDatabase { it.garmin().count() } })

    @Test
    fun `without its permissions or without Health Connect nothing is read`() {
        assertEquals(GarminRead.NotSetUp, runBlocking { garmin.read() })

        health.grantedNow = HealthSource.PERMISSIONS
        health.availability = HealthAvailability.Missing
        assertEquals(GarminRead.NotSetUp, runBlocking { garmin.read() })
        assertTrue(health.reads.isEmpty())
    }

    @Test
    fun `the first reading goes back a year in windows, later ones from the last, and reading again adds without doubling`() {
        health.grantedNow = HealthSource.PERMISSIONS
        health.samples += heartRate("hr#0", now.minusSeconds(3600))

        assertEquals(GarminRead.Read(1), runBlocking { garmin.read() })
        assertEquals(now.minus(TrackingGarmin.FIRST_READ), health.reads.first().first)
        assertTrue(health.reads.all { (from, to) -> Duration.between(from, to) <= TrackingGarmin.WINDOW })
        assertEquals(now, health.reads.last().second)

        health.reads.clear()
        health.samples += heartRate("hr#1", now.minusSeconds(1800))
        runBlocking { garmin.read() }

        assertEquals(now.minus(TrackingGarmin.OVERLAP), health.reads.single().first)
        assertEquals(2, count())
        assertEquals(now, runBlocking { garmin.lastRead() })
    }

    @Test
    fun `a reading cut off by Health Connect keeps what it read and goes on from there next time`() {
        health.grantedNow = HealthSource.PERMISSIONS
        health.samples += heartRate("old#0", now.minus(Duration.ofDays(300)))
        health.samples += heartRate("new#0", now.minusSeconds(60))
        health.failAfter = 3

        assertEquals(GarminRead.Failed, runBlocking { garmin.read() })
        assertEquals(1, count())

        health.failAfter = null
        health.reads.clear()
        runBlocking { garmin.read() }

        assertEquals(now.minus(TrackingGarmin.FIRST_READ).plus(TrackingGarmin.WINDOW.multipliedBy(3)), health.reads.first().first)
        assertEquals(2, count())
    }

    @Test
    fun `granted later, the permission for older values reads the year once more`() {
        health.grantedNow = HealthSource.NEEDED
        runBlocking { garmin.read() }
        health.samples += heartRate("old#0", now.minus(Duration.ofDays(200)))
        health.grantedNow = HealthSource.PERMISSIONS
        health.reads.clear()

        runBlocking { garmin.read() }
        assertEquals(now.minus(TrackingGarmin.FIRST_READ), health.reads.first().first)
        assertEquals(1, count())

        health.reads.clear()
        runBlocking { garmin.read() }
        assertEquals("once", now.minus(TrackingGarmin.OVERLAP), health.reads.single().first)
    }

    @Test
    fun `only the kinds granted are read, the others not`() {
        health.grantedNow = setOf("android.permission.health.READ_HEART_RATE")
        health.samples += heartRate("hr#0", now.minusSeconds(60))

        assertEquals(GarminRead.Read(1), runBlocking { garmin.read() })
        assertEquals(setOf(SampleKind.HeartRate), health.kindsRead.first())
        assertTrue(runBlocking { garmin.isSetUp() })
    }

    @Test
    fun `a value written anew in Health Connect takes the place of its copy`() {
        health.grantedNow = HealthSource.PERMISSIONS
        health.samples += heartRate("hr#0", now.minusSeconds(60), value = 70)
        runBlocking { garmin.read() }

        health.samples[0] = heartRate("hr#0", now.minusSeconds(60), value = 72)
        runBlocking { garmin.read() }

        assertEquals(1, count())
        val source = checkNotNull(runBlocking { garmin.sources() }).single()
        assertEquals(72L, source.second?.value)
    }

    @Test
    fun `a record written anew with fewer values leaves none of its old ones`() {
        health.grantedNow = HealthSource.PERMISSIONS
        health.samples += listOf(heartRate("hr#0", now.minusSeconds(120)), heartRate("hr#1", now.minusSeconds(60)))
        runBlocking { garmin.read() }

        health.samples.removeAt(1)
        runBlocking { garmin.read() }

        assertEquals(1, count())
    }

    @Test
    fun `while Tracking is locked nothing is read or kept`() {
        health.grantedNow = HealthSource.NEEDED
        health.samples += heartRate("hr#0", now.minusSeconds(60))
        fixture.tracking.lock()

        assertEquals(GarminRead.Locked, runBlocking { garmin.read() })
        assertTrue(health.reads.isEmpty())
    }

    @Test
    fun `the sources say by kind which app and device, how many, and the last value`() {
        health.grantedNow = HealthSource.NEEDED
        health.samples += heartRate("hr#0", now.minusSeconds(120), value = 64)
        health.samples += heartRate("hr#1", now.minusSeconds(60), value = 66)
        health.samples += GarminSample.of("strap#0", SampleKind.HeartRate, now.minusSeconds(30), now.minusSeconds(30), 90, GARMIN, DeviceKind.ChestStrap)
        health.samples += GarminSample.of("steps", SampleKind.Steps, now.minusSeconds(900), now.minusSeconds(600), 300, "com.example.phone", DeviceKind.Unknown)
        runBlocking { garmin.read() }

        val sources = checkNotNull(runBlocking { garmin.sources() })

        val watch = sources.single { it.first.kind == SampleKind.HeartRate && it.first.device == DeviceKind.Watch }
        assertEquals(2, watch.first.count)
        assertEquals(66L, watch.second?.value)
        assertTrue(sources.any { it.first.kind == SampleKind.HeartRate && it.first.device == DeviceKind.ChestStrap })
        assertEquals("com.example.phone", sources.single { it.first.kind == SampleKind.Steps }.first.sourceApp)
    }

    @Test
    fun `a heart rate's tier is decided by the steps kept around it`() {
        health.grantedNow = HealthSource.NEEDED
        val resting = heartRate("hr#0", now.minus(Duration.ofHours(2)))
        val walking = heartRate("hr#1", now.minusSeconds(600))
        health.samples += listOf(resting, walking)
        health.samples += GarminSample.of("steps", SampleKind.Steps, now.minusSeconds(660), now.minusSeconds(540), 200, GARMIN, DeviceKind.Watch)
        runBlocking { garmin.read() }

        assertEquals(Tier.A, runBlocking { garmin.tierOf(resting) })
        assertEquals(Tier.B, runBlocking { garmin.tierOf(walking) })
    }

    private companion object {
        const val GARMIN = "com.garmin.android.apps.connectmobile"
    }
}
