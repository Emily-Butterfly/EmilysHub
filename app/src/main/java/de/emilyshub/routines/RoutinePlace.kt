package de.emilyshub.routines

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.emilyshub.places.Place

/** A place a routine is bound to (SPEC.md §4): it pops up there, at any of its places, and waits elsewhere. */
@Entity(
    tableName = "routine_places",
    primaryKeys = ["routine_id", "place_id"],
    foreignKeys = [
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Place::class, parentColumns = ["id"], childColumns = ["place_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["place_id"])],
)
data class RoutinePlace(
    @ColumnInfo(name = "routine_id") val routineId: Long,
    @ColumnInfo(name = "place_id") val placeId: Long,
)
