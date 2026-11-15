package de.emilyshub

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowToast
import java.time.Duration

/**
 * The Eingang (SPEC.md §16, #93): a thought captured in Heute with a line and "Fertig", and sorted in later from the
 * list as a to-do, a note or a routine, or deleted.
 */
@RunWith(AndroidJUnit4::class)
class InboxScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun allowReminders() {
        // Heute warns while the reminders lack something from Android, which is not what these tests are about.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.settingsDao.values["reminders-asked"] = "yes"
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp") // the Galaxy A52
    fun `a thought typed in Heute goes into the inbox with Fertig, and the line is empty for the next`() {
        launchApp().use {
            compose.onNode(hasText("Eingang", substring = true) and hasClickAction()).assertDoesNotExist()

            capture().performTextInput("Zahnarzt anrufen")
            capture().performImeAction()

            capture().assertTextEquals("Was geht dir durch den Kopf?", "")
            capture().assertIsFocused()
            assertEquals("Im Eingang", ShadowToast.getTextOfLatestToast())
            capture().performTextInput("   ")
            capture().performImeAction()
            capture().performTextReplacement("Geschenk für Mama")
            capture().performImeAction()

            compose.onNode(hasText("Eingang (2)") and hasClickAction()).assertIsDisplayed()
        }
        assertEquals(listOf("Geschenk für Mama", "Zahnarzt anrufen"), texts())
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp") // the Galaxy A52
    fun `close to the limit the line counts, and more than 1000 characters are not kept`() {
        launchApp().use {
            capture().performTextInput("a".repeat(950))
            compose.onNodeWithText("950/1000", substring = true).assertExists()

            capture().performTextInput("a".repeat(51))
            capture().performImeAction()
        }
        assertEquals(emptyList<String>(), texts())
    }

    @Test
    fun `the inbox lists the newest first, and its texts change, on several lines`() {
        app.inbox("Einkaufen")
        app.clock.now += Duration.ofMinutes(5)
        app.inbox("Steuer")

        launchApp().use {
            openInbox()
            compose.onNode(isHeading() and hasText("Eingang")).assertIsDisplayed()
            compose.onNodeWithText("23.10.2026, 07:05").assertIsDisplayed()

            compose.onNodeWithText("Einkaufen").performClick()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("Einkaufen\nMilch")
            compose.onNode(hasText("Speichern") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Einkaufen\nMilch").assertIsDisplayed()
        }
        assertEquals(listOf("Steuer", "Einkaufen\nMilch"), texts())
    }

    @Test
    fun `an entry deleted comes back with Rückgängig, and an empty inbox says so`() {
        app.inbox("Steuer")

        launchApp().use {
            openInbox()
            compose.onNode(hasText("Löschen") and hasClickAction()).performClick()
            compose.onNodeWithText("Der Eingang ist leer").assertIsDisplayed()

            compose.onNodeWithText("Rückgängig").performClick()
            compose.onNodeWithText("Steuer").assertIsDisplayed()
        }
        assertEquals(listOf("Steuer"), texts())
    }

    @Test
    fun `sorted in as a to-do, the first line is its title and the rest its description, and it leaves once saved`() {
        app.inbox("Zahnarzt anrufen\nwegen Kontrolle")

        launchApp().use {
            openInbox()
            compose.onNode(hasText("To-do") and hasClickAction()).performClick()
            compose.onNode(isHeading() and hasText("Neues To-do")).assertIsDisplayed()
            compose.onNode(hasSetTextAction() and hasText("Titel")).assertTextContains("Zahnarzt anrufen")
            compose.onNode(hasSetTextAction() and hasText("Beschreibung (optional)"))
                .assertTextContains("wegen Kontrolle")

            // Left without saving, it stays in the inbox.
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(isHeading() and hasText("Eingang")).assertIsDisplayed()
            assertEquals(1, texts().size)

            compose.onNode(hasText("To-do") and hasClickAction()).performClick()
            compose.onNode(hasText("Speichern") and hasClickAction()).performClick()

            compose.onNodeWithText("Der Eingang ist leer").assertIsDisplayed()
        }
        assertEquals(emptyList<String>(), texts())
        val todo = runBlocking { app.todos.open().first().single().todo }
        assertEquals("Zahnarzt anrufen" to "wegen Kontrolle", todo.title to todo.description)
    }

    @Test
    fun `sorted in as a note or a routine, it leaves the inbox once saved`() {
        app.inbox("Vitamine")
        app.inbox("Rezept Linsensuppe\nRote Linsen\nKokosmilch")

        launchApp().use {
            openInbox()
            compose.onAllNodes(hasText("Notiz") and hasClickAction())[0].performClick()
            compose.onNode(hasSetTextAction() and hasText("Titel")).assertTextContains("Rezept Linsensuppe")
            compose.onNode(hasText("Speichern") and hasClickAction()).performClick()

            compose.onNode(hasText("Routine") and hasClickAction()).performClick()
            compose.onNode(hasSetTextAction() and hasText("Name")).assertTextContains("Vitamine")
            compose.onNode(hasText("Speichern") and hasClickAction()).performClick()

            compose.onNodeWithText("Der Eingang ist leer").assertIsDisplayed()
        }
        val note = runBlocking { app.notes.editing(1) }?.original
        assertEquals("Rezept Linsensuppe" to "Rote Linsen\nKokosmilch", note?.title to note?.text)
        assertEquals(listOf("Vitamine"), runBlocking { app.routines.inUse().first() }.map { it.name })
    }

    @Test
    fun `the search finds what is in the inbox and opens it`() {
        app.inbox("Fahrrad reparieren\nKette ölen")

        launchApp().use {
            compose.onNodeWithContentDescription("Suchen").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("kette")
            compose.waitUntil { compose.onAllNodes(hasText("Fahrrad reparieren")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fahrrad reparieren").performClick()

            compose.onNode(isHeading() and hasText("Eingang")).assertIsDisplayed()
        }
    }

    @Test
    fun `yesterday has no line for thoughts`() {
        launchApp().use {
            compose.onNode(hasText("Gestern", substring = true) and hasClickAction()).performClick()

            capture().assertDoesNotExist()
        }
    }

    private fun capture() = compose.onNode(hasSetTextAction() and hasText("Was geht dir durch den Kopf?"))

    private fun openInbox() = compose.onNode(hasText("Eingang", substring = true) and hasClickAction()).performClick()

    private fun texts() = runBlocking { app.inbox.entries().first().map { it.text } }
}
