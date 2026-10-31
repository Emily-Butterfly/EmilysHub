package de.emilyshub.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The app icon (SPEC.md §1): the house with an "E" cut out of it, drawn as a path of its own, the same in the launcher
 * icon, its one-colour variant and the status bar, and within the safe zone of the adaptive icon.
 */
class AppIconTest {

    private data class Corner(val x: Double, val y: Double)

    private val launcher = pathData("ic_launcher_foreground.xml")
    private val statusBar = pathData("ic_notification.xml")

    @Test
    fun `the status bar shows the same house with its E as the launcher`() {
        assertEquals(launcher, statusBar)
    }

    @Test
    fun `the one-colour variant is the same drawing`() {
        val adaptive = File("src/main/res/mipmap-anydpi/ic_launcher.xml").readText()
        assertTrue(adaptive.contains("""<monochrome android:drawable="@drawable/ic_launcher_foreground" />"""))
    }

    @Test
    fun `the house has an E cut out of it, no longer a check mark`() {
        val outlines = outlines(launcher)
        assertEquals(2, outlines.size)
        val (house, letter) = outlines
        assertEquals(listOf(Corner(54.0, 29.0), Corner(80.0, 53.0), Corner(73.0, 53.0), Corner(73.0, 79.0)), house.take(4))
        // An E: a spine and three bars, all straight, as one outline of twelve corners.
        assertEquals(12, letter.size)
        (letter + letter.first()).zipWithNext().forEach { (a, b) ->
            assertTrue("straight from $a to $b", a.x == b.x || a.y == b.y)
        }
        val (left, right) = letter.minOf { it.x } to letter.maxOf { it.x }
        // The spine on the left; the top and bottom bars reach to the right, the middle one a little less far.
        assertEquals(2, letter.count { it.x == left })
        assertEquals(4, letter.count { it.x == right })
        val middle = letter.map { it.x }.filter { it != left && it != right }.max()
        assertTrue("the middle bar is shorter", middle < right && middle > left + (right - left) / 2)
        // Inside the walls and under the roof, which falls 24 for every 26 to either side.
        letter.forEach { (x, y) ->
            assertTrue("($x, $y) in the house", x > 35 && x < 73 && y > 29 + 24.0 / 26 * abs(x - 54) && y < 79)
        }
    }

    @Test
    fun `everything lies within the safe zone, a circle of 33 around the middle of the 108 canvas`() {
        outlines(launcher).flatten().forEach { (x, y) -> assertTrue("($x, $y)", hypot(x - 54, y - 54) <= 33) }
    }

    private fun pathData(drawable: String): String {
        val xml = File("src/main/res/drawable/$drawable").readText()
        return checkNotNull(Regex("""android:pathData="([^"]+)"""").find(xml)) { "No path in $drawable" }.groupValues[1]
    }

    /** The corners of each outline of a path drawn with absolute M, L, H, V and Z only; anything else fails. */
    private fun outlines(path: String): List<List<Corner>> {
        val result = mutableListOf<MutableList<Corner>>()
        var x = 0.0
        var y = 0.0
        Regex("""([A-Za-z])([-\d.,]*)""").findAll(path.filterNot(Char::isWhitespace)).forEach { match ->
            val numbers = match.groupValues[2].split(",").filter { it.isNotEmpty() }.map { it.toDouble() }
            when (val command = match.groupValues[1]) {
                "M" -> result += mutableListOf<Corner>()
                "L", "H", "V", "Z" -> Unit
                else -> error("Unexpected path command $command")
            }
            when (match.groupValues[1]) {
                "M", "L" -> { x = numbers[0]; y = numbers[1] }
                "H" -> x = numbers[0]
                "V" -> y = numbers[0]
                "Z" -> return@forEach
            }
            result.last() += Corner(x, y)
        }
        return result
    }
}
