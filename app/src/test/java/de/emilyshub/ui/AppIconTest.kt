package de.emilyshub.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/**
 * The app icon (SPEC.md §1): the house with an "E" cut out of it, drawn as a path of its own, the same in the launcher
 * icon, its one-colour variant and the status bar, and within the safe zone of the adaptive icon.
 */
class AppIconTest {

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
        val (house, letter) = launcher.split(" M").also { assertEquals(2, it.size) }
        assertEquals("M54,29L80,53H73V79H35V53H28Z", house)
        // An E: a spine and three bars, all straight, as one outline of twelve corners.
        val corners = points("M$letter")
        assertEquals(12, corners.size)
        (corners + corners.first()).zipWithNext().forEach { (a, b) -> assertTrue("straight from $a to $b", a.first == b.first || a.second == b.second) }
        val (left, right) = corners.minOf { it.first } to corners.maxOf { it.first }
        // The spine on the left; the top and bottom bars reach to the right, the middle one not quite.
        assertEquals(2, corners.count { it.first == left })
        assertEquals(4, corners.count { it.first == right })
        // Inside the walls and under the roof.
        corners.forEach { (x, y) -> assertTrue("($x, $y) in the house", x > 35 && x < 73 && y > 29 + 26.0 / 24 * kotlin.math.abs(x - 54) && y < 79) }
    }

    @Test
    fun `everything lies within the safe zone, a circle of 33 around the middle of the 108 canvas`() {
        points(launcher).forEach { (x, y) -> assertTrue("($x, $y)", hypot(x - 54, y - 54) <= 33) }
    }

    private fun pathData(drawable: String): String =
        Regex("""android:pathData="([^"]+)"""").find(File("src/main/res/drawable/$drawable").readText())!!.groupValues[1]

    /** The corners of a path made of M, L, H, V and Z only. */
    private fun points(path: String): List<Pair<Double, Double>> {
        val result = mutableListOf<Pair<Double, Double>>()
        var x = 0.0
        var y = 0.0
        Regex("""([MLHVZ])([-\d.,]*)""").findAll(path.replace(" ", "")).forEach { match ->
            val numbers = match.groupValues[2].split(",").filter { it.isNotEmpty() }.map { it.toDouble() }
            when (match.groupValues[1]) {
                "M", "L" -> { x = numbers[0]; y = numbers[1] }
                "H" -> x = numbers[0]
                "V" -> y = numbers[0]
                "Z" -> return@forEach
            }
            result += x to y
        }
        return result
    }
}
