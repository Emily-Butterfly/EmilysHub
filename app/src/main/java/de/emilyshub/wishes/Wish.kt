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
 * reminded of. Done, it came true [doneOn] that day and shows in the review. Its tags are kept apart, as [WishTag]s.
 */
@Entity(tableName = "wishes")
data class Wish(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val period: String,
    val status: WishStatus = WishStatus.Open,
    /** Only once [status] is done. */
    @ColumnInfo(name = "done_on") val doneOn: LocalDate? = null,
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
