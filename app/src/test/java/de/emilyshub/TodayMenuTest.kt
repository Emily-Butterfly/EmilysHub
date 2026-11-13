package de.emilyshub

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import java.time.LocalDate
import java.time.LocalTime

/** The menu of each entry on Heute, and of each to-do in the To-dos tab (SPEC.md §1, Menü je Eintrag; #97). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h915dp") // the Galaxy A52, where it all fits below the line for thoughts
class TodayMenuTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun allowReminders() {
        // Heute warns while the reminders lack something from Android, which is not what these tests are about.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.settingsDao.values["reminders-asked"] = "yes"
    }

    @Test
    fun `an open routine has no button to skip in its row, but skips, drops and opens from its menu`() {
        app.routine("Joggen", "18:00")

        launchApp().use {
            compose.onNodeWithText("Überspringen").assertDoesNotExist()

            menuOf("Joggen")
            assertEquals(listOf("Überspringen", "Heute nicht", "Öffnen"), menuItems())
            item("Überspringen").performClick()

            assertEquals(listOf(OccurrenceStatus.Skipped), statuses())
        }
    }

    @Test
    fun `a long press on a row opens the same menu, and tapping it still ticks it off`() {
        app.routine("Joggen", "18:00")

        launchApp().use {
            row("Joggen").performTouchInput { longClick() }
            assertEquals(listOf("Überspringen", "Heute nicht", "Öffnen"), menuItems())
            item("Heute nicht").performClick()
            assertEquals(listOf(OccurrenceStatus.Dropped), statuses())

            compose.openPast()
            compose.onNodeWithText("Rückgängig").performClick()
            row("Joggen").performClick()
            assertEquals(listOf(OccurrenceStatus.Done), statuses())
        }
    }

    @Test
    fun `a routine skipped keeps its undo button and only opens from its menu`() {
        app.routine("Joggen", "18:00")

        launchApp().use {
            menuOf("Joggen")
            item("Überspringen").performClick()
            compose.openPast()

            compose.onNodeWithText("Rückgängig").assertIsDisplayed()
            menuOf("Joggen")
            assertEquals(listOf("Öffnen"), menuItems())
        }
    }

    @Test
    fun `Oeffnen leads to the routine's editor, and back to Heute`() {
        app.routine("Joggen", "18:00")

        launchApp().use { scenario ->
            menuOf("Joggen")
            item("Öffnen").performClick()
            compose.onNode(isHeading() and hasText("Routine bearbeiten")).assertIsDisplayed()

            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            row("Joggen").assertIsDisplayed()
        }
    }

    @Test
    fun `a to-do moves to tomorrow from its menu, keeping its time, and the bar takes it back`() {
        val id = app.todo("Arzt", due = Due.At(FRIDAY.minusDays(7), LocalTime.of(9, 0)))

        launchApp().use {
            compose.openOverdue()
            menuOf("Arzt")
            assertEquals(listOf("Morgen", "Am Wochenende", "Nächste Woche", "Datum wählen", "Öffnen"), menuItems())
            item("Morgen").performClick()

            compose.onNodeWithText("Auf Sa., 24. Okt. verschoben").assertIsDisplayed()
            row("Arzt").assertDoesNotExist()
            assertEquals(Due.At(FRIDAY.plusDays(1), LocalTime.of(9, 0)), due(id))

            compose.onNodeWithText("Rückgängig").performClick()

            assertEquals(Due.At(FRIDAY.minusDays(7), LocalTime.of(9, 0)), due(id))
        }
    }

    @Test
    fun `a to-do moves to a day picked`() {
        val id = app.todo("Müll", due = Due.On(FRIDAY))

        launchApp().use {
            menuOf("Müll")
            item("Datum wählen").performClick()
            compose.onNode(hasText("30", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Auf Fr., 30. Okt. verschoben").assertIsDisplayed()
            assertEquals(Due.On(LocalDate.parse("2026-10-30")), due(id))
        }
    }

    @Test
    fun `a to-do ticked off only opens from its menu, which leads to its editor`() {
        app.todo("Müll", due = Due.On(FRIDAY))

        launchApp().use {
            row("Müll").performClick()
            compose.openPast()
            menuOf("Müll")
            assertEquals(listOf("Öffnen"), menuItems())

            item("Öffnen").performClick()

            compose.onNode(isHeading() and hasText("To-do bearbeiten")).assertIsDisplayed()
        }
    }

    @Test
    fun `a to-do under Demnaechst has the menu too`() {
        val id = app.todo("Geschenk", due = Due.On(FRIDAY.plusDays(2)), showsDaysBefore = 3)

        launchApp().use {
            menuOf("Geschenk")
            item("Nächste Woche").performClick()

            assertEquals(Due.On(LocalDate.parse("2026-10-26")), due(id))
        }
    }

    @Test
    fun `in the To-dos tab, a to-do due whenever gets a day by moving it`() {
        val id = app.todo("Fenster putzen")

        launchApp().use {
            compose.onNode(isSelectable() and hasText("To-dos")).performClick()
            menuOf("Fenster putzen")
            item("Am Wochenende").performClick()

            compose.onNodeWithText("Auf Sa., 24. Okt. verschoben").assertIsDisplayed()
            assertEquals(Due.On(LocalDate.parse("2026-10-24")), due(id))
        }
    }

    private fun row(name: String) = compose.onNode(isToggleable() and hasText(name))

    private fun menuOf(name: String) = compose.onNodeWithContentDescription("Mehr zu „$name“").performClick()

    private fun item(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isPopup()))

    /** What the open menu offers, in its order. */
    private fun menuItems() = compose.onAllNodes(hasClickAction() and hasAnyAncestor(isPopup())).fetchSemanticsNodes()
        .map { node -> node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text } }

    private fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }

    private fun due(id: Long) = runBlocking { app.todos.get(id) }?.due

    private companion object {
        val FRIDAY: LocalDate = LocalDate.parse("2026-10-23")
    }
}
