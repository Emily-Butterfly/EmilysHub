package de.emilyshub.places

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.emilyshub.data.InputText
import java.io.Serializable

/** A named place, such as "Zuhause" or "Arbeit" (SPEC.md §4), recognised by its WLANs. */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
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

    /** The names of its WLANs, in alphabetical order. */
    val ssids: List<String> get() = wifis.map { it.ssid }.sortedWith(InputText.byName)
}

/** The places the device is at with [wifi]: those with a WLAN of its name, which matches exactly, as for Android. */
fun List<PlaceWithWifis>.placesOf(wifi: Wifi): List<PlaceWithWifis> = when (wifi) {
    is Wifi.Named -> filter { wifi.ssid in it.ssids }
    Wifi.NameHidden, Wifi.None -> emptyList()
}

enum class PlaceProblem {
    NameMissing,
    NameTooLong,
    NameTaken,
    WifisMissing,
    TooManyWifis,
    WifiInvalid,
}

/**
 * A place as typed into the editor, before it is checked; saving refuses one with problems (SPEC.md §9).
 * Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class PlaceDraft(val name: String, val wifis: List<String>) : Serializable {

    /** Its problems next to the [others], the places it is not: a name of its own, and WLANs as [Ssid] has them. */
    fun problems(others: List<PlaceWithWifis>): Set<PlaceProblem> = with(cleaned()) {
        buildSet {
            if (!InputText.isVisible(name)) add(PlaceProblem.NameMissing)
            if (InputText.lengthOf(name) > NAME_MAX) add(PlaceProblem.NameTooLong)
            if (others.any { InputText.readTheSame(it.name, name) }) add(PlaceProblem.NameTaken)
            if (wifis.isEmpty()) add(PlaceProblem.WifisMissing)
            if (wifis.size > WIFIS_MAX) add(PlaceProblem.TooManyWifis)
            if (!wifis.all(Ssid::isValid)) add(PlaceProblem.WifiInvalid)
        }
    }

    /**
     * The name on a single line; the WLANs each once, those of only spaces left out. A WLAN name is kept exactly as
     * it is otherwise, as Android reports it: spaces and upper and lower case count.
     */
    fun cleaned() = copy(name = InputText.singleLine(name), wifis = wifis.filter(String::isNotBlank).distinct())

    companion object {
        const val NAME_MAX = 40

        /** Home, a repeater and a guest network, with room to spare. */
        const val WIFIS_MAX = 10
    }
}

/** The place as the editor starts out with it. */
fun PlaceWithWifis.toDraft() = PlaceDraft(place.name, ssids)
