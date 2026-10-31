package de.emilyshub

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `bottom bar offers the five areas in order`() {
        val labels = compose.onAllNodes(isSelectable()).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }

        assertEquals(listOf("Heute", "Routinen", "To-dos", "Ziele", "Notizen"), labels)
    }

    @Test
    fun `app starts on Heute`() {
        tab("Heute").assertIsSelected()
        heading("Heute").assertIsDisplayed()
    }

    @Test
    fun `tapping a tab opens that area`() {
        for (area in listOf("Routinen", "To-dos", "Ziele", "Notizen", "Heute")) {
            tab(area).performClick().assertIsSelected()
            heading(area).assertIsDisplayed()
        }
    }

    @Test
    fun `back from another area returns to Heute`() {
        tab("Ziele").performClick()

        pressBack()

        tab("Heute").assertIsSelected()
        heading("Heute").assertIsDisplayed()
    }

    @Test
    fun `settings open from the top bar and back returns to the area`() {
        tab("Notizen").performClick()

        compose.onNodeWithContentDescription("Einstellungen").performClick()
        heading("Einstellungen").assertIsDisplayed()

        pressBack()
        heading("Notizen").assertIsDisplayed()
    }

    @Test
    fun `the back arrow in settings returns to the area`() {
        tab("Routinen").performClick()
        compose.onNodeWithContentDescription("Einstellungen").performClick()

        compose.onNodeWithContentDescription("Zurück").performClick()

        heading("Routinen").assertIsDisplayed()
    }

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    /** The screen's title, marked as a heading for accessibility. */
    private fun heading(title: String) = compose.onNode(isHeading() and hasText(title))

    private fun pressBack() {
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }
}
