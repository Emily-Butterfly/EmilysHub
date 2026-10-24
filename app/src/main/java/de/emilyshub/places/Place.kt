package de.emilyshub.places

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.emilyshub.data.InputText
import java.io.Serializable

/** A named place, such as "Zuhause" or "Arbeit" (SPEC.md §4), recognised by its WLANs, its GPS [area], or both. */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @Embedded(prefix = "area_") val area: Area? = null,
)

/**
 * A WLAN, by its name (SSID), at which its place is recognised. The same WLAN can be at several places, such as
 * "eduroam" at a university and at work: with it, the device is at all of them.
 */
@Entity(
    tableName = "place_wifis",
    primaryKeys = ["place_id", "ssid"],
    foreignKeys = [
        ForeignKey(entity = Place::class, parentColumns = ["id"], childColumns = ["place_id"], onDelete = ForeignKey.CASCADE),
    ],
)
data class PlaceWifi(
    @ColumnInfo(name = "place_id") val placeId: Long,
    val ssid: String,
)

/** A place with its WLANs. */
data class PlaceWithWifis(
    @Embedded val place: Place,
    @Relation(parentColumn = "id", entityColumn = "place_id") val wifis: List<PlaceWifi>,
) {
    val id: Long get() = place.id

    val name: String get() = place.name

    val area: Area? get() = place.area

    /** The names of its WLANs, in alphabetical order. */
    val ssids: List<String> get() = wifis.map { it.ssid }.sortedWith(InputText.byName)
}

/** The places the device is at with [wifi]: those with a WLAN of its name, which matches exactly, as for Android. */
fun List<PlaceWithWifis>.placesOf(wifi: Wifi): List<PlaceWithWifis> = filter { it.has(wifi) == true }

/** Whether [wifi] is one of the place's WLANs; nothing told (null) while Android keeps back its name, or without one. */
fun PlaceWithWifis.has(wifi: Wifi?): Boolean? = when (wifi) {
    null, Wifi.NameHidden -> null
    Wifi.None -> false
    is Wifi.Named -> wifi.ssid in ssids
}

enum class PlaceProblem {
    NameMissing,
    NameTooLong,
    NameTaken,
    WifiOrAreaMissing,
    TooManyWifis,
    WifiInvalid,
    LatitudeInvalid,
    LongitudeInvalid,
    RadiusOutOfRange,
}

/**
 * A place as typed into the editor, before it is checked; saving refuses one with problems (SPEC.md §9). Its GPS area
 * is the circle of [radius] meters around [latitude] and [longitude], typed in degrees ([Degrees]); without one, both
 * are empty. Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class PlaceDraft(
    val name: String,
    val wifis: List<String>,
    val latitude: String = "",
    val longitude: String = "",
    val radius: Int = Area.RADIUS_DEFAULT,
) : Serializable {

    /** Whether it has a GPS area, or has one begun. */
    val hasArea: Boolean get() = latitude.isNotBlank() || longitude.isNotBlank()

    /**
     * Its problems next to the [others], the places it is not: a name of its own; WLANs as [Ssid] has them, a GPS area,
     * or both; and for the area, a center on the earth and a radius of [Area.RADIUS_MIN] to [Area.RADIUS_MAX] meters.
     */
    fun problems(others: List<PlaceWithWifis>): Set<PlaceProblem> = with(cleaned()) {
        buildSet {
            if (!InputText.isVisible(name)) add(PlaceProblem.NameMissing)
            if (InputText.lengthOf(name) > NAME_MAX) add(PlaceProblem.NameTooLong)
            if (others.any { InputText.readTheSame(it.name, name) }) add(PlaceProblem.NameTaken)
            if (wifis.isEmpty() && !hasArea) add(PlaceProblem.WifiOrAreaMissing)
            if (wifis.size > WIFIS_MAX) add(PlaceProblem.TooManyWifis)
            if (!wifis.all(Ssid::isValid)) add(PlaceProblem.WifiInvalid)
            if (hasArea) {
                if (!within(latitude, Coordinates.LATITUDE_MAX)) add(PlaceProblem.LatitudeInvalid)
                if (!within(longitude, Coordinates.LONGITUDE_MAX)) add(PlaceProblem.LongitudeInvalid)
                if (radius !in Area.RADIUS_MIN..Area.RADIUS_MAX) add(PlaceProblem.RadiusOutOfRange)
            }
        }
    }

    /** Its GPS area; none without one, or with one that has problems. */
    fun area(): Area? {
        val latitude = Degrees.parse(latitude) ?: return null
        val longitude = Degrees.parse(longitude) ?: return null
        return Area(Coordinates(latitude, longitude), radius)
    }

    /** With its GPS area around [center], in degrees as they are shown. */
    fun withCenter(center: Coordinates) =
        copy(latitude = Degrees.format(center.latitude), longitude = Degrees.format(center.longitude))

    /** Without a GPS area; the radius as it starts out for a new one. */
    fun withoutArea() = copy(latitude = "", longitude = "", radius = Area.RADIUS_DEFAULT)

    /**
     * The name on a single line; the WLANs each once, those of only spaces left out; the degrees without spaces around
     * them. A WLAN name is kept exactly as it is otherwise, as Android reports it: spaces and upper and lower case count.
     */
    fun cleaned() = copy(
        name = InputText.singleLine(name),
        wifis = wifis.filter(String::isNotBlank).distinct(),
        latitude = latitude.trim(),
        longitude = longitude.trim(),
    )

    private fun within(degrees: String, max: Double) = Degrees.parse(degrees)?.let { it in -max..max } == true

    companion object {
        const val NAME_MAX = 40

        /** Home, a repeater and a guest network, with room to spare. */
        const val WIFIS_MAX = 10
    }
}

/** The place as the editor starts out with it. */
fun PlaceWithWifis.toDraft(): PlaceDraft {
    val draft = PlaceDraft(place.name, ssids)
    return area?.let { draft.withCenter(it.center).copy(radius = it.radius) } ?: draft
}
