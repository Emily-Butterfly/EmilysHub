package de.emilyshub.warum

import java.time.LocalDate

/** Where a Grund stands in an image of the board: in [column] 0 (left) or 1 (right), [top] pixels down. */
data class Spot(val column: Int, val top: Int)

/** The Gründe of an image laid out: where each stands, in their order, and how high the image is. */
data class BoardLayout(val spots: List<Spot>, val height: Int)

/**
 * The board as images (SPEC.md §18): the Gründe in two columns, each into the shorter one, the left first, as the board
 * on the screen lays them, [gap] apart and [padding] from the edges. An image is no higher than [limit]: one that would
 * be higher ends before the next Grund, which starts the next image, laid out anew. A single Grund higher than the limit
 * has an image of its own; none is ever cut.
 */
class BoardPages(private val gap: Int, private val padding: Int, private val limit: Int) {

    /** The Gründe as high as [heights] laid out in one image. */
    fun layout(heights: List<Int>): BoardLayout {
        val columns = intArrayOf(0, 0)
        val spots = heights.map { height ->
            val column = if (columns[1] < columns[0]) 1 else 0
            val top = columns[column] + if (columns[column] > 0) gap else 0
            columns[column] = top + height
            Spot(column, padding + top)
        }
        return BoardLayout(spots, padding + columns.max() + padding)
    }

    /** The Gründe as high as [heights] split into images, each by the range of the Gründe it shows. */
    fun split(heights: List<Int>): List<IntRange> = buildList {
        var start = 0
        for (end in heights.indices) {
            if (end > start && layout(heights.subList(start, end + 1)).height > limit) {
                add(start until end)
                start = end
            }
        }
        if (heights.isNotEmpty()) add(start..heights.lastIndex)
    }

    companion object {
        /** An image of the board as saved: "Mein Warum 2026-11-24.png", or of several, "Mein Warum 2026-11-24 (1 von 3).png". */
        fun fileName(day: LocalDate, page: Int, of: Int): String =
            if (of == 1) "Mein Warum $day.png" else "Mein Warum $day ($page von $of).png"
    }
}
