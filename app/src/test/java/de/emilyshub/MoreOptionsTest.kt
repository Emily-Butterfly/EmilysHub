package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.Priority
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The editors of routines and to-dos (SPEC.md §8, #92) show what each entry needs first; all else lies folded under
 * "Mehr Optionen", whose row names what of it is set.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h915dp") // the Galaxy A52
class MoreOptionsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a new routine shows name, times, repetition and checklist, and all else folded`() {
        launchApp().use {
            compose.onNode(hasText("Routinen") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()

            assertInOrder(field("Name"), text("Uhrzeiten"), text("Wiederholung"), text("Checkliste"), moreOptions())
            field("Name").assertIsDisplayed()
            compose.onNodeWithText("Uhrzeiten").assertIsDisplayed()
            moreOptions().assert(hasText("Beschreibung, Priorität, Tags", substring = true))
            field("Beschreibung (optional)").assertDoesNotExist()
            compose.onNodeWithText("Zeitfenster").assertDoesNotExist()

            compose.openMoreOptions()
            assertInOrder(moreOptions(), field("Beschreibung (optional)"), text("Priorität"), text("Zeitfenster"))
        }
    }

    @Test
    fun `a new to-do shows title, due and description, and all else folded`() {
        launchApp().use {
            compose.onNode(hasText("To-dos") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()

            assertInOrder(field("Titel"), text("Fälligkeit"), field("Beschreibung (optional)"), moreOptions())
            field("Titel").assertIsDisplayed()
            compose.onNodeWithText("Fälligkeit").assertIsDisplayed()
            moreOptions().assert(hasText("In Heute zeigen, Wiederholung", substring = true))
            compose.onNodeWithText("Priorität").assertDoesNotExist()

            compose.openMoreOptions()
            assertInOrder(moreOptions(), text("Weckzeit"), text("Priorität"), text("Checkliste"))
        }
    }

    @Test
    fun `folded, the row names what is set, also for one opened to edit`() {
        val gym = app.place("Gym", "Gym-WLAN")
        val sport = app.tag("Sport")
        val health = app.tag("Gesundheit")
        app.routine("Training", "18:00", priority = Priority.High, tags = setOf(sport, health), places = setOf(gym), fullScreen = true)
        app.todo("Steuer", priority = Priority.Low, checklist = listOf("Belege"))

        launchApp().use {
            compose.onNode(hasText("Routinen") and hasClickAction()).performClick()
            compose.onNodeWithText("Training").performClick()
            moreOptions().assert(hasText("Hohe Priorität · 2 Tags · Ort: Gym · Vollbild-Alarm"))
            compose.onNodeWithText("Zeitfenster").assertDoesNotExist()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNode(hasText("To-dos") and hasClickAction()).performClick()
            compose.onNodeWithText("Steuer").performClick()
            moreOptions().assert(hasText("Niedrige Priorität · 1 Unterpunkt"))
        }
    }

    @Test
    fun `a problem in a field folded away unfolds it on saving`() {
        launchApp().use {
            compose.onNode(hasText("Routinen") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            field("Name").performTextInput("Lesen")
            compose.openMoreOptions()
            field("Beschreibung (optional)").performTextInput("a".repeat(1001))
            compose.openMoreOptions()
            field("Beschreibung (optional)").assertDoesNotExist()

            compose.onNode(hasText("Speichern") and hasClickAction()).performClick()

            compose.onNodeWithText("Höchstens 1000 Zeichen").performScrollTo().assertIsDisplayed()
            field("Beschreibung (optional)").assertExists()
        }
        assertEquals(emptyList<String>(), runBlocking { app.routines.inUse().first() }.map { it.name })
    }

    @Test
    fun `unfolded stays unfolded through rotation`() {
        launchApp().use { scenario ->
            compose.onNode(hasText("To-dos") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            compose.openMoreOptions()

            scenario.recreate()

            compose.onNodeWithText("Priorität").assertExists()
        }
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun text(text: String) = compose.onNodeWithText(text)

    private fun moreOptions() = compose.onNode(hasText("Mehr Optionen") and hasClickAction())

    /** One below the other, as listed. */
    private fun assertInOrder(vararg nodes: SemanticsNodeInteraction) {
        val tops: List<Dp> = nodes.map { it.getUnclippedBoundsInRoot().top }
        assertTrue("In order from the top: $tops", tops.zipWithNext().all { (above, below) -> above < below })
    }
}
