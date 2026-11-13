package de.emilyshub

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.notes.NoteDraft
import de.emilyshub.notes.NoteLink
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration

/**
 * Leaving an editor with its input changed asks first (SPEC.md §8, #87): by the back arrow, the back key or the gesture,
 * "Änderungen speichern?" with Speichern, Verwerfen and Weiter bearbeiten; unchanged, it closes at once.
 */
@RunWith(AndroidJUnit4::class)
class LeavingEditorsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a to-do changed asks when the back arrow leaves it, and Verwerfen leaves it as saved`() {
        app.todo("Steuer")

        launchApp().use {
            openTodo("Steuer")
            field("Titel").performTextReplacement("Steuererklärung")

            backArrow()
            question().assertIsDisplayed()
            answer("Verwerfen")

            heading("To-dos").assertIsDisplayed()
        }
        assertEquals(listOf("Steuer"), todoTitles())
    }

    @Test
    fun `a to-do unchanged closes at once on back, spaces at the edges being no change`() {
        app.todo("Steuer")

        launchApp().use { scenario ->
            openTodo("Steuer")
            field("Titel").performTextReplacement("Steuer  ")

            scenario.pressBack()

            question().assertDoesNotExist()
            heading("To-dos").assertIsDisplayed()
        }
    }

    @Test
    fun `Speichern in the question saves the to-do as the button does`() {
        app.todo("Steuer")

        launchApp().use { scenario ->
            openTodo("Steuer")
            field("Titel").performTextReplacement("Steuererklärung")

            scenario.pressBack()
            answer("Speichern")

            heading("To-dos").assertIsDisplayed()
        }
        assertEquals(listOf("Steuererklärung"), todoTitles())
    }

    @Test
    fun `Speichern in the question keeps a to-do that cannot be saved open, saying why at its field`() {
        launchApp().use { scenario ->
            openTodos()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Beschreibung (optional)").performTextReplacement("Belege sammeln")

            scenario.pressBack()
            answer("Speichern")

            question().assertDoesNotExist()
            compose.onNodeWithText("Bitte gib einen Titel ein.").assertIsDisplayed()
        }
        assertEquals(emptyList<String>(), todoTitles())
    }

    @Test
    fun `Weiter bearbeiten leaves the to-do open as typed`() {
        app.todo("Steuer")

        launchApp().use { scenario ->
            openTodo("Steuer")
            field("Titel").performTextReplacement("Steuererklärung")

            scenario.pressBack()
            answer("Weiter bearbeiten")

            question().assertDoesNotExist()
            field("Titel").assertIsDisplayed()
            compose.onNode(hasSetTextAction() and hasText("Steuererklärung")).assertIsDisplayed()
        }
        assertEquals(listOf("Steuer"), todoTitles())
    }

    @Test
    fun `the question stays through rotation and the lock`() {
        app.authenticator.deviceSecure = true
        app.todo("Steuer")

        launchApp().use { scenario ->
            compose.unlockWithFingerprint(app.authenticator)
            openTodo("Steuer")
            field("Titel").performTextReplacement("Steuererklärung")
            scenario.pressBack()

            scenario.recreate()
            question().assertIsDisplayed()

            scenario.leaveAndReturn { ShadowSystemClock.advanceBy(Duration.ofMinutes(5)) }
            compose.unlockWithFingerprint(app.authenticator)
            question().assertIsDisplayed()
        }
    }

    @Test
    fun `an editor covered by another does not ask, and keeps what was typed`() {
        val milk = app.todo("Milch")
        runBlocking { app.notes.create(NoteDraft("Einkaufsliste", links = setOf(NoteLink.Todo(milk)))) }

        launchApp().use { scenario ->
            openTodo("Milch")
            field("Titel").performTextReplacement("Hafermilch")
            compose.onNodeWithText("Einkaufsliste").performScrollTo().performClick()
            heading("Notiz bearbeiten").assertIsDisplayed()

            scenario.pressBack()

            question().assertDoesNotExist()
            compose.onNode(hasSetTextAction() and hasText("Hafermilch")).assertExists()
            scenario.pressBack()
            question().assertIsDisplayed()
        }
    }

    @Test
    fun `the routine editor asks only when changed`() = asksOnlyWhenChanged("Neue Routine", change = { field("Name").performTextReplacement("Vitamine") }) {
        compose.onNode(hasText("Routinen") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Routine anlegen").performClick()
    }

    @Test
    fun `the goal editor asks only when changed`() = asksOnlyWhenChanged("Neues Ziel", change = { field("Titel").performTextReplacement("Arabisch A2") }) {
        compose.openGoals()
        compose.onNodeWithContentDescription("Ziel anlegen").performClick()
    }

    @Test
    fun `the note editor asks only when changed`() = asksOnlyWhenChanged("Neue Notiz", change = { field("Titel").performTextReplacement("Brot") }) {
        compose.onNode(hasText("Notizen") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Notiz anlegen").performClick()
    }

    @Test
    fun `the place editor asks only when changed`() = asksOnlyWhenChanged("Neuer Ort", change = { field("Name").performTextReplacement("Zuhause") }) {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Orte verwalten").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Ort anlegen").performClick()
    }

    @Test
    fun `the booking editor asks only when changed`() =
        asksOnlyWhenChanged("Neue Buchung", change = { field("Beschreibung (optional)").performScrollTo().performTextReplacement("Brot") }) {
            openLedger()
            compose.onNodeWithContentDescription("Buchung anlegen").performClick()
        }

    @Test
    fun `the fixed booking editor asks only when changed`() =
        asksOnlyWhenChanged("Neue Fixbuchung", change = { field("Beschreibung (optional)").performScrollTo().performTextReplacement("Miete") }) {
            openLedger()
            compose.onNodeWithContentDescription("Fixbuchungen").performClick()
            compose.onNodeWithContentDescription("Fixbuchung anlegen").performClick()
        }

    @Test
    fun `the Abo editor asks only when changed`() = asksOnlyWhenChanged("Neues Abo", change = { field("Name").performTextReplacement("Streaming") }) {
        openLedger()
        compose.onNodeWithContentDescription("Abos").performClick()
        compose.onNodeWithContentDescription("Abo anlegen").performClick()
    }

    @Test
    fun `the session editor asks only when changed`() =
        asksOnlyWhenChanged("Neue Session", change = { field("Notiz (optional)").performScrollTo().performTextReplacement("Verben") }) {
            openLanguages()
            compose.onNodeWithContentDescription("Session eintragen").performClick()
        }

    @Test
    fun `Verwerfen in the session editor lets go of what was imported for it`() {
        val pdf = app.lessonDocuments.put("Blatt.pdf", "%PDF-1.4".toByteArray())

        launchApp().use { scenario ->
            openLanguages()
            compose.onNodeWithContentDescription("Session eintragen").performClick()
            compose.onNode(hasText("PDF") and hasClickAction()).performScrollTo().performClick()
            scenario.pickFile(Uri.parse(pdf))
            compose.onNodeWithContentDescription("PDF Blatt.pdf").performScrollTo().assertIsDisplayed()
            assertEquals(1, attachmentFiles().size)

            backArrow()
            answer("Verwerfen")

            heading("Neue Session").assertDoesNotExist()
        }
        assertEquals(emptyList<String>(), attachmentFiles())
    }

    @Test
    fun `the teacher editor asks only when changed`() = asksOnlyWhenChanged("Neuer Lehrer", change = { field("Name").performTextReplacement("Amal") }) {
        openLanguages()
        compose.onNodeWithContentDescription("Menü der Sprachen").performClick()
        compose.onNodeWithText("Lehrer verwalten").performClick()
        compose.onNodeWithContentDescription("Lehrer anlegen").performClick()
    }

    @Test
    fun `the budget calculator asks only when changed`() =
        asksOnlyWhenChanged(
            "Budget-Rechner",
            change = {
                compose.onNode(hasScrollAction()).performScrollToNode(hasSetTextAction() and hasText("Lebensmittel"))
                field("Lebensmittel").performTextReplacement("400")
            },
        ) {
            openLedger()
            compose.onNodeWithContentDescription("Kategorien").performClick()
            compose.onNodeWithText("Budget-Rechner").performClick()
        }

    /**
     * The editor headed [editor], opened by [open] from the start of the app: left unchanged by the back key, it closes at once; left by the back
     * arrow after [change], it asks, and Verwerfen closes it.
     */
    private fun asksOnlyWhenChanged(editor: String, change: () -> Unit, open: () -> Unit) {
        launchApp().use { scenario ->
            open()
            heading(editor).assertIsDisplayed()
            scenario.pressBack()
            question().assertDoesNotExist()
            heading(editor).assertDoesNotExist()
        }
        launchApp().use {
            open()
            change()
            backArrow()
            question().assertIsDisplayed()
            answer("Verwerfen")
            heading(editor).assertDoesNotExist()
        }
    }

    /** The files of the lessons' attachments on the device, saved or still in an editor. */
    private fun attachmentFiles() = File(app.filesDir, "attachments").listFiles().orEmpty().filter { it.isFile }.map { it.name }

    private fun openLedger() {
        compose.onNode(hasText("Notizen") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Haushaltsbuch öffnen").performClick()
    }

    private fun openLanguages() {
        compose.onNode(isSelectable() and hasText("Sonstiges")).performClick()
        compose.onNode(hasText("Sprachen") and hasClickAction()).performClick()
    }

    private fun openTodos() = compose.onNode(hasText("To-dos") and hasClickAction()).performClick()

    private fun openTodo(title: String) {
        openTodos()
        compose.onNodeWithText(title).performClick()
    }

    private fun todoTitles() = runBlocking { app.todos.open().first().map { it.todo.title } }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun heading(text: String) = compose.onNode(isHeading() and hasText(text))

    private fun backArrow() = compose.onNodeWithContentDescription("Zurück").performClick()

    private fun ActivityScenario<MainActivity>.pressBack() {
        compose.waitForIdle()
        onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun question() = compose.onNode(hasText("Änderungen speichern?") and hasAnyAncestor(isDialog()))

    private fun answer(label: String) = compose.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
}
