package de.emilyshub.places

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Where the device is, asking Android only for what the places in question are recognised by (SPEC.md §4). */
@RunWith(AndroidJUnit4::class)
class LocatorTest {

    private val database = inMemoryDatabase()
    private val places = Places({ database }, Dispatchers.Unconfined)
    private val wifi = FakeWifi()
    private var asked = 0
    private val location = object : CurrentLocation {
        override suspend fun position(): Position? {
            asked++
            return Position(Coordinates(52.520008, 13.404954), accuracy = 10.0)
        }
    }
    private val locator = Locator(wifi, location, places)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `the position is asked for only for places with a GPS area, which it then tells of`() = runBlocking {
        val home = places.create(PlaceDraft("Zuhause", listOf("FritzBox")))
        val gym = places.create(PlaceDraft("Gym", emptyList()).withCenter(Coordinates(52.520008, 13.404954)))
        wifi.ssid = "FritzBox"

        assertEquals(Whereabouts.At(setOf(home)), locator.whereabouts(setOf(home)))
        assertEquals(0, asked)

        assertEquals(Whereabouts.At(setOf(home, gym)), locator.whereabouts(setOf(home, gym)))
        assertEquals(1, asked)
    }

    @Test
    fun `places not in question are not told of`() = runBlocking {
        val home = places.create(PlaceDraft("Zuhause", listOf("FritzBox")))
        places.create(PlaceDraft("Gym", emptyList()).withCenter(Coordinates(52.520008, 13.404954)))
        wifi.ssid = "FritzBox"

        assertEquals(Whereabouts.At(setOf(home)), locator.whereabouts(setOf(home)))
    }
}
