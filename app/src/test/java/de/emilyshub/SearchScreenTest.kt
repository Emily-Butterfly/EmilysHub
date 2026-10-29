package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.notes.NoteDraft
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The search over all areas (SPEC.md §8): reached from every tab, its results by area, each opening its entry. */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `the search is there on every tab`() {
        launchApp().use {
            for (area in listOf("Heute", "Routinen", "To-dos", "Wunschliste", "Notizen")) {
                compose.onNode(hasText(area) and hasClickAction()).performClick()
                compose.onNodeWithContentDescription("Suchen").performClick()
                compose.onNode(isHeading() and hasText("Suche")).assertIsDisplayed()
                compose.onNodeWithContentDescription("Zurück").performClick()
            }
        }
    }

    @Test
    fun `what is found shows by area, and tapping it opens it, back leading to the search as typed`() {
        app.routine("Vokabeln Arabisch", "20:00")
        app.todo("Wörterbuch Arabisch")
        app.wish("Arabisch A2")
        runBlocking { app.notes.create(NoteDraft("Verben", "Arabisch, Präsens")) }

        launchApp().use {
            compose.onNodeWithContentDescription("Suchen").performClick()
            compose.onNode(hasSetTextAction() and hasText("Suchbegriff")).performTextInput("arabisch")
            waitFor("Vokabeln Arabisch")

            for (area in listOf("Routinen", "To-dos", "Wünsche", "Notizen")) {
                compose.onNode(hasScrollAction()).performScrollToNode(isHeading() and hasText(area))
            }
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Wörterbuch Arabisch"))
            compose.onNodeWithText("Wörterbuch Arabisch").performClick()
            compose.onNodeWithText("To-do bearbeiten").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Verben"))
            compose.onNodeWithText("Verben").performClick()
            compose.onNodeWithText("Notiz bearbeiten").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(hasSetTextAction() and hasText("arabisch")).assertIsDisplayed()
        }
    }

    @Test
    fun `nothing found says so`() {
        launchApp().use {
            compose.onNodeWithContentDescription("Suchen").performClick()
            compose.onNode(hasSetTextAction() and hasText("Suchbegriff")).performTextInput("Nordlicht")
            waitFor("Nichts gefunden.")
        }
    }

    /** Waits for [text] to show: the search looks again a moment after typing stops. */
    private fun waitFor(text: String) {
        compose.waitUntil(timeoutMillis = 2_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }
}
