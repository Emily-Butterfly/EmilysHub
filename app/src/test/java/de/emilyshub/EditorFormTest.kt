package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.todos.WakeTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * The editors (SPEC.md §8, #91): a new entry opens with the keyboard in its first field, one saved does not; "Speichern"
 * is one button at the bottom; "Fertig" in the title of a new to-do or goal saves it.
 */
@RunWith(AndroidJUnit4::class)
class EditorFormTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a new to-do opens with its title focused, one saved does not`() {
        app.todo("Steuer")

        launchApp().use {
            openTodos()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Titel").assertIsFocused()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNodeWithText("Steuer").performClick()
            field("Titel").assertIsNotFocused()
        }
    }

    @Test
    fun `the first field of each new entry is focused`() {
        launchApp().use {
            compose.onNode(hasText("Routinen") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            field("Name").assertIsFocused()
            back()

            compose.onNode(hasText("Notizen") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Notiz anlegen").performClick()
            field("Titel").assertIsFocused()
            back()

            compose.onNodeWithContentDescription("Haushaltsbuch öffnen").performClick()
            compose.onNodeWithContentDescription("Buchung anlegen").performClick()
            field("Betrag in Euro").assertIsFocused()
        }
    }

    @Test
    fun `Speichern at the bottom saves`() {
        launchApp().use {
            openTodos()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Titel").performTextInput("Steuer")

            compose.onNode(hasText("Speichern") and hasClickAction()).performClick()

            heading("To-dos").assertIsDisplayed()
        }
        assertEquals(listOf("Steuer"), titles())
    }

    @Test
    fun `Fertig in the title of a new to-do saves it`() {
        launchApp().use {
            openTodos()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Titel").performTextInput("Steuer")

            field("Titel").performImeAction()

            heading("To-dos").assertIsDisplayed()
        }
        assertEquals(listOf("Steuer"), titles())
    }

    @Test
    fun `Fertig in the name of a new routine goes on to the next field instead`() {
        launchApp().use {
            compose.onNode(hasText("Routinen") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            field("Name").performTextInput("Vitamine")

            field("Name").performImeAction()

            heading("Neue Routine").assertIsDisplayed()
            field("Name").assertIsNotFocused()
        }
    }

    @Test
    fun `a missing title keeps the editor open, the title focused`() {
        launchApp().use {
            openTodos()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Beschreibung (optional)").performClick()

            compose.onNode(hasText("Speichern") and hasClickAction()).performClick()

            heading("Neues To-do").assertIsDisplayed()
            compose.onNodeWithText("Bitte gib einen Titel ein.").performScrollTo().assertIsDisplayed()
            field("Titel").assertIsFocused()
        }
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun `a problem there as the editor opens leaves it at its top`() {
        app.todo("Anrufen", wake = WakeTime.At(LocalDate.parse("2026-10-23"), LocalTime.of(6, 30)))

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Anrufen").performClick()

            compose.onNodeWithText("schon vorbei", substring = true).assertExists()
            field("Titel").assertIsDisplayed()
        }
    }

    @Test
    fun `a problem found on saving comes into view, the first one`() {
        launchApp().use {
            compose.onNodeWithContentDescription("Einstellungen").performClick()
            compose.onNodeWithText("Orte verwalten").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Ort anlegen").performClick()
            field("Name").performTextInput("Zuhause")

            compose.onNode(hasText("Speichern") and hasClickAction()).performClick()

            compose.onNodeWithText("Ein Ort braucht mindestens ein WLAN", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `the first field of a new goal, Abo and teacher is focused too`() {
        launchApp().use {
            compose.openGoals()
            compose.onNodeWithContentDescription("Ziel anlegen").performClick()
            field("Titel").assertIsFocused()
        }
        launchApp().use {
            compose.onNode(hasText("Notizen") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Haushaltsbuch öffnen").performClick()
            compose.onNodeWithContentDescription("Abos").performClick()
            compose.onNodeWithContentDescription("Abo anlegen").performClick()
            field("Name").assertIsFocused()
        }
        launchApp().use {
            compose.onNode(isSelectable() and hasText("Sonstiges")).performClick()
            compose.onNode(hasText("Sprachen") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Menü der Sprachen").performClick()
            compose.onNodeWithText("Lehrer verwalten").performClick()
            compose.onNodeWithContentDescription("Lehrer anlegen").performClick()
            field("Name").assertIsFocused()
        }
    }

    private fun titles() = runBlocking { app.todos.open().first().map { it.todo.title } }

    private fun openTodos() = compose.onNode(hasText("To-dos") and hasClickAction() and isSelectable()).performClick()

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun heading(text: String) = compose.onNode(isHeading() and hasText(text))

    private fun back() = compose.onNodeWithContentDescription("Zurück").performClick()
}
