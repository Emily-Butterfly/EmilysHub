package de.emilyshub.tags

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import de.emilyshub.data.InputText
import de.emilyshub.data.NameProblem

/**
 * A tag (SPEC.md §8), shared by all areas. Each area links its entries to the tags in a table of its own, like
 * routines in `routine_tags`, so that a tag deleted is taken off everywhere and an entry deleted takes its links along.
 */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
) {
    companion object {
        /** The longest name, which still fits on a chip. */
        const val NAME_MAX = 30
    }
}

/**
 * What keeps [name], on a single line, from naming a tag besides the [others]; none if it can. Two tags read
 * differently, so that "arbeit" does not come next to "Arbeit".
 */
fun tagNameProblem(name: String, others: Collection<Tag>): NameProblem? =
    InputText.nameProblem(name, Tag.NAME_MAX, others.map { it.name })
