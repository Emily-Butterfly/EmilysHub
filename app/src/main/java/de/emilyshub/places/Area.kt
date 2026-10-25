package de.emilyshub.places

import androidx.room.Embedded
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the earth, in degrees as Android's locations have it (WGS 84): north and east are positive. */
data class Coordinates(val latitude: Double, val longitude: Double) {

    /** How far it is to [other] along the earth's surface, in meters; the earth taken as a sphere, off by under 0.5 %. */
    fun distanceTo(other: Coordinates): Double {
        val latitudes = Math.toRadians(latitude) to Math.toRadians(other.latitude)
        val northward = latitudes.second - latitudes.first
        val eastward = Math.toRadians(other.longitude - longitude)
        val haversine = sin(northward / 2).squared() + cos(latitudes.first) * cos(latitudes.second) * sin(eastward / 2).squared()
        return 2 * EARTH_RADIUS * asin(sqrt(haversine.coerceAtMost(1.0)))
    }

    private fun Double.squared() = this * this

    companion object {
        const val LATITUDE_MAX = 90.0
        const val LONGITUDE_MAX = 180.0

        /** The earth's mean radius, in meters. */
        private const val EARTH_RADIUS = 6_371_008.8
    }
}

/** Where Android says the device is: at [coordinates], give or take [accuracy] meters. */
data class Position(val coordinates: Coordinates, val accuracy: Double)

/** A place's GPS area (SPEC.md §4): the circle of [radius] meters around [center]. */
data class Area(@Embedded val center: Coordinates, val radius: Int) {

    /**
     * Whether the device is within it at [position]: up to the radius from the center, the border included, and also
     * once Android's give or take reaches into it, so that a reminder is not held back on a position near the border.
     * A position less precise than the area is large tells nothing (null): within or not, it would be a guess.
     */
    fun includes(position: Position): Boolean? = when {
        position.accuracy > radius -> null
        else -> center.distanceTo(position.coordinates) <= radius + position.accuracy
    }

    /**
     * Whether the device is surely within it at [position], so that being there can count as arriving: the position
     * itself lies within the radius, and it is as precise as the area is large. Near the border, it is only maybe.
     */
    fun surelyIncludes(position: Position): Boolean =
        position.accuracy <= radius && center.distanceTo(position.coordinates) <= radius

    companion object {
        const val RADIUS_MIN = 50
        const val RADIUS_MAX = 500
        const val RADIUS_DEFAULT = 150
    }
}

/** Degrees as typed and shown: with a decimal point or comma, a minus for south and west. */
object Degrees {
    private val TYPED = Regex("""[+\-−]?\d{1,3}([.,]\d+)?""")

    /** The degrees typed; none for what is not a number of degrees, before checking their range. */
    fun parse(typed: String): Double? {
        val trimmed = typed.trim()
        return if (TYPED.matches(trimmed)) trimmed.replace('−', '-').replace(',', '.').toDouble() else null
    }

    /** The degrees with a comma, to [DECIMALS] places at most: about 10 cm. */
    fun format(degrees: Double): String =
        BigDecimal.valueOf(degrees).setScale(DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

    private const val DECIMALS = 6
}
