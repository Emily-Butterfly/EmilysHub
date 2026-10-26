package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.notes.Container
import de.emilyshub.notes.NoteDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Notes in tabs (SPEC.md §7): written in Markdown, shown in a preview without HTML and with no link to open (SPEC.md §9);
 * "Alle" shows all of them, "Allgemein" those in no tab of the user's, which are made, renamed, put in order and deleted.
 */
@RunWith(AndroidJUnit4::class)
class NotesScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a note written in Markdown shows in its preview as such, without HTML or links, and in the tab it was made in`() {
        val arabic = runBlocking { app.notes.createTab("Arabisch") }

        launchApp().use {
            openNotes()
            compose.onNodeWithText("Noch keine Notizen. Mit „+“ schreibst du eine.").assertIsDisplayed()
            tab("Arabisch").performClick()
            compose.onNodeWithContentDescription("Notiz anlegen").performClick()
            field("Titel").performTextInput("Verben")
            compose.onNode(hasText("Liegt in") and hasText("Arabisch")).assertIsDisplayed()
            field("Text in Markdown").performTextInput(
                "# Präsens\n- [x] **kataba** – schreiben\n- [ ] qara'a\n\n<b>fett?</b> [Wörterbuch](javascript:alert(1))",
            )

            option("Vorschau").performClick()
            compose.onNode(isHeading() and hasText("Präsens")).assertIsDisplayed()
            compose.onNode(isToggleable() and hasText("kataba – schreiben")).assertIsOn()
            compose.onNode(isToggleable() and hasText("qara'a")).assertIsOff()
            compose.onNode(isToggleable() and hasClickAction()).assertDoesNotExist()
            compose.onNodeWithText("<b>fett?</b> Wörterbuch").assertIsDisplayed()
            compose.onNodeWithText("javascript", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Verben").assertIsDisplayed()
            tab("Allgemein").performClick()
            compose.onNodeWithText("Verben").assertDoesNotExist()
            compose.onNodeWithText("In diesem Tab gibt es noch keine Notizen.").assertIsDisplayed()
            tab("Alle").performClick()
            compose.onNodeWithText("Verben").assertIsDisplayed()
        }
        val saved = runBlocking { app.notes.notebook().first().all() }.single()
        assertEquals("Verben" to arabic, saved.title to saved.tabId)
    }

    @Test
    fun `a note opens in its preview, is changed and moved to another tab, and deleting it asks first`() {
        val recipes = runBlocking { app.notes.createTab("Rezepte") }
        runBlocking { app.notes.create(NoteDraft("Pizza", "Teig **24 h** gehen lassen")) }

        launchApp().use {
            openNotes()
            compose.onNodeWithText("Pizza").performClick()
            compose.onNodeWithText("Teig 24 h gehen lassen").assertIsDisplayed()
            option("Vorschau").assertIsSelected()

            option("Bearbeiten").performClick()
            field("Text in Markdown").performTextReplacement("Teig **48 h** gehen lassen")
            compose.onNode(hasText("Liegt in") and hasText("Allgemein") and hasClickAction()).performClick()
            compose.onNode(isSelectable() and hasText("Rezepte") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Speichern").performClick()

            tab("Rezepte").performClick()
            compose.onNodeWithText("Pizza").performClick()
            compose.onNodeWithText("Teig 48 h gehen lassen").assertIsDisplayed()
            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNodeWithText("Abbrechen").performClick()
            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Pizza").assertDoesNotExist()
        }
        assertEquals(emptyList<Any>(), runBlocking { app.notes.notebook().first().notesIn(Container(recipes)) })
    }

    @Test
    fun `a note needs a title, and without one it is not saved`() {
        launchApp().use {
            openNotes()
            compose.onNodeWithContentDescription("Notiz anlegen").performClick()
            field("Text in Markdown").performTextInput("- Milch")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Gib der Notiz einen Titel.").assertIsDisplayed()
        }
        assertEquals(emptyList<Any>(), runBlocking { app.notes.notebook().first().all() })
    }

    @Test
    fun `tabs are made, renamed and put in order, and deleting one moves its notes to Allgemein or deletes them, as chosen`() {
        val (arabic, recipes) = runBlocking { app.notes.createTab("Arabisch") to app.notes.createTab("Rezepte") }
        val verbs = runBlocking { app.notes.create(NoteDraft("Verben", container = Container(arabic))) }
        runBlocking { app.notes.create(NoteDraft("Pizza", container = Container(recipes))) }

        launchApp().use {
            openNotes()
            compose.onNodeWithContentDescription("Tabs verwalten").performClick()
            compose.onNodeWithContentDescription("Tab anlegen").performClick()
            nameField().performTextInput("allgemein")
            compose.onNodeWithText("Diesen Namen hat schon ein Tab.").assertIsDisplayed()
            compose.onNodeWithText("Anlegen").assertIsNotEnabled()
            nameField().performTextReplacement("Reisen")
            compose.onNodeWithText("Anlegen").performClick()

            // On its name: the arrows next to it take a tap near them.
            compose.onNodeWithText("Reisen", useUnmergedTree = true).performClick()
            nameField().performTextReplacement("Urlaub")
            compose.onNodeWithText("Umbenennen").performClick()
            compose.onNodeWithContentDescription("„Urlaub“ nach oben").performClick()
            compose.onNodeWithContentDescription("„Arabisch“ nach oben").assertIsNotEnabled()

            compose.onNodeWithContentDescription("Arabisch löschen").performClick()
            compose.onNodeWithText("Nach „Allgemein“").performClick()
            compose.onNodeWithContentDescription("Rezepte löschen").performClick()
            compose.onNodeWithText("Mitlöschen").performClick()
            compose.onNodeWithContentDescription("Urlaub löschen").performClick()
            compose.onNodeWithText("„Urlaub“ wird gelöscht.").assertIsDisplayed()
            compose.onNodeWithText("Abbrechen").performClick()
        }
        assertEquals(listOf("Urlaub"), runBlocking { app.notes.notebook().first().tabs }.map { it.name })
        val left = runBlocking { app.notes.notebook().first().all() }
        assertEquals(listOf("Verben"), left.map { it.title })
        assertNull(left.single { it.id == verbs }.tabId)
    }

    @Test
    fun `the tabs show in their order after Alle and Allgemein, and one deleted leaves Alle shown`() {
        val (arabic, recipes) = runBlocking { app.notes.createTab("Arabisch") to app.notes.createTab("Rezepte") }
        runBlocking { app.notes.arrangeTabs(listOf(recipes, arabic)) }

        launchApp().use {
            openNotes()
            tab("Alle").assertIsSelected()
            val lefts = listOf("Alle", "Allgemein", "Rezepte", "Arabisch").map { tab(it).getUnclippedBoundsInRoot().left }
            assertEquals(lefts.sorted(), lefts)
            tab("Rezepte").performClick()
            runBlocking { app.notes.deleteTabWithContents(recipes) }

            compose.onNodeWithText("Rezepte").assertDoesNotExist()
            tab("Alle").assertIsSelected()
        }
    }

    private fun openNotes() = compose.onNode(hasText("Notizen") and hasClickAction()).performClick()

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun option(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun nameField() = field("Name des Tabs")
}
