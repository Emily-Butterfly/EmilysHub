package de.emilyshub

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.Priority
import de.emilyshub.todos.Due
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/** The To-dos tab (SPEC.md §5): to-dos made and changed in the editor, ordered, filtered, and ticked off into the archive. */
@RunWith(AndroidJUnit4::class)
class TodosScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `a to-do is made with title, description, due day and time, priority, checklist and tags, and listed`() {
        app.tag("Steuern")

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Noch keine To-dos", substring = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()

            field("Titel").performTextInput("Steuererklärung")
            field("Beschreibung (optional)").performTextInput("Belege sammeln")
            option("Datum + Uhrzeit").tap()
            compose.onNode(hasText("08:00") and hasClickAction()).tap()
            typeTime(hour = "18", minute = "00")
            option("Hoch").tap()
            compose.onNodeWithText("Unterpunkt hinzufügen").tap()
            field("Unterpunkt").performTextInput("Belege")
            compose.onNode(hasText("Steuern") and hasClickAction()).tap()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Steuererklärung").assertIsDisplayed()
            compose.onNodeWithText("Fällig: heute, 18:00").assertIsDisplayed()
        }
        val saved = runBlocking { app.todos.open().first().single() }
        assertEquals(Due.At(today, LocalTime.of(18, 0)), saved.todo.due)
        assertEquals("Belege sammeln", saved.todo.description)
        assertEquals(Priority.High, saved.todo.priority)
        assertEquals(listOf("Belege"), saved.checklistItems.map { it.text })
        assertEquals(1, saved.tagIds.size)
    }

    @Test
    fun `a to-do is due on a day without a time, or whenever`() {
        launchApp().use {
            openTodos()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Titel").performTextInput("Müll")
            option("Datum").tap()
            compose.onNodeWithText("23.10.2026").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Fällig: heute").assertIsDisplayed()
        }
        assertEquals(listOf(Due.On(today)), runBlocking { app.todos.open().first().map { it.todo.due } })
    }

    @Test
    fun `a to-do needs a title before it is saved`() {
        launchApp().use {
            openTodos()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()

            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Bitte gib einen Titel ein.").assertIsDisplayed()
        }
        assertEquals(0, runBlocking { app.todos.open().first().size })
    }

    @Test
    fun `a to-do is changed in the editor, and deleted there, after asking`() {
        app.todo("Steuer")

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Steuer").performClick()
            field("Titel").performTextReplacement("Steuererklärung")
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Steuererklärung").assertIsDisplayed()

            compose.onNodeWithText("Steuererklärung").performClick()
            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNode(hasText("Löschen") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Noch keine To-dos", substring = true).assertIsDisplayed()
        }
        assertEquals(0, runBlocking { app.todos.open().first().size + app.todos.archived().first().size })
    }

    @Test
    fun `ticked off in the list, a to-do goes to the archive, which can be undone right away`() {
        app.todo("Steuer")

        launchApp().use {
            openTodos()
            compose.onNodeWithContentDescription("„Steuer“ abhaken").assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("„Steuer“ abhaken").performClick()
            compose.mainClock.advanceTimeBy(SNACKBAR_SHOWS)
            compose.onNodeWithText("„Steuer“ erledigt").assertIsDisplayed()
            compose.onNodeWithText("Rückgängig").performClick()
            compose.mainClock.autoAdvance = true

            compose.onNodeWithContentDescription("„Steuer“ abhaken").assertIsDisplayed()
        }
        assertEquals(listOf("Steuer"), runBlocking { app.todos.open().first().map { it.todo.title } })
    }

    @Test
    fun `in the archive, a to-do done is restored, or deleted for good after asking`() {
        val taxes = app.todo("Steuer")
        val window = app.todo("Fenster")
        runBlocking {
            app.todos.markDone(taxes)
            app.todos.markDone(window)
        }

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Archiv (2)").performScrollTo().performClick()
            compose.onAllNodesWithText("Erledigt am 23.10.2026").assertCountEquals(2)

            compose.onNode(hasText("Wiederherstellen") and hasAnyAncestor(hasText("Steuer"))).performClick()
            compose.onNodeWithContentDescription("„Fenster“ endgültig löschen").performClick()
            compose.onNodeWithText("Endgültig löschen?").assertIsDisplayed()
            compose.onNode(hasText("Löschen") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Nichts archiviert.").assertIsDisplayed()

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithContentDescription("„Steuer“ abhaken").assertIsDisplayed()
        }
        assertEquals(emptyList<String>(), runBlocking { app.todos.archived().first().map { it.title } })
    }

    @Test
    fun `the to-dos are ordered by due, the overdue ones first, or by priority`() {
        app.todo("Morgen", due = Due.On(today.plusDays(1)), priority = Priority.Low)
        app.todo("Irgendwann", priority = Priority.High)
        app.todo("Seit gestern", due = Due.On(today.minusDays(1)))

        launchApp().use {
            openTodos()
            assertEquals(listOf("Seit gestern", "Morgen", "Irgendwann"), listed())
            compose.onNodeWithText("Überfällig: gestern").assertIsDisplayed()

            option("Nach Priorität").performClick()
            assertEquals(listOf("Irgendwann", "Seit gestern", "Morgen"), listed())

            option("Nach Fälligkeit").performClick()
            assertEquals(listOf("Seit gestern", "Morgen", "Irgendwann"), listed())
        }
    }

    @Test
    fun `the to-dos are filtered by a tag, and say so when none has it`() {
        val taxes = app.tag("Steuern")
        app.tag("Haushalt")
        app.todo("Steuer", tags = setOf(taxes))
        app.todo("Fenster")

        launchApp().use {
            openTodos()
            chip("Steuern").performClick()
            assertEquals(listOf("Steuer"), listed())

            chip("Haushalt").performClick()
            compose.onNodeWithText("Kein To-do mit dem Tag „Haushalt“.").assertIsDisplayed()

            chip("Alle").performClick()
            assertEquals(listOf("Fenster", "Steuer"), listed())
        }
    }

    private fun openTodos() = compose.onNode(hasText("To-dos") and hasClickAction()).performClick()

    /** The titles of the to-dos listed, in their order, by the boxes that tick them off. */
    private fun listed() = compose.onAllNodes(hasContentDescription("abhaken", substring = true)).fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.ContentDescription].single().removePrefix("„").removeSuffix("“ abhaken") }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    /** A chip of the tag filter, not the tags shown with a to-do. */
    private fun chip(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))

    private fun option(label: String) = compose.onNode(isSelectable() and hasText(label))

    /** Scrolls the editor to the node first: its form is longer than the test screen. */
    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    /** Types the time into the open time dialog and confirms it. */
    private fun typeTime(hour: String, minute: String) {
        val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        fields[0].performTextReplacement(hour)
        fields[1].performTextReplacement(minute)
        compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
    }

    private companion object {
        /** Long enough for the snackbar to come in, short of it going away again. */
        const val SNACKBAR_SHOWS = 1_000L
    }
}
