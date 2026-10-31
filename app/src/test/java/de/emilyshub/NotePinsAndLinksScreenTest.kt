package de.emilyshub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.notes.Container
import de.emilyshub.notes.NoteDraft
import de.emilyshub.notes.NoteLink
import de.emilyshub.notes.NoteSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pinning and linking notes (SPEC.md §7): folders and notes pinned come first, folders before notes; a note pinned on
 * Heute shows there and opens; a note linked to a goal, a to-do and a routine shows in their editors too.
 */
@RunWith(AndroidJUnit4::class)
class NotePinsAndLinksScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `pinned folders and notes come first, and a note pinned on Heute shows there, opens, and is taken off again`() {
        runBlocking {
            app.notes.sort(Container(), NoteSort.Name)
            app.notes.createFolder("Archiv", Container())
            app.notes.createFolder("Wichtig", Container())
            app.notes.create(NoteDraft("Alphabet"))
            app.notes.create(NoteDraft("Zahlen", "1 bis 10"))
        }

        launchApp().use {
            openNotes()
            tab("Allgemein").performClick()
            assertOrder("Archiv", "Wichtig", "Alphabet", "Zahlen")
            more("Wichtig")
            compose.onNodeWithText("Anpinnen").performClick()
            more("Zahlen")
            compose.onNodeWithText("Anpinnen").performClick()
            assertOrder("Wichtig", "Zahlen", "Archiv", "Alphabet")

            more("Zahlen")
            compose.onNodeWithText("In Heute anpinnen").performClick()
            compose.onNode(hasText("Heute") and hasClickAction()).performClick()
            compose.onNodeWithText("Angepinnte Notizen").assertIsDisplayed()
            compose.onNodeWithText("Zahlen").performClick()
            compose.onNodeWithText("1 bis 10").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNodeWithContentDescription("„Zahlen“ aus Heute nehmen").performClick()
            compose.onNodeWithText("Angepinnte Notizen").assertDoesNotExist()
        }
    }

    @Test
    fun `a note is linked in its editor to a goal, a to-do and a routine, which show it in their editors`() {
        val vocabulary = app.wish("Wortschatz")
        val dictionary = app.todo("Wörterbuch kaufen")
        app.routine("Vokabeln", "20:00")
        val verbs = runBlocking { app.notes.create(NoteDraft("Verben")) }

        launchApp().use {
            openNotes()
            compose.onNodeWithText("Verben").performClick()
            compose.onNode(isSelectable() and hasText("Bearbeiten")).performClick()
            link("Ziel", "Wortschatz")
            link("To-do", "Wörterbuch kaufen")
            link("Routine", "Vokabeln")
            compose.onNodeWithContentDescription("Verknüpfung mit „Vokabeln“ lösen").performScrollTo().performClick()
            compose.onNodeWithText("Routine · Vokabeln").assertDoesNotExist()
            compose.onNode(isSelectable() and hasText("Vorschau")).performClick()
            compose.onNodeWithText("Ziel · Wortschatz").assertIsDisplayed()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNode(hasText("To-dos") and hasClickAction()).performClick()
            compose.onNodeWithText("Wörterbuch kaufen").performClick()
            compose.onNodeWithText("Verknüpfte Notizen").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Verben").performScrollTo().assertIsDisplayed()
        }
        assertEquals(setOf(NoteLink.Goal(vocabulary), NoteLink.Todo(dictionary)), runBlocking { app.notes.linksOf(verbs) })
    }

    @Test
    fun `a note is linked from a to-do too, opens from there, and back the to-do is as it was typed`() {
        app.todo("Wörterbuch kaufen")
        runBlocking { app.notes.create(NoteDraft("Verben", "kataba")) }

        launchApp().use {
            compose.onNode(hasText("To-dos") and hasClickAction()).performClick()
            compose.onNodeWithText("Wörterbuch kaufen").performClick()
            compose.onNodeWithText("Notiz verknüpfen").performScrollTo().performClick()
            compose.onNode(hasText("Verben") and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasSetTextAction() and hasText("Wörterbuch kaufen")).performTextReplacement("Wörterbuch Arabisch kaufen")

            compose.onNodeWithText("Verben").performScrollTo().performClick()
            compose.onNodeWithText("kataba").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(hasSetTextAction() and hasText("Wörterbuch Arabisch kaufen")).assertExists()

            compose.onNodeWithContentDescription("Verknüpfung mit „Verben“ lösen").performScrollTo().performClick()
            compose.onNodeWithText("Verben").assertDoesNotExist()
        }
        val verbs = runBlocking { app.notes.notebook().first().all().single().id }
        assertEquals(emptySet<NoteLink>(), runBlocking { app.notes.linksOf(verbs) })
    }

    @Test
    fun `a to-do ticked off opens from the archive, showing its notes`() {
        val milk = app.todo("Milch kaufen")
        runBlocking {
            app.notes.create(NoteDraft("Einkaufsliste", links = setOf(NoteLink.Todo(milk))))
            app.todos.markDone(milk)
        }

        launchApp().use {
            compose.onNode(hasText("To-dos") and hasClickAction()).performClick()
            compose.onNodeWithText("Archiv", substring = true).performScrollTo().performClick()
            compose.onNodeWithText("Milch kaufen").performClick()
            compose.onNodeWithText("Einkaufsliste").performScrollTo().assertIsDisplayed()
        }
    }

    private fun openNotes() = compose.onNode(hasText("Notizen") and hasClickAction()).performClick()

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    /** Opens the menu of [name] by its action, as a screen reader does: on the small test screen, "+" lies over it. */
    private fun more(name: String) {
        compose.onNodeWithContentDescription("Mehr zu „$name“").performSemanticsAction(SemanticsActions.OnClick)
    }

    /** Links the note open in its editor to the [kind] named [name]. */
    private fun link(kind: String, name: String) {
        compose.onNodeWithText("Verknüpfen").performScrollTo().performClick()
        compose.onNode(isSelectable() and hasText(kind) and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasText(name) and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("$kind · $name").performScrollTo().assertIsDisplayed()
    }

    private fun assertOrder(vararg names: String) {
        val tops = names.map { compose.onNodeWithText(it, useUnmergedTree = true).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
    }
}
