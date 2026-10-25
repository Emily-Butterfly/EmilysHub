package de.emilyshub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Goals on the Wunschliste tab (SPEC.md §6): subgoals to any depth, to-dos hanging under goals, the tree opened up and
 * closed, progress worked out or set by hand, and a goal done once all of its parts are.
 */
@RunWith(AndroidJUnit4::class)
class GoalsScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a subgoal is added from its goal, and shows indented below it once that is opened up`() {
        app.wish("Arabisch A2")

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Arabisch A2").performClick()
            compose.onNodeWithText("Unterziel hinzufügen").tap()

            compose.onNodeWithText("Neues Unterziel").assertIsDisplayed()
            compose.onNode(hasText("Teil von") and hasText("Arabisch A2")).tap()
            compose.onNodeWithText("Ziel wählen").assertIsDisplayed()
            compose.onNodeWithText("Abbrechen").performClick()
            field("Titel").performTextInput("Wortschatz")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Wortschatz").assertDoesNotExist()
            compose.onNodeWithContentDescription("„Arabisch A2“ aufklappen").performClick()
            compose.onNodeWithText("Wortschatz").assertIsDisplayed()
            compose.onNodeWithContentDescription("„Arabisch A2“ zuklappen").performClick()
            compose.onNodeWithText("Wortschatz").assertDoesNotExist()
        }
    }

    @Test
    fun `a to-do hangs under a goal from its editor, and ticked off in the tree, the goal is done and in the review`() {
        app.wish("Steuererklärung")
        app.todo("Belege sammeln")

        launchApp().use {
            compose.onNode(hasText("To-dos") and hasClickAction()).performClick()
            compose.onNodeWithText("Belege sammeln").performClick()
            compose.onNode(hasText("Ziel") and hasText("Keinem Ziel")).tap()
            compose.onNode(isSelectable() and hasText("Steuererklärung") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Speichern").performClick()

            openWishlist()
            compose.onNodeWithText("Fortschritt: 0 %").assertIsDisplayed()
            compose.onNodeWithContentDescription("„Steuererklärung“ aufklappen").performClick()
            compose.onNodeWithContentDescription("„Belege sammeln“ abhaken").performClick()

            compose.onNodeWithText("Steuererklärung").assertDoesNotExist()
            compose.onNodeWithText("Rückblick (1)").performClick()
            compose.onNodeWithText("Steuererklärung").assertIsDisplayed()
        }
    }

    @Test
    fun `a wish made into a to-do opens that to-do, hanging under the wish`() {
        app.wish("Steuererklärung")

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Steuererklärung").performClick()
            compose.onNodeWithText("In To-do umwandeln").tap()

            compose.onNodeWithText("To-do bearbeiten").assertIsDisplayed()
            compose.onNode(hasText("Ziel") and hasText("Steuererklärung")).performScrollTo().assertIsDisplayed()
        }
        val todo = runBlocking { app.todos.open().first().single().todo }
        assertEquals("Steuererklärung", todo.title)
        assertEquals(runBlocking { app.wishes.current().first().single().wish.id }, todo.goalId)
    }

    @Test
    fun `progress is worked out from the parts, can be set by hand, and reset`() {
        val move = app.wish("Umzug")
        val packing = app.todo("Kisten packen", goal = move)
        app.todo("Kartons kaufen", goal = move)
        runBlocking { app.todos.markDone(packing) }

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Fortschritt: 50 %").assertIsDisplayed()

            compose.onNodeWithText("Umzug").performClick()
            compose.onNodeWithText("Aus den Unterpunkten").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("50 %").assertIsDisplayed()
            compose.onNodeWithText("Selbst festlegen").tap()
            compose.onNodeWithContentDescription("Fortschritt").performSemanticsAction(SemanticsActions.SetProgress) { it(80f) }
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Fortschritt: 80 %").assertIsDisplayed()

            compose.onNodeWithText("Umzug").performClick()
            compose.onNodeWithText("Zurücksetzen auf 50 % aus den Unterpunkten").tap()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Fortschritt: 50 %").assertIsDisplayed()
        }
    }

    @Test
    fun `a goal is not offered to hang under itself, nor under its subgoals`() {
        val arabic = app.wish("Arabisch A2")
        app.wish("Wortschatz", parent = arabic)
        app.wish("Reise")

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Arabisch A2").performClick()
            compose.onNode(hasText("Teil von") and hasText("Keinem Ziel")).tap()

            compose.onNode(isSelectable() and hasText("Reise") and hasAnyAncestor(isDialog())).assertIsDisplayed()
            compose.onNode(hasText("Arabisch A2") and hasAnyAncestor(isDialog())).assertDoesNotExist()
            compose.onNode(hasText("Wortschatz") and hasAnyAncestor(isDialog())).assertDoesNotExist()
        }
    }

    @Test
    fun `deleting a goal says its subgoals go with it and its to-dos stay`() {
        val arabic = app.wish("Arabisch A2")
        app.wish("Wortschatz", parent = arabic)

        launchApp().use {
            openWishlist()
            compose.onNodeWithText("Arabisch A2").performClick()
            compose.onNodeWithContentDescription("Löschen").performClick()

            compose.onNodeWithText("„Arabisch A2“ wird mit allen Unterzielen gelöscht.", substring = true).assertIsDisplayed()
            compose.onNodeWithText("To-dos daran bleiben erhalten.", substring = true).assertIsDisplayed()
        }
    }

    private fun openWishlist() = compose.onNode(hasText("Wunschliste") and hasClickAction()).performClick()

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    /** Scrolls the editor to the node first: its form is longer than the test screen. */
    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()
}
