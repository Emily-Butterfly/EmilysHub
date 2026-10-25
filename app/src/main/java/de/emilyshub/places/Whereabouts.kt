package de.emilyshub.places

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/** Where the device is, as far as the app can tell by the WLAN it is connected to and its position (SPEC.md §4). */
sealed interface Whereabouts {
    /** At these places, or maybe at them, as Android keeps back what would tell; [surely] at those it does tell so. */
    data class At(val placeIds: Set<Long>, val surely: Set<Long> = placeIds) : Whereabouts

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

    /** Whether the device is at one of [placeIds] for sure, as the WLAN or the position tells, not just maybe. */
    fun surelyAt(placeIds: Set<Long>): Boolean = this is At && placeIds.any { it in surely }

    companion object {
        /**
         * At the [places] that the WLAN connected to or the [position] tells the device is at, surely so, and maybe at
         * those that neither tells it is not at, as Android keeps back the WLAN's name or the position.
         */
        fun of(wifi: Wifi?, position: Position?, places: List<PlaceWithWifis>): Whereabouts {
            val told = places.associate { it.id to it.told(wifi, position) }
            return At(
                placeIds = told.filterValues { true in it || false !in it }.keys,
                surely = told.filterValues { true in it }.keys,
            )
        }

        /** What each way the place is recognised by tells: here, not here, or nothing (null) as Android keeps it back. */
        private fun PlaceWithWifis.told(wifi: Wifi?, position: Position?): List<Boolean?> = buildList {
            if (wifis.isNotEmpty()) add(has(wifi))
            area?.let { add(position?.let(it::includes)) }
        }
    }
}

/**
 * Tells where the device is now, by the WLAN it is connected to, its position, and the [places] that have them. Only
 * what the places in question are recognised by is asked for: the position, which takes Android a while and the
 * battery a bit, only for those with a GPS area, at the same time as the WLAN.
 */
class Locator(private val wifi: CurrentWifi, private val location: CurrentLocation, private val places: Places) {
    /** Where the device is as far as the places with [placeIds] go. */
    suspend fun whereabouts(placeIds: Set<Long>): Whereabouts = coroutineScope {
        val inQuestion = places.all().first().filter { it.id in placeIds }
        val position = async { if (inQuestion.any { it.area != null }) location.position() else null }
        val connected = when {
            inQuestion.none { it.wifis.isNotEmpty() } -> null
            // Android reports a WLAN at once; should it not, the place is unknown rather than a reminder held back.
            else -> withTimeoutOrNull(WAIT.toMillis()) { wifi.current().first() }
        }
        Whereabouts.of(connected, position.await(), inQuestion)
    }

    private companion object {
        val WAIT: Duration = Duration.ofSeconds(5)
    }
}
