package de.emilyshub

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** The to-dos on Heute (SPEC.md §1, §5): those due today among the routines by time, the overdue ones at the top. */
@RunWith(AndroidJUnit4::class)
class TodayTodosTest {

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
    fun `Heute shows the to-dos due today among the routines by time, the overdue ones at the very top`() {
        app.routine("Tabletten", "08:00")
        app.todo("Arzt", due = Due.At(today, LocalTime.of(9, 0)))
        app.todo("Müll", due = Due.On(today))
        app.todo("Steuer", due = Due.On(today.minusDays(2)))
        app.todo("Morgen", due = Due.On(today.plusDays(1)))
        app.todo("Irgendwann")

        launchApp().use {
            compose.onNodeWithText("To-do, überfällig: 21.10.2026").assertIsDisplayed()
            val shown = titles("Steuer", "Müll", "Tabletten", "Arzt", "Morgen", "Irgendwann")
            assertEquals(listOf("Steuer", "Müll", "Tabletten", "Arzt"), shown)
            compose.onNodeWithText("To-do, 09:00").assertIsDisplayed()
        }
    }

    @Test
    fun `a to-do ticked off on Heute is done and stays there ticked for the day, and unticking it opens it again`() {
        app.todo("Müll", due = Due.On(today))

        launchApp().use {
            row("Müll").performClick().assertIsOn()
            assertEquals(listOf("Müll"), runBlocking { app.todos.archived().first().map { it.title } })

            row("Müll").performClick().assertIsOff()
        }
        assertEquals(listOf("Müll"), runBlocking { app.todos.open().first().map { it.todo.title } })
    }

    @Test
    fun `a to-do's checklist shows on Heute, and ticking all of it off ticks the to-do off`() {
        app.todo("Umzug", due = Due.On(today), checklist = listOf("Kartons", "Wagen"))

        launchApp().use {
            row("Kartons").performClick().assertIsOn()
            row("Umzug").assertIsOff()

            row("Wagen").performClick()

            row("Umzug").assertIsOn()
        }
        assertEquals(listOf("Umzug"), runBlocking { app.todos.archived().first().map { it.title } })
    }

    private fun row(name: String) = compose.onNode(isToggleable() and hasText(name))

    /** Of [names], those on Heute, in the order shown. */
    private fun titles(vararg names: String): List<String> {
        val rows = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].map { it.text } }
        return rows.mapNotNull { texts -> names.firstOrNull { it in texts } }
    }
}
