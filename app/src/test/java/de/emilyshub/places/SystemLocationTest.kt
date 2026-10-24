package de.emilyshub.places

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** The position as Android's own location service tells it, without Google Play Services (SPEC.md §4). */
@RunWith(AndroidJUnit4::class)
class SystemLocationTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = shadowOf(app.getSystemService(LocationManager::class.java))

    @Before
    fun allowed() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        ShadowSystemClock.advanceBy(Duration.ofHours(1))
    }

    @Test
    fun `a position Android found in the last two minutes is taken at once, the newest one, as the device may have moved`() =
        runBlocking {
            manager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            val earlier = location(52.52, 13.4, accuracy = 8f, age = Duration.ofMinutes(1))
            manager.setLastKnownLocation(LocationManager.GPS_PROVIDER, earlier)
            val newer = location(52.53, 13.41, accuracy = 30f, age = Duration.ofSeconds(30))
            manager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, newer)

            assertEquals(Position(Coordinates(52.53, 13.41), 30.0), SystemLocation(app).position())
        }

    @Test
    fun `without one that recent, Android is asked where the device is now`() = runBlocking {
        manager.setLastKnownLocation(LocationManager.GPS_PROVIDER, location(52.0, 13.0, accuracy = 5f, age = Duration.ofMinutes(10)))

        val asked = async(Dispatchers.Unconfined) { SystemLocation(app).position() }
        manager.simulateLocation(location(52.52, 13.4, accuracy = 8f, age = Duration.ZERO))

        assertEquals(Position(Coordinates(52.52, 13.4), 8.0), asked.await())
    }

    @Test
    fun `there is none while Android finds none in time`() = runBlocking {
        assertEquals(null, SystemLocation(app, wait = Duration.ofMillis(50)).position())
    }

    @Test
    fun `there is none without the location allowed`() = runBlocking {
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        manager.setLastKnownLocation(LocationManager.GPS_PROVIDER, location(52.52, 13.4, accuracy = 8f, age = Duration.ZERO))

        assertEquals(null, SystemLocation(app).position())
    }

    /** What Android found by GPS [age] ago. */
    private fun location(latitude: Double, longitude: Double, accuracy: Float, age: Duration) =
        Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.accuracy = accuracy
            time = System.currentTimeMillis() - age.toMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - age.toNanos()
        }
}
