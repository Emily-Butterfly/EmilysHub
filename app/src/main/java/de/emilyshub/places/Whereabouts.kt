package de.emilyshub.places

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/** Where the device is, as far as the app can tell by the WLAN it is connected to (SPEC.md §4). */
sealed interface Whereabouts {
    /** At these places; at none without a WLAN, or with one that no place has. */
    data class At(val placeIds: Set<Long>) : Whereabouts

    /** Android does not tell the WLAN's name, say without the location allowed in the background. */
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
        fun of(wifi: Wifi, places: List<PlaceWithWifis>): Whereabouts = when (wifi) {
            Wifi.NameHidden -> Unknown
            Wifi.None, is Wifi.Named -> At(places.placesOf(wifi).map { it.id }.toSet())
        }
    }
}

/** Tells where the device is now, by the WLAN it is connected to and the [places] that have it. */
class Locator(private val wifi: CurrentWifi, private val places: Places) {
    suspend fun whereabouts(): Whereabouts {
        // Android reports a WLAN at once; should it not, the place is unknown rather than a reminder held back.
        val connected = withTimeoutOrNull(WAIT.toMillis()) { wifi.current().first() } ?: return Whereabouts.Unknown
        return Whereabouts.of(connected, places.all().first())
    }

    private companion object {
        val WAIT: Duration = Duration.ofSeconds(5)
    }
}
