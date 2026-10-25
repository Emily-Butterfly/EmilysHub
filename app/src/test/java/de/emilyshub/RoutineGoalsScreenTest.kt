package de.emilyshub

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
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.RoutineTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Routines under goals (SPEC.md §2, §6): put under one in the routine editor, aiming at a streak or at times done, and
 * shown in the goal's tree with how far they have come; without a target they only show there.
 */
@RunWith(AndroidJUnit4::class)
class RoutineGoalsScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a routine is put under a goal in its editor, aiming at days in a row, and the goal shows how far it has come`() {
        val vocabulary = app.wish("Wortschatz")
        val routine = app.routine("Vokabeln", "20:00")
        app.history(routine, "2026-10-21" to OccurrenceStatus.Done, "2026-10-22" to OccurrenceStatus.Done)

        launchApp().use {
            compose.onNode(hasText("Routinen") and hasClickAction()).performClick()
            compose.onNodeWithText("Vokabeln").performClick()
            compose.onNodeWithText("Routinen-Ziel").assertDoesNotExist()
            compose.onNode(hasText("Ziel") and hasText("Kein Ziel")).tap()
            compose.onNode(isSelectable() and hasText("Wortschatz") and hasAnyAncestor(isDialog())).performClick()
            option("Tage in Folge").tap()
            field("Anzahl").performScrollTo().performTextReplacement("10")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNode(hasText("Wunschliste") and hasClickAction()).performClick()
            compose.onNodeWithText("Fortschritt: 20 %").assertIsDisplayed()
            compose.onNodeWithContentDescription("„Wortschatz“ aufklappen").performClick()
            compose.onNodeWithText("Routine · 2 von 10 Tagen in Folge").assertIsDisplayed()
            compose.onNodeWithText("Vokabeln").performClick()
            compose.onNodeWithText("Routine bearbeiten").assertIsDisplayed()
        }
        val saved = runBlocking { app.routines.get(routine) }
        assertEquals(vocabulary to RoutineTarget.Streak(10), saved?.goalId to saved?.target)
    }

    @Test
    fun `aiming at times done, the goal shows the times, and once reached, that it is reached`() {
        val yoga = app.wish("Yoga-Routine")
        val routine = app.routine("Yoga", "18:00", goal = yoga, target = RoutineTarget.TimesDone(2))
        val stretching = app.routine("Dehnen", "07:00", goal = app.wish("Gelenkig"), target = RoutineTarget.TimesDone(5))
        app.history(routine, "2026-10-21" to OccurrenceStatus.Done, "2026-10-22" to OccurrenceStatus.Done)
        app.history(stretching, "2026-10-22" to OccurrenceStatus.Done)

        launchApp().use {
            compose.onNode(hasText("Wunschliste") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("„Yoga-Routine“ aufklappen").performClick()
            compose.onNodeWithText("Routine · Erreicht: 2 Mal erledigt").assertIsDisplayed()
            compose.onNodeWithContentDescription("„Gelenkig“ aufklappen").performClick()
            compose.onNodeWithText("Routine · 1 von 5 Mal erledigt").assertIsDisplayed()
        }
    }

    @Test
    fun `a routine without a target only shows with its goal, counting towards nothing`() {
        app.routine("Spazieren", "12:00", goal = app.wish("Fit bleiben"))

        launchApp().use {
            compose.onNode(hasText("Wunschliste") and hasClickAction()).performClick()
            compose.onNodeWithText("Fortschritt", substring = true).assertDoesNotExist()
            compose.onNodeWithContentDescription("„Fit bleiben“ aufklappen").performClick()
            compose.onNodeWithText("Spazieren").assertIsDisplayed()
            compose.onNodeWithText("Routine").assertIsDisplayed()
        }
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun option(label: String) = compose.onNode(isSelectable() and hasText(label))

    /** Scrolls the editor to the node first: its form is longer than the test screen. */
    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()
}
