package de.emilyshub.places

import org.junit.Assert.assertEquals
import org.junit.Test

class WhereaboutsTest {

    private val uni = PlaceWithWifis(Place(1, "Uni"), listOf(PlaceWifi(1, "eduroam")))
    private val work = PlaceWithWifis(Place(2, "Arbeit"), listOf(PlaceWifi(2, "eduroam"), PlaceWifi(2, "Firma")))
    private val gym = PlaceWithWifis(Place(3, "Gym", Area(Coordinates(52.52, 13.4), radius = 150)), emptyList())
    private val home =
        PlaceWithWifis(Place(4, "Zuhause", Area(Coordinates(52.5, 13.3), radius = 150)), listOf(PlaceWifi(4, "FritzBox")))

    @Test
    fun `the device is at the places with the WLAN connected to, at none without one, maybe at any while its name is kept back`() {
        assertEquals(Whereabouts.At(setOf(1L, 2L)), Whereabouts.of(Wifi.Named("eduroam"), null, listOf(uni, work)))
        assertEquals(Whereabouts.At(setOf(2L)), Whereabouts.of(Wifi.Named("Firma"), null, listOf(uni, work)))
        assertEquals(Whereabouts.At(emptySet()), Whereabouts.of(Wifi.Named("Café"), null, listOf(uni, work)))
        assertEquals(Whereabouts.At(emptySet()), Whereabouts.of(Wifi.None, null, listOf(uni, work)))
        assertEquals(Whereabouts.At(setOf(1L, 2L), surely = emptySet()), Whereabouts.of(Wifi.NameHidden, null, listOf(uni, work)))
        assertEquals(Whereabouts.At(setOf(1L, 2L), surely = emptySet()), Whereabouts.of(wifi = null, null, listOf(uni, work)))
    }

    @Test
    fun `the device is at the GPS places whose area its position lies within, and maybe at any while it has none`() {
        assertEquals(Whereabouts.At(setOf(3L)), Whereabouts.of(Wifi.None, Position(Coordinates(52.5205, 13.4), 10.0), listOf(gym)))
        assertEquals(Whereabouts.At(emptySet()), Whereabouts.of(Wifi.None, Position(Coordinates(52.6, 13.4), 10.0), listOf(gym)))
        assertEquals(Whereabouts.At(setOf(3L), surely = emptySet()), Whereabouts.of(Wifi.None, position = null, listOf(gym)))
    }

    @Test
    fun `at a place with a WLAN and a GPS area, either tells it is there, and it is not there only if one tells so`() {
        val away = Position(Coordinates(52.6, 13.4), 10.0)
        val atHome = Position(Coordinates(52.5, 13.3), 10.0)

        assertEquals(Whereabouts.At(setOf(4L)), Whereabouts.of(Wifi.Named("FritzBox"), away, listOf(home)))
        assertEquals(Whereabouts.At(setOf(4L)), Whereabouts.of(Wifi.None, atHome, listOf(home)))
        assertEquals(Whereabouts.At(emptySet()), Whereabouts.of(Wifi.NameHidden, away, listOf(home)))
        assertEquals(Whereabouts.At(emptySet()), Whereabouts.of(Wifi.None, position = null, listOf(home)))
        assertEquals(Whereabouts.At(setOf(4L), surely = emptySet()), Whereabouts.of(Wifi.NameHidden, position = null, listOf(home)))
    }

    @Test
    fun `the device is surely at a place only where the WLAN or the position tells so, not where it only may be`() {
        assertEquals(true, Whereabouts.of(Wifi.Named("Firma"), null, listOf(uni, work)).surelyAt(setOf(1L, 2L)))
        assertEquals(false, Whereabouts.of(Wifi.NameHidden, null, listOf(uni, work)).surelyAt(setOf(1L)))
        val imprecise = Position(Coordinates(52.5205, 13.4), accuracy = 2_000.0)
        assertEquals(false, Whereabouts.of(Wifi.None, imprecise, listOf(gym)).surelyAt(setOf(3L)))
        assertEquals(true, Whereabouts.of(Wifi.None, Position(Coordinates(52.5205, 13.4), 10.0), listOf(gym)).surelyAt(setOf(3L)))
        assertEquals(false, Whereabouts.Unknown.surelyAt(setOf(3L)))
    }

    @Test
    fun `a reminder bound to places pops up at any of them, one bound to none everywhere, and either where the place is unknown`() {
        assertEquals(true, Whereabouts.At(setOf(1L)).allows(setOf(1L, 2L)))
        assertEquals(false, Whereabouts.At(setOf(3L)).allows(setOf(1L, 2L)))
        assertEquals(false, Whereabouts.At(emptySet()).allows(setOf(1L)))
        assertEquals(true, Whereabouts.At(emptySet()).allows(emptySet()))
        assertEquals(true, Whereabouts.Unknown.allows(setOf(1L)))
    }
}
