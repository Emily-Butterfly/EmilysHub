package de.emilyshub

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.OccurrenceStatus.Done
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The small "€" atop Heute (SPEC.md §1): it opens this month of the household book (SPEC.md §13). */
@RunWith(AndroidJUnit4::class)
class TodayLedgerButtonTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `on Heute the euro sign opens this month of the household book, and back goes to Heute`() {
        launchApp().use {
            val button = compose.onNodeWithContentDescription("Haushaltsbuch öffnen")
            button.assert(hasText("€"))
            button.performClick()

            compose.onNodeWithText("Oktober 2026").assertIsDisplayed()
            compose.onNodeWithText("In diesem Monat gibt es noch keine Buchungen.").assertIsDisplayed()

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(isHeading() and hasText("Heute")).assertIsDisplayed()
        }
    }

    @Test
    fun `only Heute has the euro sign, the notes keep their own button`() {
        launchApp().use {
            for (area in listOf("Routinen", "To-dos", "Sonstiges")) {
                tab(area).performClick()
                compose.onNode(hasContentDescription("Haushaltsbuch öffnen")).assertDoesNotExist()
            }
            tab("Notizen").performClick()
            compose.onAllNodesWithContentDescription("Haushaltsbuch öffnen").fetchSemanticsNodes().let { assertEquals(1, it.size) }
            compose.onNodeWithContentDescription("Haushaltsbuch öffnen").assert(hasText("Haushaltsbuch"))
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `it sits left of the flame, as large as the magnifier`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-22" to Done)

        launchApp().use {
            compose.waitForIdle()
            val button = bounds(hasContentDescription("Haushaltsbuch öffnen"))
            val flame = bounds(hasContentDescription("Streak", substring = true))
            val magnifier = bounds(hasContentDescription("Suchen") and hasClickAction())

            assertTrue("${button.right} <= ${flame.left}", button.right <= flame.left)
            assertEquals(magnifier.right - magnifier.left, button.right - button.left)
            assertEquals(magnifier.bottom - magnifier.top, button.bottom - button.top)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `without a flame it sits left of the magnifier`() {
        launchApp().use {
            compose.waitForIdle()
            compose.onNode(hasContentDescription("Streak", substring = true)).assertDoesNotExist()
            val button = bounds(hasContentDescription("Haushaltsbuch öffnen"))
            val magnifier = bounds(hasContentDescription("Suchen") and hasClickAction())
            val cog = bounds(hasContentDescription("Einstellungen") and hasClickAction())

            // Next to it, as the magnifier is to the cog.
            assertEquals(cog.left - magnifier.right, magnifier.left - button.right)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp", fontScale = 2f)
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `with large type the title, the euro sign, the flame, magnifier and cog do not overlap`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-21" to Done, "2026-10-22" to Done)

        launchApp().use {
            compose.waitForIdle()
            val inOrder = listOf(
                bounds(isHeading() and hasText("Heute")),
                bounds(hasContentDescription("Haushaltsbuch öffnen")),
                bounds(hasContentDescription("Streak", substring = true)),
                bounds(hasContentDescription("Suchen") and hasClickAction()),
                bounds(hasContentDescription("Einstellungen") and hasClickAction()),
            )
            inOrder.zipWithNext().forEach { (left, right) -> assertTrue("$left before $right", left.right <= right.left) }
            assertTrue(inOrder.last().right.value <= 411f)
        }
    }

    private fun bounds(matcher: SemanticsMatcher): DpRect = compose.onNode(matcher).getBoundsInRoot()

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))
}
