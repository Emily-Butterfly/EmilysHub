package de.emilyshub

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.routines.OccurrenceStatus.Skipped
import de.emilyshub.todos.Due
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate

/** The heatmap under Heute (SPEC.md §1): what was done each day of the last six months. */
@RunWith(AndroidJUnit4::class)
class TodayHeatmapTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun allowReminders() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `each day reads its date and how much was done, only routines done and to-dos ticked off counting`() {
        val vitamins = app.routine("Vitamine", "07:30")
        val yoga = app.routine("Yoga", "18:00")
        app.history(vitamins, "2026-10-20" to Done, "2026-10-21" to Missed, "2026-10-22" to Done)
        app.history(yoga, "2026-10-20" to Done, "2026-10-22" to Skipped)
        runBlocking { app.routines.archive(yoga) } // archived, what it did still counts

        launchApp().use {
            day("Dienstag, 20. Oktober: 2 erledigt").assertExists()
            day("Mittwoch, 21. Oktober: 0 erledigt").assertExists()
            day("Donnerstag, 22. Oktober: 1 erledigt").assertExists()
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp") // the Galaxy A52, where it all fits below the line for thoughts
    fun `tapping a day shows its date and count`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-14" to Done)

        launchApp().use {
            day("Mittwoch, 14. Oktober: 1 erledigt").performClick()

            compose.onNodeWithText("Mi, 14.10.: 1 erledigt").assertIsDisplayed()
            day("Mittwoch, 14. Oktober: 1 erledigt").assert(stateDescription("ausgewählt"))
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp") // the Galaxy A52, where it all fits below the line for thoughts
    fun `it counts at once what is ticked off today, and undone`() {
        app.routine("Vitamine", "07:30")
        app.todo("Arzt anrufen", due = Due.On(LocalDate.parse("2026-10-23")))

        launchApp().use {
            day("Freitag, 23. Oktober: 0 erledigt").assertExists()

            compose.onNode(isToggleable() and hasText("Vitamine")).performClick()
            compose.onNode(isToggleable() and hasText("Arzt anrufen")).performClick()
            day("Freitag, 23. Oktober: 2 erledigt").assertExists()

            compose.openPast()
            compose.onNode(isToggleable() and hasText("Vitamine")).performClick()
            day("Freitag, 23. Oktober: 1 erledigt").assertExists()
        }
    }

    @Test
    fun `a to-do deleted for good no longer counts`() {
        val call = app.todo("Arzt anrufen", due = Due.On(LocalDate.parse("2026-10-23")))
        runBlocking { app.todos.markDone(call) }

        launchApp().use {
            day("Freitag, 23. Oktober: 1 erledigt").assertExists()

            runBlocking { app.todos.delete(call) }
            day("Freitag, 23. Oktober: 0 erledigt").assertExists()
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp") // the Galaxy A52, where it all fits below the line for thoughts
    fun `it is there with nothing due today`() {
        launchApp().use {
            compose.onNodeWithText("Erledigt, letzte 6 Monate").assertIsDisplayed()
            day("Freitag, 23. Oktober: 0 erledigt").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp") // the Galaxy A52
    fun `it fits the width of the Galaxy A52 without scrolling sideways`() {
        launchApp().use {
            val width = compose.onRoot().getBoundsInRoot().right
            val today = day("Freitag, 23. Oktober: 0 erledigt").getBoundsInRoot()
            val firstWeek = day("Montag, 27. April: 0 erledigt").getBoundsInRoot()

            assertTrue("Today at ${today.right} within $width", today.right <= width)
            assertTrue("The first week at ${firstWeek.left}", firstWeek.left >= 0.dp)
        }
    }

    private fun stateDescription(state: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    /** A day of the heatmap, by what TalkBack reads, scrolled to if Heute scrolls. */
    private fun day(description: String) = run {
        compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
            compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(description))
        }
        compose.onNodeWithContentDescription(description)
    }
}
