package de.emilyshub.warum

import androidx.compose.ui.graphics.toArgb
import de.emilyshub.ui.theme.FliederColorScheme
import de.emilyshub.ui.theme.LilaColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a Grund looks (SPEC.md §18, #163): its font and size, its colours from the palette or "Standard", and whether its
 * text is hard to read on its background, by the contrast WCAG asks of large text (3:1).
 */
class ReasonLookTest {

    private val light = StandardColors(
        text = FliederColorScheme.onSurface.toArgb(),
        background = FliederColorScheme.surfaceContainerHigh.toArgb(),
    )
    private val dark = StandardColors(
        text = LilaColorScheme.onSurface.toArgb(),
        background = LilaColorScheme.surfaceContainerHigh.toArgb(),
    )

    @Test
    fun `the palette has seven hues in four steps and seven neutrals, and each colour stays the same in light and dark`() {
        val hues = ReasonColor.entries.filter { it.hue != null }
        assertEquals(28, hues.size)
        assertEquals(7, hues.map { it.hue }.distinct().size)
        assertTrue(hues.groupBy { it.hue }.values.all { steps -> steps.map { it.step } == listOf(1, 2, 3, 4) })
        assertEquals(7, ReasonColor.entries.count { it.hue == null && it != ReasonColor.Standard })
        assertEquals(36, ReasonColor.entries.size)

        val blue = ReasonLook(background = ReasonColor.Blue4, text = ReasonColor.White)
        assertEquals(blue.backgroundIn(light), blue.backgroundIn(dark))
        assertEquals(blue.textIn(light), blue.textIn(dark))
    }

    @Test
    fun `contrast at the line, 3 to 1, reads, and just under it is hard to read`() {
        val white = 0xFFFFFFFF.toInt()
        assertFalse(Contrast.hardToRead(text = 0xFF949494.toInt(), background = white))
        assertTrue(Contrast.hardToRead(text = 0xFF959595.toInt(), background = white))
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), white), 0.01)
        assertEquals(Contrast.ratio(white, 0xFF767676.toInt()), Contrast.ratio(0xFF767676.toInt(), white), 0.0001)
    }

    @Test
    fun `Standard follows the design of the app, and reads well in light and in dark`() {
        val standard = ReasonLook()
        assertEquals(light.background, standard.backgroundIn(light))
        assertEquals(dark.background, standard.backgroundIn(dark))
        assertEquals(light.text, standard.textIn(light))
        assertEquals(dark.text, standard.textIn(dark))
        assertFalse(standard.hardToReadIn(light))
        assertFalse(standard.hardToReadIn(dark))

        // Black on the app's own background reads in light but not in dark.
        val black = ReasonLook(text = ReasonColor.Black)
        assertFalse(black.hardToReadIn(light))
        assertTrue(black.hardToReadIn(dark))
    }

    @Test
    fun `yellow on white is hard to read, and the editor allows it all the same`() {
        val look = ReasonLook(background = ReasonColor.White, text = ReasonColor.Yellow3)
        assertTrue(look.hardToReadIn(light))
        assertEquals(look, ReasonLook.of(look.font.stored, look.size.stored, look.background.stored, look.text.stored))
    }

    @Test
    fun `an unknown font, size or colour kept counts as Standard`() {
        assertEquals(ReasonLook(), ReasonLook.of("comic", "riesig", "pink", null))
        assertEquals(ReasonLook(font = ReasonFont.Hand, size = ReasonSize.Large), ReasonLook.of("hand", "large", "?", "?"))
    }

    @Test
    fun `there are eight fonts in three sizes, the standard one in the middle`() {
        assertEquals(8, ReasonFont.entries.size)
        assertEquals(listOf(ReasonSize.Small, ReasonSize.Medium, ReasonSize.Large), ReasonSize.entries)
        assertEquals(ReasonSize.Medium, ReasonLook().size)
        assertEquals(ReasonFont.Standard, ReasonLook().font)
        assertEquals(ReasonFont.entries.size, ReasonFont.entries.map { it.stored }.distinct().size)
        assertEquals(ReasonColor.entries.size, ReasonColor.entries.map { it.stored }.distinct().size)
    }
}
