package de.emilyshub.places

import org.junit.Assert.assertEquals
import org.junit.Test

class WhereaboutsTest {

    private val uni = PlaceWithWifis(Place(1, "Uni"), listOf(PlaceWifi(1, "eduroam")))
    private val work = PlaceWithWifis(Place(2, "Arbeit"), listOf(PlaceWifi(2, "eduroam"), PlaceWifi(2, "Firma")))

    @Test
    fun `the device is at the places with the WLAN it is connected to, at none without one, unknown while its name is kept back`() {
        assertEquals(Whereabouts.At(setOf(1L, 2L)), Whereabouts.of(Wifi.Named("eduroam"), listOf(uni, work)))
        assertEquals(Whereabouts.At(setOf(2L)), Whereabouts.of(Wifi.Named("Firma"), listOf(uni, work)))
        assertEquals(Whereabouts.At(emptySet()), Whereabouts.of(Wifi.Named("Café"), listOf(uni, work)))
        assertEquals(Whereabouts.At(emptySet()), Whereabouts.of(Wifi.None, listOf(uni, work)))
        assertEquals(Whereabouts.Unknown, Whereabouts.of(Wifi.NameHidden, listOf(uni, work)))
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
