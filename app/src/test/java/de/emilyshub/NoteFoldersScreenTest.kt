package de.emilyshub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.notes.Container
import de.emilyshub.notes.NoteDraft
import de.emilyshub.notes.NoteSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/**
 * Folders of notes (SPEC.md §7): made in a tab and in each other, opened up in the list and opened, with the path
 * leading back; moved, also to another tab, but never into themselves; deleted with what is in them moving up a level or
 * with it; each sorted by name, by last change or by hand.
 */
@RunWith(AndroidJUnit4::class)
class NoteFoldersScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `folders are made in each other and opened, the path leads back, and they open up in the list`() {
        val arabic = runBlocking { app.notes.createTab("Arabisch") }

        launchApp().use { scenario ->
            openNotes()
            tab("Arabisch").performClick()
            newFolder("Grammatik")
            compose.onNodeWithText("Grammatik").performClick()
            newFolder("Verben")
            compose.onNodeWithText("Verben").performClick()
            compose.onNodeWithText("In diesem Ordner liegt noch nichts.").assertIsDisplayed()
            compose.onNodeWithContentDescription("Notiz anlegen").performClick()
            compose.onNode(hasText("Liegt in") and hasText("Arabisch › Grammatik › Verben")).assertIsDisplayed()
            compose.onNode(hasSetTextAction() and hasText("Titel")).performTextInput("kataba")
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("kataba").assertIsDisplayed()

            // Back goes up a level; the path leads back to any level.
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithText("kataba").assertDoesNotExist()
            pathPart("Arabisch").performClick()
            compose.onNodeWithText("Verben").assertDoesNotExist()

            compose.onNodeWithContentDescription("„Grammatik“ aufklappen").performClick()
            compose.onNodeWithContentDescription("„Verben“ aufklappen").performClick()
            compose.onNodeWithText("kataba").assertIsDisplayed()
            compose.onNodeWithContentDescription("„Grammatik“ zuklappen").performClick()
            compose.onNodeWithText("Verben").assertDoesNotExist()

            tab("Alle").performClick()
            compose.onNodeWithText("Arabisch › Grammatik › Verben").assertIsDisplayed()
        }
        val book = runBlocking { app.notes.notebook().first() }
        val kataba = book.all().single()
        assertEquals(listOf("Grammatik", "Verben"), book.path(kataba.folderId).map { it.name })
        assertEquals(arabic, kataba.tabId)
    }

    @Test
    fun `a note and a folder are moved, also to another tab, but a folder is never offered itself or a folder in it`() {
        val (arabic, travel) = runBlocking { app.notes.createTab("Arabisch") to app.notes.createTab("Reisen") }
        val grammar = runBlocking { app.notes.createFolder("Grammatik", Container(arabic)) }
        runBlocking { app.notes.createFolder("Verben", Container(arabic, grammar)) }
        val alphabet = runBlocking { app.notes.create(NoteDraft("Alphabet", tabId = arabic)) }

        launchApp().use {
            openNotes()
            tab("Arabisch").performClick()
            more("Alphabet")
            compose.onNodeWithText("Verschieben nach…").performClick()
            choice("Grammatik").performClick()
            compose.onNodeWithText("Alphabet").assertDoesNotExist()

            more("Grammatik")
            compose.onNodeWithText("Verschieben nach…").performClick()
            choice("Verben").assertDoesNotExist()
            choice("Grammatik").assertDoesNotExist()
            choice("Reisen").performClick()

            compose.onNodeWithText("Grammatik").assertDoesNotExist()
            tab("Reisen").performClick()
            compose.onNodeWithText("Grammatik").assertIsDisplayed()
        }
        val moved = runBlocking { app.notes.get(alphabet) }
        assertEquals(Container(travel, grammar), moved?.let { Container(it.tabId, it.folderId) })
    }

    @Test
    fun `deleting a folder asks whether what is in it moves up a level or goes along`() {
        val recipes = runBlocking { app.notes.createFolder("Rezepte", Container()) }
        val baking = runBlocking { app.notes.createFolder("Backen", Container(folderId = recipes)) }
        val bread = runBlocking { app.notes.create(NoteDraft("Brot", folderId = baking)) }
        runBlocking { app.notes.createFolder("Leer", Container()) }

        launchApp().use {
            openNotes()
            tab("Allgemein").performClick()
            compose.onNodeWithContentDescription("„Rezepte“ aufklappen").performClick()
            more("Backen")
            compose.onNodeWithText("Löschen").performClick()
            compose.onNodeWithText("Eine Ebene höher").performClick()
            compose.onNodeWithText("Backen").assertDoesNotExist()
            compose.onNodeWithText("Brot").assertIsDisplayed()
            assertEquals(recipes, runBlocking { app.notes.get(bread)?.folderId })

            more("Leer")
            compose.onNodeWithText("Löschen").performClick()
            compose.onNodeWithText("„Leer“ wird gelöscht.").assertIsDisplayed()
            compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

            more("Rezepte")
            compose.onNodeWithText("Löschen").performClick()
            compose.onNodeWithText("Mitlöschen").performClick()
            compose.onNodeWithText("Rezepte").assertDoesNotExist()
        }
        val book = runBlocking { app.notes.notebook().first() }
        assertEquals(emptyList<Any>(), book.all() + book.foldersIn(Container()))
    }

    @Test
    fun `each folder is sorted as chosen, by last change, by name or by hand`() {
        val shop = runBlocking { app.notes.createFolder("Einkauf", Container()) }
        val inShop = Container(folderId = shop)
        runBlocking { app.notes.create(NoteDraft("Apfel", folderId = shop)) }
        app.clock.now += Duration.ofMinutes(1)
        runBlocking { app.notes.create(NoteDraft("Brot", folderId = shop)) }

        launchApp().use {
            openNotes()
            tab("Allgemein").performClick()
            compose.onNodeWithText("Einkauf").performClick()
            assertOrder("Brot", "Apfel")

            compose.onNodeWithText("Sortierung: Zuletzt geändert").performClick()
            choice("Name").performClick()
            assertOrder("Apfel", "Brot")

            compose.onNodeWithText("Sortierung: Name").performClick()
            choice("Manuell").performClick()
            assertOrder("Apfel", "Brot")
            more("Brot")
            compose.onNodeWithText("Nach oben").performClick()
            assertOrder("Brot", "Apfel")
        }
        assertEquals(NoteSort.Manual, runBlocking { app.notes.notebook().first().sortOf(inShop) })
    }

    private fun openNotes() = compose.onNode(hasText("Notizen") and hasClickAction()).performClick()

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun newFolder(name: String) {
        compose.onNodeWithText("Ordner anlegen").performClick()
        compose.onNode(hasSetTextAction() and hasText("Name des Ordners")).performTextInput(name)
        compose.onNodeWithText("Anlegen").performClick()
    }

    private fun pathPart(name: String) = compose.onNode(hasClickAction() and hasText(name) and !isSelectable())

    /** Opens the menu of [name] by its action, as a screen reader does: on the small test screen, "+" lies over it. */
    private fun more(name: String) {
        compose.onNodeWithContentDescription("Mehr zu „$name“").performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun choice(label: String) = compose.onNode(isSelectable() and hasText(label) and hasAnyAncestor(isDialog()))

    private fun assertOrder(vararg titles: String) {
        val tops = titles.map { compose.onNodeWithText(it).top() }
        assertEquals(tops.sorted(), tops)
    }

    private fun SemanticsNodeInteraction.top() = getUnclippedBoundsInRoot().top
}
