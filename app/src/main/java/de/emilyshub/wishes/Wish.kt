package de.emilyshub.wishes

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import de.emilyshub.tags.Tag
import java.time.LocalDate

/** Where a wish stands (SPEC.md §6): open, in progress, or done, as [stored]. */
enum class WishStatus(val stored: String) { Open("open"), InProgress("in_progress"), Done("done") }

/**
 * A wish (SPEC.md §6): something to do some day, as its [period] says in the user's words ("2027", "Sommer"), and never
 * reminded of. Done, it came true [doneOn] that day, and shows in the review. A wish is a goal, which can hang under
 * another one, its [parentId], to any depth, and have to-dos hang under it; how far it has come is worked out from those,
 * unless set by hand as [manualProgress]. Its tags are kept apart, as [WishTag]s.
 */
@Entity(
    tableName = "wishes",
    foreignKeys = [
        ForeignKey(entity = Wish::class, parentColumns = ["id"], childColumns = ["parent_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["parent_id"])],
)
data class Wish(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val period: String,
    val status: WishStatus = WishStatus.Open,
    /** Only once [status] is done. */
    @ColumnInfo(name = "done_on") val doneOn: LocalDate? = null,
    /** The goal it is part of; none at the top. Deleting that goal deletes this one with it. */
    @ColumnInfo(name = "parent_id") val parentId: Long? = null,
    /** How far it has come in percent, if set by hand; none: worked out from its parts. */
    @ColumnInfo(name = "manual_progress") val manualProgress: Int? = null,
)

/** A tag given to a wish: the wishes' link to the shared tags (SPEC.md §8). */
@Entity(
    tableName = "wish_tags",
    primaryKeys = ["wish_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = Wish::class, parentColumns = ["id"], childColumns = ["wish_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class WishTag(
    @ColumnInfo(name = "wish_id") val wishId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

/** A wish with its tags. */
data class WishWithTags(
    @Embedded val wish: Wish,
    @Relation(parentColumn = "id", entityColumn = "wish_id") val tags: List<WishTag>,
) {
    /** The ids of its tags, as the editor and the tag filter take them. */
    val tagIds: Set<Long> get() = tags.map { it.tagId }.toSet()
}
