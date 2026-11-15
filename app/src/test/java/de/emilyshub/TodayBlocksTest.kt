package de.emilyshub

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.todos.Due
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
import java.time.Instant
import java.time.LocalDate

/** Heute's date line and its three blocks (SPEC.md §1, Drei Blöcke; #96). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h915dp") // the Galaxy A52, where the blocks fit below the line for thoughts
class TodayBlocksTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun allowReminders() {
        // Heute warns while the reminders lack something from Android, which is not what these tests are about.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `the date line says how far the day is, and only the date without entries`() {
        launchApp().use {
            compose.onNodeWithText("Fr., 23. Okt.").assertIsDisplayed()
        }
        app.routine("Vitamine", "07:30")
        app.routine("Tagebuch", "20:00")

        launchApp().use {
            compose.onNodeWithText("Fr., 23. Okt. · 0 von 2 erledigt").assertIsDisplayed()

            row("Vitamine").performClick()

            compose.onNodeWithText("Fr., 23. Okt. · 1 von 2 erledigt").assertIsDisplayed()
        }
    }

    @Test
    fun `the overdue to-dos are one line, folded, that opens and folds again`() {
        app.todo("Steuer", due = Due.On(FRIDAY.minusDays(3)))
        app.todo("Reifen", due = Due.On(FRIDAY.minusDays(1)))

        launchApp().use {
            foldLine("Überfällig (2)").assertStateIs("zugeklappt")
            row("Steuer").assertDoesNotExist()

            foldLine("Überfällig (2)").performClick().assertStateIs("aufgeklappt")
            assertEquals(listOf("Steuer", "Reifen"), titles())

            foldLine("Überfällig (2)").performClick().assertStateIs("zugeklappt")
            row("Steuer").assertDoesNotExist()
        }
    }

    @Test
    fun `ticked off, an entry goes to what is over, and the bar brings it back`() {
        app.routine("Vitamine", "07:30")
        app.routine("Tagebuch", "20:00")

        launchApp().use {
            row("Vitamine").performClick()

            assertEquals(listOf("Tagebuch"), titles())
            foldLine("Schon vorbei").assertIsDisplayed()
            compose.onNodeWithText("1 erledigt").assertIsDisplayed()
            compose.onNodeWithText("„Vitamine“ abgehakt").assertIsDisplayed()

            compose.onNodeWithText("Rückgängig").performClick()

            assertEquals(listOf("Vitamine", "Tagebuch"), titles())
            foldLine("Schon vorbei").assertDoesNotExist()
            assertEquals(listOf(OccurrenceStatus.Open, OccurrenceStatus.Open), statuses())
        }
    }

    @Test
    fun `what is over opens with each entry's status, and an entry unticked there is up next again`() {
        app.routine("Vitamine", "07:30")
        app.routine("Joggen", "18:00")

        launchApp().use {
            row("Vitamine").performClick()
            compose.onNodeWithContentDescription("Mehr zu „Joggen“").performClick()
            compose.onNodeWithText("Überspringen").performClick()
            compose.onNodeWithText("Für heute ist alles erledigt").assertIsDisplayed()
            compose.onNodeWithText("1 erledigt · 1 übersprungen").assertIsDisplayed()

            foldLine("Schon vorbei").performClick()
            row("Vitamine").assertIsOn()
            compose.onNodeWithText("Übersprungen").assertIsDisplayed()

            row("Vitamine").performClick()

            compose.onNodeWithText("Für heute ist alles erledigt").assertDoesNotExist()
            assertEquals(OccurrenceStatus.Open, statuses().first())
        }
    }

    @Test
    fun `a routine with a checklist stays up next until its last item is ticked off`() {
        app.routine("Zähne", "08:00", checklist = listOf("Putzen", "Zahnseide"))

        launchApp().use {
            row("Putzen").performClick()
            foldLine("Schon vorbei").assertDoesNotExist()
            row("Zähne").assertIsDisplayed()

            row("Zahnseide").performClick()

            foldLine("Schon vorbei").assertIsDisplayed()
            row("Zähne").assertDoesNotExist()
        }
    }

    @Test
    fun `a missed routine no longer shows its checklist`() {
        app.routine("Zähne", "07:30", checklist = listOf("Putzen")) // open until 11:30
        app.clock.now = Instant.parse("2026-10-23T10:00:00Z") // 12:00 in Berlin
        runBlocking { app.reminders.update() } // as the app does when it starts

        launchApp().use {
            compose.onNodeWithText("1 verpasst").assertIsDisplayed()
            foldLine("Schon vorbei").performClick()

            compose.onNodeWithText("Verpasst").assertIsDisplayed()
            row("Zähne").assertIsDisplayed()
            row("Putzen").assertDoesNotExist()
        }
    }

    @Test
    fun `another tab folds the blocks again, a screen over Heute does not`() {
        app.todo("Steuer", due = Due.On(FRIDAY.minusDays(3)))

        launchApp().use { scenario ->
            foldLine("Überfällig (1)").performClick()

            compose.onNodeWithContentDescription("Einstellungen").performClick()
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            foldLine("Überfällig (1)").assertStateIs("aufgeklappt")

            tab("To-dos").performClick()
            tab("Heute").performClick()
            foldLine("Überfällig (1)").assertStateIs("zugeklappt")
        }
    }

    private fun tab(name: String) = compose.onNode(isSelectable() and hasText(name))

    private fun row(name: String) = compose.onNode(isToggleable() and hasText(name))

    private fun foldLine(title: String) = compose.onNode(isHeading() and hasText(title, substring = true))

    private fun SemanticsNodeInteraction.assertStateIs(state: String) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state))

    private fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }

    /** The names of the entries shown, routines' and to-dos', in their order. */
    private fun titles() = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.Text].last().text }

    private companion object {
        val FRIDAY: LocalDate = LocalDate.parse("2026-10-23")
    }
}
