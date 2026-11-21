package de.emilyshub

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
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
import de.emilyshub.data.Saving
import de.emilyshub.languages.CardDraft
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
 * list as a to-do, a note, a routine or a card (#152), or deleted.
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
    fun `without a language not archived an entry offers no card`() {
        app.inbox("kitab – Buch")
        runBlocking { app.languages.setLanguageArchived(language("Arabisch"), archived = true) }

        launchApp().use {
            openInbox()
            compose.onNode(hasText("To-do") and hasClickAction()).assertIsDisplayed()
            compose.onNode(hasText("Karte") and hasClickAction()).assertDoesNotExist()
        }
    }

    @Test
    fun `sorted in as a card of the only language, its first line is its sides, the rest its note, and it leaves once saved`() {
        app.inbox("kitab – Buch\nPlural kutub")
        val arabic = language("Arabisch")

        launchApp().use {
            openInbox()
            compose.onNode(hasText("Karte") and hasClickAction()).performClick()
            compose.onNode(isHeading() and hasText("Neue Karte")).assertIsDisplayed()
            compose.onNode(hasSetTextAction() and hasText("Vorderseite")).assertTextContains("kitab")
            compose.onNode(hasSetTextAction() and hasText("Rückseite")).assertTextContains("Buch")
            compose.onNode(hasSetTextAction() and hasText("Notiz", substring = true)).assertTextContains("Plural kutub")
            compose.onNode(hasText("Speichern und neue") and hasClickAction()).assertDoesNotExist()

            // Left without saving, it stays in the inbox.
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(isHeading() and hasText("Eingang")).assertIsDisplayed()
            assertEquals(1, texts().size)

            compose.onNode(hasText("Karte") and hasClickAction()).performClick()
            compose.onNode(hasText("Speichern") and hasClickAction()).performClick()

            compose.onNodeWithText("Der Eingang ist leer").assertIsDisplayed()
        }
        val card = runBlocking { app.flashcards.tab(arabic).first() }?.cards?.single()?.card
        assertEquals(Triple("kitab", "Buch", "Plural kutub"), card?.let { Triple(it.front, it.back, it.note) })
    }

    @Test
    fun `with two languages a card asks which first, the one the newest card was made for on top`() {
        app.inbox("ciao")
        val arabic = language("Arabisch")
        val italian = language("Italienisch")
        runBlocking { app.flashcards.create(italian, CardDraft("grazie", "danke")) }

        launchApp().use {
            openInbox()
            compose.onNode(hasText("Karte") and hasClickAction()).performClick()
            val choices = compose.onAllNodes(hasAnyAncestor(isDialog()) and hasClickAction() and (hasText("Arabisch") or hasText("Italienisch")))
                .fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString() }
            assertEquals(listOf("Italienisch", "Arabisch"), choices)
            compose.onNode(hasText("Arabisch") and hasAnyAncestor(isDialog())).performClick()

            compose.onNode(isHeading() and hasText("Neue Karte")).assertIsDisplayed()
            compose.onNode(hasSetTextAction() and hasText("Vorderseite")).assertTextContains("ciao")
            compose.onNode(hasSetTextAction() and hasText("Rückseite")).performTextInput("hallo")
            assertEquals(1, texts().size)
            compose.onNode(hasText("Speichern") and hasClickAction()).performClick()

            compose.onNodeWithText("Der Eingang ist leer").assertIsDisplayed()
        }
        assertEquals(listOf("ciao"), runBlocking { app.flashcards.tab(arabic).first() }?.cards?.map { it.card.front })
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

    private fun language(name: String) = runBlocking { (app.languages.createLanguage(name) as Saving.Saved).id }

    private fun texts() = runBlocking { app.inbox.entries().first().map { it.text } }
}
