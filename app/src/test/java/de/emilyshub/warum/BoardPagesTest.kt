package de.emilyshub.warum

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The board as images (SPEC.md §18, #164): laid out in two columns as on the screen, split into images no higher than
 * the limit, each with the next Gründe in their order laid out anew, never through a Grund; and their file names.
 */
class BoardPagesTest {

    private val pages = BoardPages(gap = 10, padding = 20, limit = 300)

    @Test
    fun `each reason goes into the shorter column, the left one first, and the image is as high as the higher column`() {
        val layout = pages.layout(listOf(100, 50, 30, 20))

        assertEquals(listOf(Spot(0, 20), Spot(1, 20), Spot(1, 80), Spot(1, 120)), layout.spots)
        // The right column: 50 + 10 + 30 + 10 + 20.
        assertEquals(20 + 120 + 20, layout.height)
    }

    @Test
    fun `a board that fits is one image`() {
        assertEquals(listOf(0..2), pages.split(listOf(100, 100, 100)))
    }

    @Test
    fun `a board exactly at the limit is still one image, and one beyond it two`() {
        // Both columns 100 + 10 + 150, with the padding above and below: 300.
        assertEquals(listOf(0..3), pages.split(listOf(100, 100, 150, 150)))
        assertEquals(300, pages.layout(listOf(100, 100, 150, 150)).height)
        assertEquals(listOf(0..2, 3..3), pages.split(listOf(100, 100, 150, 151)))
    }

    @Test
    fun `the next image takes up the next reasons in their order, laid out anew from the top`() {
        val split = pages.split(listOf(200, 200, 200, 200, 50))

        assertEquals(listOf(0..1, 2..4), split)
        assertEquals(listOf(Spot(0, 20), Spot(1, 20), Spot(0, 230)), pages.layout(listOf(200, 200, 50)).spots)
    }

    @Test
    fun `a single reason higher than the limit has an image of its own, never cut`() {
        assertEquals(listOf(0..0, 1..1, 2..2), pages.split(listOf(100, 500, 100)))
        assertEquals(listOf(0..0, 1..1, 2..2), pages.split(listOf(500, 500, 100)))
        assertEquals(listOf(0..1), pages.split(listOf(100, 50)))
        assertEquals(emptyList<IntRange>(), pages.split(emptyList()))
    }

    @Test
    fun `one image is named by the day, several are counted`() {
        val day = LocalDate.parse("2026-11-24")
        assertEquals("Mein Warum 2026-11-24.png", BoardPages.fileName(day, page = 1, of = 1))
        assertEquals("Mein Warum 2026-11-24 (1 von 3).png", BoardPages.fileName(day, page = 1, of = 3))
        assertEquals("Mein Warum 2026-11-24 (3 von 3).png", BoardPages.fileName(day, page = 3, of = 3))
    }
}
