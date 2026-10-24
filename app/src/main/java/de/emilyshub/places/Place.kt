package de.emilyshub.places

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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

/** A WLAN, by its name (SSID), at which its place is recognised. A WLAN belongs to one place only. */
@Entity(
    tableName = "place_wifis",
    primaryKeys = ["place_id", "ssid"],
    foreignKeys = [
        ForeignKey(entity = Place::class, parentColumns = ["id"], childColumns = ["place_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["ssid"], unique = true)],
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
    /** The names of its WLANs, in alphabetical order. */
    val ssids: List<String> get() = wifis.map { it.ssid }.sortedWith(InputText.byName)
}

/** The place with the WLAN named [ssid]; none without a WLAN, or for one that no place has. Names match exactly, as for Android. */
fun List<PlaceWithWifis>.placeOf(ssid: String?): PlaceWithWifis? = ssid?.let { find { place -> it in place.ssids } }

enum class PlaceProblem {
    NameMissing,
    NameTooLong,
    NameTaken,
    WifisMissing,
    TooManyWifis,
    WifiTooLong,
    WifiTaken,
}

/**
 * A place as typed into the editor, before it is checked; saving refuses one with problems (SPEC.md §9).
 * Serializable, so that the editor keeps it across rotation and the app lock.
 */
data class PlaceDraft(val name: String, val wifis: List<String>) : Serializable {

    /** Its problems next to the [others], the places it is not: a name and WLANs of its own. */
    fun problems(others: List<PlaceWithWifis>): Set<PlaceProblem> = with(cleaned()) {
        buildSet {
            if (!InputText.isVisible(name)) add(PlaceProblem.NameMissing)
            if (InputText.lengthOf(name) > NAME_MAX) add(PlaceProblem.NameTooLong)
            if (others.any { InputText.readTheSame(it.place.name, name) }) add(PlaceProblem.NameTaken)
            if (wifis.isEmpty()) add(PlaceProblem.WifisMissing)
            if (wifis.size > WIFIS_MAX) add(PlaceProblem.TooManyWifis)
            if (wifis.any(::isTooLong)) add(PlaceProblem.WifiTooLong)
            if (wifis.any { ownerOf(it, others) != null }) add(PlaceProblem.WifiTaken)
        }
    }

    /**
     * The name on a single line; the WLANs without surrounding spaces, each once, empty ones left out. A WLAN name
     * is kept as it is otherwise: Android tells upper and lower case apart.
     */
    fun cleaned() = copy(name = InputText.singleLine(name), wifis = wifis.map(String::trim).filter(String::isNotEmpty).distinct())

    /** Which of the [others] has the WLAN named [ssid] already. */
    fun ownerOf(ssid: String, others: List<PlaceWithWifis>): PlaceWithWifis? = others.find { ssid.trim() in it.ssids }

    companion object {
        const val NAME_MAX = 40

        /** Home, a repeater and a guest network, with room to spare. */
        const val WIFIS_MAX = 10

        /** How long a WLAN name can be, in bytes of UTF-8: an "ä" takes two. */
        const val SSID_MAX_BYTES = 32

        fun isTooLong(ssid: String) = ssid.trim().toByteArray(Charsets.UTF_8).size > SSID_MAX_BYTES
    }
}

/** The place as the editor starts out with it. */
fun PlaceWithWifis.toDraft() = PlaceDraft(place.name, ssids)
