package de.emilyshub.places

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/** Where the device is, as far as the app can tell by the WLAN it is connected to and its position (SPEC.md §4). */
sealed interface Whereabouts {
    /** At these places, or maybe at them, as Android keeps back what would tell. */
    data class At(val placeIds: Set<Long>) : Whereabouts

    /** Nothing is known: maybe at any place. */
    data object Unknown : Whereabouts

    /**
     * Whether a reminder bound to [placeIds] pops up here: at any of them, everywhere if bound to none, and where the
     * place is [Unknown], so that a reminder is never held back for want of a permission.
     */
    fun allows(placeIds: Set<Long>): Boolean = when (this) {
        Unknown -> true
        is At -> placeIds.isEmpty() || placeIds.any { it in this.placeIds }
    }

    companion object {
        /**
         * At the [places] that the WLAN connected to or the [position] tells the device is at, and maybe at those that
         * neither tells it is not at, as Android keeps back the WLAN's name or the position.
         */
        fun of(wifi: Wifi?, position: Position?, places: List<PlaceWithWifis>): Whereabouts =
            At(places.filter { it.mayBeHere(wifi, position) }.map { it.id }.toSet())

        private fun PlaceWithWifis.mayBeHere(wifi: Wifi?, position: Position?): Boolean {
            // What each way it is recognised by tells: here, not here, or nothing (null) as Android keeps it back.
            val told = buildList {
                if (wifis.isNotEmpty()) add(isAt(wifi))
                area?.let { add(position?.let(it::mayContain)) }
            }
            return true in told || false !in told
        }

        /** Whether [wifi] is one of the place's WLANs; nothing told without a WLAN name. */
        private fun PlaceWithWifis.isAt(wifi: Wifi?): Boolean? = when (wifi) {
            null, Wifi.NameHidden -> null
            Wifi.None -> false
            is Wifi.Named -> wifi.ssid in ssids
        }
    }
}

/**
 * Tells where the device is now, by the WLAN it is connected to, its position, and the [places] that have them. The
 * position is only asked for while a place has a GPS area, at the same time as the WLAN.
 */
class Locator(private val wifi: CurrentWifi, private val location: CurrentLocation, private val places: Places) {
    suspend fun whereabouts(): Whereabouts = coroutineScope {
        val all = places.all().first()
        val position = async { if (all.any { it.area != null }) location.position() else null }
        // Android reports a WLAN at once; should it not, the place is unknown rather than a reminder held back.
        val connected = withTimeoutOrNull(WAIT.toMillis()) { wifi.current().first() }
        Whereabouts.of(connected, position.await(), all)
    }

    private companion object {
        val WAIT: Duration = Duration.ofSeconds(5)
    }
}
