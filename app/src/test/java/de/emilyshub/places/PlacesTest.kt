package de.emilyshub.places

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlacesTest {

    private val database = inMemoryDatabase()
    private val places = Places({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `places are listed by name, each with its WLANs as Android names them, empty and repeated ones left out`() = runBlocking {
        places.create(PlaceDraft(" Zuhause ", listOf("FritzBox 7590", " ", "FritzBox 7590", "Gast ")))
        places.create(PlaceDraft("Arbeit", listOf("Firma")))

        assertEquals(listOf("Arbeit" to listOf("Firma"), "Zuhause" to listOf("FritzBox 7590", "Gast ")), listed())
    }

    @Test
    fun `a place is renamed and its WLANs changed, and it can be deleted`() = runBlocking {
        val home = places.create(PlaceDraft("Zuhause", listOf("FritzBox")))
        places.create(PlaceDraft("Gym", listOf("FitX")))

        places.update(home, PlaceDraft("Daheim", listOf("Mesh", "FritzBox")))
        assertEquals(listOf("Daheim" to listOf("FritzBox", "Mesh"), "Gym" to listOf("FitX")), listed())

        places.delete(home)
        assertEquals(listOf("Gym" to listOf("FitX")), listed())
    }

    @Test
    fun `a place needs a name of up to 40 characters that no other place has, in whatever case`() {
        val work = PlaceWithWifis(Place(1, "Arbeit"), listOf(PlaceWifi(1, "Firma")))

        assertEquals(setOf(PlaceProblem.NameMissing), PlaceDraft(" ​ ", listOf("Mesh")).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.NameTooLong), PlaceDraft("a".repeat(41), listOf("Mesh")).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.NameTaken), PlaceDraft("ARBEIT", listOf("Mesh")).problems(listOf(work)))
        assertEquals(emptySet<PlaceProblem>(), PlaceDraft("Büro", listOf("Mesh")).problems(listOf(work)))
    }

    @Test
    fun `a place has up to 10 WLANs, each a name Android could give one, of up to 32 bytes`() {
        val ten = (1..10).map { "WLAN $it" }

        assertEquals(emptySet<PlaceProblem>(), PlaceDraft("Zuhause", ten).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.TooManyWifis), PlaceDraft("Zuhause", ten + "WLAN 11").problems(others = emptyList()))
        // "ä" takes two bytes: 16 of them fit, 17 do not.
        assertEquals(emptySet<PlaceProblem>(), PlaceDraft("Zuhause", listOf("ä".repeat(16))).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.WifiInvalid), PlaceDraft("Zuhause", listOf("ä".repeat(17))).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.WifiInvalid), PlaceDraft("Zuhause", listOf("Fritz\u0007Box")).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.WifiInvalid), PlaceDraft("Zuhause", listOf("\u200B")).problems(others = emptyList()))
    }

    @Test
    fun `a place is recognised by its WLANs, by a GPS area, or by both, and needs one of them`() {
        val noWifi = PlaceDraft("Gym", listOf(" "))

        assertEquals(setOf(PlaceProblem.WifiOrAreaMissing), noWifi.problems(others = emptyList()))
        assertEquals(emptySet<PlaceProblem>(), noWifi.copy(latitude = "52,52", longitude = "13,4").problems(others = emptyList()))
        val both = PlaceDraft("Zuhause", listOf("FritzBox"), latitude = "52,52", longitude = "13,4")
        assertEquals(emptySet<PlaceProblem>(), both.problems(others = emptyList()))
    }

    @Test
    fun `a GPS area has a latitude from -90 to 90 and a longitude from -180 to 180, both of them, in degrees`() {
        val gym = PlaceDraft("Gym", emptyList())
        fun problemsAt(latitude: String, longitude: String) = gym.copy(latitude = latitude, longitude = longitude).problems(emptyList())

        assertEquals(emptySet<PlaceProblem>(), problemsAt("90", "-180"))
        assertEquals(emptySet<PlaceProblem>(), problemsAt("-90,0", "180.0"))
        assertEquals(setOf(PlaceProblem.LatitudeInvalid), problemsAt("90,000001", "13,4"))
        assertEquals(setOf(PlaceProblem.LongitudeInvalid), problemsAt("52,5", "-180,5"))
        assertEquals(setOf(PlaceProblem.LatitudeInvalid), problemsAt("Berlin", "13,4"))
        assertEquals(setOf(PlaceProblem.LongitudeInvalid), problemsAt("52,5", " "))
        assertEquals(setOf(PlaceProblem.LatitudeInvalid), problemsAt("", "13,4"))
    }

    @Test
    fun `a GPS area has a radius of 50 to 500 m, 150 m unless chosen otherwise`() {
        val gym = PlaceDraft("Gym", emptyList(), latitude = "52,52", longitude = "13,4")

        assertEquals(150, gym.radius)
        assertEquals(emptySet<PlaceProblem>(), gym.copy(radius = 50).problems(others = emptyList()))
        assertEquals(emptySet<PlaceProblem>(), gym.copy(radius = 500).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.RadiusOutOfRange), gym.copy(radius = 49).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.RadiusOutOfRange), gym.copy(radius = 501).problems(others = emptyList()))
    }

    @Test
    fun `a place is saved with its GPS area, which comes back in the editor as typed, and can lose it again`() = runBlocking {
        val gym = places.create(PlaceDraft("Gym", emptyList(), latitude = " 52.520008", longitude = "13,404954 ", radius = 200))

        val saved = places.all().first().single()
        assertEquals(Area(latitude = 52.520008, longitude = 13.404954, radius = 200), saved.area)
        assertEquals(PlaceDraft("Gym", emptyList(), latitude = "52,520008", longitude = "13,404954", radius = 200), saved.toDraft())

        places.update(gym, PlaceDraft("Gym", listOf("FitX"), latitude = "", longitude = "", radius = 200))
        assertEquals(listOf(null), places.all().first().map { it.area })
        assertEquals(PlaceDraft("Gym", listOf("FitX")), places.all().first().single().toDraft())
    }

    @Test
    fun `the same WLAN can be at several places, which are all where one is with it`() = runBlocking {
        places.create(PlaceDraft("Uni", listOf("eduroam")))
        places.create(PlaceDraft("Arbeit", listOf("eduroam", "Firma")))

        assertEquals(listOf("Arbeit", "Uni"), places.all().first().placesOf(Wifi.Named("eduroam")).map { it.name })
    }

    @Test
    fun `a place with a problem is refused, its own name and WLANs not counting against it`() = runBlocking {
        val home = places.create(PlaceDraft("Zuhause", listOf("FritzBox")))
        places.create(PlaceDraft("Arbeit", listOf("Firma")))

        places.update(home, PlaceDraft("zuhause", listOf("FritzBox")))

        assertThrows(IllegalArgumentException::class.java) { runBlocking { places.create(PlaceDraft("Gym", listOf("\u0007"))) } }
        val takenName = PlaceDraft("Arbeit", listOf("FritzBox"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { places.update(home, takenName) } }
        assertEquals(listOf("Arbeit" to listOf("Firma"), "zuhause" to listOf("FritzBox")), listed())
    }

    @Test
    fun `the place one is at is the one with the WLAN one is connected to, the name matching exactly`() = runBlocking {
        places.create(PlaceDraft("Zuhause", listOf("FritzBox", "Mesh")))
        places.create(PlaceDraft("Arbeit", listOf("Firma")))
        val all = places.all().first()

        assertEquals(listOf("Zuhause"), all.placesOf(Wifi.Named("Mesh")).map { it.name })
        assertEquals(emptyList<String>(), all.placesOf(Wifi.Named("mesh")).map { it.name })
        assertEquals(emptyList<String>(), all.placesOf(Wifi.Named("Café")).map { it.name })
        assertEquals(emptyList<String>(), all.placesOf(Wifi.NameHidden).map { it.name })
        assertEquals(emptyList<String>(), all.placesOf(Wifi.None).map { it.name })
    }

    @Test
    fun `the WLAN name Android reports loses its quotes, and is none while Android keeps it back`() {
        assertEquals("FritzBox 7590", Ssid.of("\"FritzBox 7590\""))
        assertEquals(null, Ssid.of("<unknown ssid>"))
        assertEquals(null, Ssid.of(null))
        assertEquals(null, Ssid.of("\"\""))
        // A name that is no UTF-8 comes as hex digits, without quotes, and is taken as it is.
        assertEquals("c8f6", Ssid.of("c8f6"))
    }

    private suspend fun listed() = places.all().first().map { it.name to it.ssids }
}
