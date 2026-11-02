package de.emilyshub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.notes.Container
import de.emilyshub.notes.NoteDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The archive of notes (SPEC.md §7): notes and folders archived from their menus or the editor, found in the archive
 * beside the tabs, only to read there, brought back where they were, or deleted for good after asking.
 */
@RunWith(AndroidJUnit4::class)
class NoteArchiveScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a note archived from its menu leaves the list, reads in the archive only, and comes back where it was`() {
        runBlocking { app.notes.create(NoteDraft("Pizza", "Teig **24 h** gehen lassen")) }

        launchApp().use {
            openNotes()
            menuOf("Pizza")
            compose.onNodeWithText("Archivieren").performClick()
            compose.onNodeWithText("Pizza").assertDoesNotExist()

            openArchive()
            compose.onNodeWithText("Allgemein").assertIsDisplayed()
            compose.onNodeWithText("Pizza").performClick()
            compose.onNodeWithText("Im Archiv").assertIsDisplayed()
            compose.onNodeWithText("Teig 24 h gehen lassen").assertIsDisplayed()
            compose.onNodeWithText("Bearbeiten").assertDoesNotExist()
            compose.onNodeWithText("Speichern").assertDoesNotExist()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNodeWithText("Wiederherstellen").performClick()
            compose.onNodeWithText("Nichts archiviert. Notizen und Ordner archivierst du über ihr Menü.").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Pizza").assertIsDisplayed()
        }
    }

    @Test
    fun `a folder archived shows what is in it in the archive, and is deleted there for good after asking`() {
        val grammar = runBlocking { app.notes.createFolder("Grammatik", Container()) }
        runBlocking { app.notes.create(NoteDraft("Verben", "kataba", Container(null, grammar))) }

        launchApp().use {
            openNotes()
            tab("Allgemein").performClick()
            menuOf("Grammatik")
            compose.onNodeWithText("Archivieren").performClick()
            compose.onNodeWithText("Grammatik").assertDoesNotExist()

            openArchive()
            compose.onNodeWithText("📁 Grammatik").performClick()
            compose.onNodeWithText("Verben").assertIsDisplayed()
            compose.onNodeWithText("Wiederherstellen").assertDoesNotExist()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNodeWithContentDescription("„Grammatik“ endgültig löschen").performClick()
            compose.onNodeWithText("„Grammatik“ wird mit allem darin endgültig gelöscht.").assertIsDisplayed()
            compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("📁 Grammatik").assertDoesNotExist()
        }
        assertTrue(runBlocking { app.notes.notebook().first().archived().isEmpty() })
        assertEquals(null, runBlocking { app.notes.notebook().first().folder(grammar) })
    }

    @Test
    fun `archiving in the editor saves the changes first`() {
        val pizza = runBlocking { app.notes.create(NoteDraft("Pizza", "Teig")) }

        launchApp().use {
            openNotes()
            compose.onNodeWithText("Pizza").performClick()
            compose.onNode(isSelectable() and hasText("Bearbeiten")).performClick()
            compose.onNode(hasSetTextAction() and hasText("Titel")).performTextReplacement("Pizza Napoli")
            compose.onNodeWithText("Archivieren").performClick()
            compose.onNodeWithText("Pizza Napoli").assertDoesNotExist()
        }
        val book = runBlocking { app.notes.notebook().first() }
        val note = checkNotNull(book.note(pizza))
        assertTrue(book.isArchived(note))
        assertEquals("Pizza Napoli", note.title)
    }

    private fun openNotes() = compose.onNode(hasText("Notizen") and hasClickAction()).performClick()

    private fun openArchive() {
        compose.onNodeWithContentDescription("Mehr zu „Notizen“").performClick()
        compose.onNodeWithText("Archiv").performClick()
    }

    private fun menuOf(name: String) =
        compose.onNodeWithContentDescription("Mehr zu „$name“").performSemanticsAction(SemanticsActions.OnClick)

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))
}
