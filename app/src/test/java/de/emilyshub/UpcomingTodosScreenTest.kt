package de.emilyshub

import android.Manifest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.todos.Due
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate
import java.time.LocalTime

/**
 * "Demnächst" on Heute (SPEC.md §1, §5): to-dos some days before they are due, set in the editor, with when they are
 * due, to tick off there.
 */
@RunWith(AndroidJUnit4::class)
class UpcomingTodosScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val today = LocalDate.parse("2026-10-23")

    @Before
    fun allowReminders() {
        // Heute warns while the reminders lack something from Android, which is not what these tests are about.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.settingsDao.values["reminders-asked"] = "yes"
    }

    @Test
    fun `chosen in the editor, a to-do shows under "coming up" with when it is due`() {
        app.todo("Geschenk kaufen", due = Due.On(LocalDate.parse("2026-10-27")))

        launchApp().use {
            compose.onNodeWithText("Demnächst").assertDoesNotExist()
            compose.onNode(hasText("To-dos") and hasClickAction()).performClick()
            compose.onNodeWithText("Geschenk kaufen").performClick()
            option("Am Fälligkeitstag").performScrollTo()
            option("Schon einige Tage vorher").tap()
            compose.onNode(hasSetTextAction() and hasText("Tage vorher")).performScrollTo().performTextReplacement("4")
            compose.onNodeWithText("Speichern").performClick()
            compose.onNode(hasText("Heute") and hasClickAction()).performClick()

            compose.onNodeWithText("Demnächst").assertIsDisplayed()
            compose.onNodeWithText("To-do, fällig: Di, 27.10. · in 4 Tagen").assertIsDisplayed()
        }
        assertEquals(4, runBlocking { app.todos.open().first().single().todo.showsDaysBefore })
    }

    @Test
    fun `due tomorrow at a time, it says so, and ticked off there it stays ticked off`() {
        val tomorrow = Due.At(today.plusDays(1), LocalTime.of(9, 30))
        app.todo("Zahnarzt", due = tomorrow, checklist = listOf("Karte", "Überweisung"), showsDaysBefore = 2)

        launchApp().use {
            compose.onNodeWithText("To-do, fällig: morgen, 09:30").assertIsDisplayed()
            compose.onNode(isToggleable() and hasText("Karte")).performClick()
            compose.onNode(isToggleable() and hasText("Zahnarzt", substring = true)).performClick()

            compose.onNode(isToggleable() and hasText("Zahnarzt", substring = true)).assertIsOn()
        }
        assertEquals(listOf<String>(), runBlocking { app.todos.open().first() }.map { it.todo.title })
    }

    private fun option(label: String) = compose.onNode(isSelectable() and hasText(label))

    /** Scrolls the editor to the node first: its form is longer than the test screen. */
    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()
}
