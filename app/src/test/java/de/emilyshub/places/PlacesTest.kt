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
    fun `places are listed by name, each with its WLANs, empty and repeated ones left out`() = runBlocking {
        places.create(PlaceDraft(" Zuhause ", listOf("FritzBox 7590", " ", "FritzBox 7590 ", "Gast")))
        places.create(PlaceDraft("Arbeit", listOf("Firma")))

        assertEquals(listOf("Arbeit" to listOf("Firma"), "Zuhause" to listOf("FritzBox 7590", "Gast")), listed())
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
    fun `a place has 1 to 10 WLANs, each with a name of up to 32 bytes that no other place has`() {
        val work = PlaceWithWifis(Place(1, "Arbeit"), listOf(PlaceWifi(1, "Firma")))
        val ten = (1..10).map { "WLAN $it" }

        assertEquals(setOf(PlaceProblem.WifisMissing), PlaceDraft("Zuhause", listOf(" ")).problems(others = emptyList()))
        assertEquals(emptySet<PlaceProblem>(), PlaceDraft("Zuhause", ten).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.TooManyWifis), PlaceDraft("Zuhause", ten + "WLAN 11").problems(others = emptyList()))
        // "ä" takes two bytes: 16 of them fit, 17 do not.
        assertEquals(emptySet<PlaceProblem>(), PlaceDraft("Zuhause", listOf("ä".repeat(16))).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.WifiTooLong), PlaceDraft("Zuhause", listOf("ä".repeat(17))).problems(others = emptyList()))
        assertEquals(setOf(PlaceProblem.WifiTaken), PlaceDraft("Zuhause", listOf("Firma")).problems(listOf(work)))
        assertEquals("Arbeit", PlaceDraft("Zuhause", listOf("Firma")).ownerOf("Firma", listOf(work))?.place?.name)
    }

    @Test
    fun `a place with a problem is refused, its own name and WLANs not counting against it`() = runBlocking {
        val home = places.create(PlaceDraft("Zuhause", listOf("FritzBox")))
        places.create(PlaceDraft("Arbeit", listOf("Firma")))

        places.update(home, PlaceDraft("zuhause", listOf("FritzBox")))

        assertThrows(IllegalArgumentException::class.java) { runBlocking { places.create(PlaceDraft("Gym", listOf("Firma"))) } }
        val takenName = PlaceDraft("Arbeit", listOf("FritzBox"))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { places.update(home, takenName) } }
        assertEquals(listOf("Arbeit" to listOf("Firma"), "zuhause" to listOf("FritzBox")), listed())
    }

    @Test
    fun `the place one is at is the one with the WLAN one is connected to, the name matching exactly`() = runBlocking {
        places.create(PlaceDraft("Zuhause", listOf("FritzBox", "Mesh")))
        places.create(PlaceDraft("Arbeit", listOf("Firma")))
        val all = places.all().first()

        assertEquals("Zuhause", all.placeOf("Mesh")?.place?.name)
        assertEquals(null, all.placeOf("mesh"))
        assertEquals(null, all.placeOf("Café"))
        assertEquals(null, all.placeOf(null))
    }

    @Test
    fun `the WLAN name Android reports loses its quotes, and is none while Android keeps it back`() {
        assertEquals("FritzBox 7590", ssidOf("\"FritzBox 7590\""))
        assertEquals(null, ssidOf("<unknown ssid>"))
        assertEquals(null, ssidOf(null))
        assertEquals(null, ssidOf("\"\""))
        // A name that is no UTF-8 comes as hex digits, without quotes, and is taken as it is.
        assertEquals("c8f6", ssidOf("c8f6"))
    }

    private suspend fun listed() = places.all().first().map { it.place.name to it.ssids }
}
