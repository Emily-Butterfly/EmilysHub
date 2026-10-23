package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.RoutineDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RoutineEditingTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a routine created in the Routinen tab is listed there and due on Heute`() {
        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            heading("Neue Routine").assertIsDisplayed()

            field("Name").performTextInput("Zähne putzen")
            field("Beschreibung (optional)").performTextInput("Zwei Minuten")
            chooseTime(hour = "07", minute = "30")
            compose.onNodeWithText("Speichern").performClick()

            heading("Routinen").assertIsDisplayed()
            compose.onNodeWithText("Zähne putzen").assertIsDisplayed()
            compose.onNodeWithText("Täglich um 07:30").assertIsDisplayed()

            tab("Heute").performClick()
            compose.onNodeWithText("Zähne putzen").assertIsDisplayed()
            compose.onNodeWithText("07:30").assertIsDisplayed()
        }
        assertEquals(listOf("Zähne putzen" to "Zwei Minuten"), savedRoutines().map { it.name to it.description })
    }

    @Test
    fun `a routine is only saved with a name`() {
        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()

            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Bitte gib einen Namen ein.").assertIsDisplayed()
            heading("Neue Routine").assertIsDisplayed()
        }
        assertEquals(emptyList<String>(), savedRoutines())
    }

    @Test
    fun `tapping a routine opens it for editing`() {
        routine("Vitamine", "08:00")

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithText("Vitamine").performClick()
            heading("Routine bearbeiten").assertIsDisplayed()

            field("Name").performTextReplacement("Vitamin D")
            chooseTime(hour = "09", minute = "15")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Vitamin D").assertIsDisplayed()
            compose.onNodeWithText("Täglich um 09:15").assertIsDisplayed()
        }
    }

    @Test
    fun `deleting a routine asks first`() {
        routine("Vitamine", "08:00")

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithText("Vitamine").performClick()

            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNodeWithText("Routine löschen?").assertIsDisplayed()
            compose.onNodeWithText("Abbrechen").performClick()
            heading("Routine bearbeiten").assertIsDisplayed()
            assertEquals(1, savedRoutines().size)

            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

            heading("Routinen").assertIsDisplayed()
            compose.onNodeWithText("Noch keine Routinen", substring = true).assertIsDisplayed()
        }
        assertEquals(emptyList<String>(), savedRoutines())
    }

    @Test
    fun `back leaves the editor without saving`() {
        launchApp().use { scenario ->
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            field("Name").performTextInput("Halb fertig")

            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            heading("Routinen").assertIsDisplayed()
        }
        assertEquals(emptyList<String>(), savedRoutines())
    }

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun heading(title: String) = compose.onNode(isHeading() and hasText(title))

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    /** Opens the time dialog, types the time and confirms it. */
    private fun chooseTime(hour: String, minute: String) {
        compose.onNode(hasText("Uhrzeit") and hasClickAction()).performClick()
        val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        fields[0].performTextReplacement(hour)
        fields[1].performTextReplacement(minute)
        compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
    }

    private fun routine(name: String, time: String) = runBlocking {
        app.routines.create(RoutineDraft(name = name, description = "", time = LocalTime.parse(time)))
    }

    private fun savedRoutines() = runBlocking { app.routines.all().first() }
}
