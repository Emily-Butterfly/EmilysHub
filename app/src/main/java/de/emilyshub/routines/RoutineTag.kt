package de.emilyshub.routines

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import de.emilyshub.tags.Tag

/** A tag given to a routine: the routines' link to the shared tags. */
@Entity(
    tableName = "routine_tags",
    primaryKeys = ["routine_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class RoutineTag(
    @ColumnInfo(name = "routine_id") val routineId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)
