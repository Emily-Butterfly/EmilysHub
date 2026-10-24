package de.emilyshub.routines

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A sub-item ("Unterpunkt") of a routine's checklist (SPEC.md §2), at its [position] among the others. */
@Entity(
    tableName = "checklist_items",
    foreignKeys = [
        ForeignKey(entity = Routine::class, parentColumns = ["id"], childColumns = ["routine_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["routine_id"])],
)
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    val position: Int,
    val text: String,
)

/** An item ticked off on one occurrence; each occurrence of the routine is ticked off on its own. */
@Entity(
    tableName = "checklist_ticks",
    primaryKeys = ["occurrence_id", "item_id"],
    foreignKeys = [
        ForeignKey(
            entity = Occurrence::class, parentColumns = ["id"], childColumns = ["occurrence_id"], onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = ChecklistItem::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["item_id"])],
)
data class ChecklistTick(
    @ColumnInfo(name = "occurrence_id") val occurrenceId: Long,
    @ColumnInfo(name = "item_id") val itemId: Long,
)

/** An item of the checklist on Heute: [ticked] off on this occurrence, or with all of it ticked off at once. */
data class ChecklistEntry(val itemId: Long, val text: String, val ticked: Boolean)

/** How long a checklist of a routine or a to-do can be (SPEC.md §2, §5): how many items, of how many characters. */
object Checklists {
    const val ITEMS_MAX = 20
    const val ITEM_LENGTH_MAX = 80
}
