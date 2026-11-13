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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Saving
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.languages.TeacherEditing
import de.emilyshub.notes.NoteDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast

/**
 * Notes and sessions save themselves when left (SPEC.md §8, #88): by the back arrow, the back key or the gesture, a
 * changed draft that can be saved is saved without asking; one that cannot be asks as the other editors do (#87).
 */
@RunWith(AndroidJUnit4::class)
class SavingOnLeavingTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a note changed and left by back saves itself and says so`() {
        runBlocking { app.notes.create(NoteDraft("Pizza", "Teig gehen lassen")) }

        launchApp().use { scenario ->
            openNotes()
            compose.onNodeWithText("Pizza").performClick()
            compose.onNode(hasText("Bearbeiten") and isSelectable()).performClick()
            field("Text in Markdown").performTextReplacement("Teig 48 h gehen lassen")

            scenario.pressBack()

            question().assertDoesNotExist()
            heading("Notizen").assertIsDisplayed()
            assertEquals("Gespeichert", ShadowToast.getTextOfLatestToast())
        }
        assertEquals(listOf("Pizza" to "Teig 48 h gehen lassen"), notes())
    }

    @Test
    fun `a new note without a title takes the first line of its text as one`() {
        launchApp().use {
            openNotes()
            compose.onNodeWithContentDescription("Notiz anlegen").performClick()
            field("Text in Markdown").performTextReplacement("# Einkauf\n- Milch")

            backArrow()

            question().assertDoesNotExist()
            heading("Notizen").assertIsDisplayed()
        }
        assertEquals(listOf("Einkauf" to "# Einkauf\n- Milch"), notes())
    }

    @Test
    fun `a new note left empty makes nothing`() {
        launchApp().use { scenario ->
            openNotes()
            compose.onNodeWithContentDescription("Notiz anlegen").performClick()
            field("Titel").performTextReplacement("   ")

            scenario.pressBack()

            question().assertDoesNotExist()
            heading("Notizen").assertIsDisplayed()
        }
        assertEquals(emptyList<Pair<String, String>>(), notes())
    }

    @Test
    fun `a note that cannot be saved asks, as a title too long`() {
        launchApp().use {
            openNotes()
            compose.onNodeWithContentDescription("Notiz anlegen").performClick()
            field("Titel").performTextReplacement("x".repeat(81))

            backArrow()

            question().assertIsDisplayed()
        }
        assertEquals(emptyList<Pair<String, String>>(), notes())
    }

    @Test
    fun `a session with all it needs saves itself when left`() {
        runBlocking {
            val arabic = (app.languages.createLanguage("Arabisch") as Saving.Saved).id
            app.languages.saveTeacher(TeacherEditing.New, TeacherDraft("Amal", languageIds = setOf(arabic)))
        }

        launchApp().use { scenario ->
            openLanguages()
            compose.onNode(hasText("Arabisch") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Session eintragen").performClick()
            compose.onNode(hasText("Amal") and isSelectable()).performScrollTo().performClick()
            compose.onNode(hasText("45 Min.") and isSelectable()).performClick()

            scenario.pressBack()

            question().assertDoesNotExist()
            heading("Arabisch").assertIsDisplayed()
        }
        val arabic = runBlocking { app.languages.languages().first() }.single().language.id
        assertEquals(listOf(45), runBlocking { checkNotNull(app.languages.page(arabic).first()).lessons }.map { it.lesson.minutes })
    }

    @Test
    fun `a session without a teacher asks`() {
        runBlocking { app.languages.createLanguage("Arabisch") }

        launchApp().use { scenario ->
            openLanguages()
            compose.onNode(hasText("Arabisch") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Session eintragen").performClick()
            compose.onNode(hasText("45 Min.") and isSelectable()).performClick()

            scenario.pressBack()

            question().assertIsDisplayed()
        }
    }

    private fun notes() = runBlocking { app.testDatabase.notes().notes() }.map { it.title to it.text }

    private fun openNotes() = compose.onNode(hasText("Notizen") and hasClickAction()).performClick()

    private fun openLanguages() {
        compose.onNode(isSelectable() and hasText("Sonstiges")).performClick()
        compose.onNode(hasText("Sprachen") and hasClickAction()).performClick()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun heading(text: String) = compose.onNode(isHeading() and hasText(text))

    private fun backArrow() = compose.onNodeWithContentDescription("Zurück").performClick()

    private fun ActivityScenario<MainActivity>.pressBack() {
        compose.waitForIdle()
        onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun question() = compose.onNode(hasText("Änderungen speichern?") and hasAnyAncestor(isDialog()))
}
