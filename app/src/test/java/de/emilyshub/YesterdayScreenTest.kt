package de.emilyshub

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
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
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.todos.Due
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate

/** Catching up on yesterday from Heute (SPEC.md §1, Gestern nachtragen), on Friday, 23 October 2026. */
@RunWith(AndroidJUnit4::class)
class YesterdayScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun allowReminders() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `yesterday opens from the line above Heute, plainly marked, and leads back to Heute`() {
        launchApp().use {
            compose.onNodeWithText("‹ Gestern").performClick()

            compose.onNode(isHeading() and hasText("Gestern, Do. 22. Okt. – nachtragen")).assertIsDisplayed()
            // Only one day back.
            compose.onNodeWithText("‹ Gestern").assertDoesNotExist()
            compose.onNodeWithText("Gestern stand nichts an.").assertIsDisplayed()

            compose.onNodeWithText("Heute ›").performClick()
            compose.onNodeWithText("‹ Gestern").assertIsDisplayed()
            compose.onNodeWithText("Gestern, ", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `back from yesterday leads to Heute`() {
        launchApp().use {
            compose.onNodeWithText("‹ Gestern").performClick()

            compose.waitForIdle()
            it.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }

            compose.onNodeWithText("‹ Gestern").assertIsDisplayed()
            tab("Heute").assertIsDisplayed()
        }
    }

    @Test
    fun `a routine missed yesterday is ticked off there, done that day, and the flame of today follows`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-21" to Done, "2026-10-22" to Missed)

        launchApp().use {
            compose.onNodeWithContentDescription("Streak", substring = true).assertDoesNotExist()
            compose.onNodeWithText("‹ Gestern").performClick()
            compose.onNodeWithText("Verpasst").assertIsDisplayed()
            // Neither skipped nor dropped on the day before.
            compose.onNodeWithText("Überspringen").assertDoesNotExist()
            compose.onNodeWithContentDescription("Mehr zu „Vitamine“").assertDoesNotExist()

            compose.onNode(isToggleable() and hasText("Vitamine")).assertIsOff().performClick()

            compose.onNode(isToggleable() and hasText("Vitamine")).assertIsOn()
            compose.onNodeWithContentDescription("Streak 2 Tage, heute noch offen").assertIsDisplayed()
        }
        assertEquals(listOf(Done), statusesOn("2026-10-22"))
    }

    @Test
    fun `a to-do overdue yesterday is ticked off there, done that day, and leaves Heute`() {
        val rubbish = app.todo("Müll", due = Due.On(LocalDate.parse("2026-10-22")))

        launchApp().use {
            compose.onNodeWithText("Überfällig (1)").assertIsDisplayed()
            compose.onNodeWithText("‹ Gestern").performClick()
            compose.onNode(isToggleable() and hasText("Müll")).performClick()
            compose.onNode(isToggleable() and hasText("Müll")).assertIsOn()

            compose.onNodeWithText("Heute ›").performClick()
            compose.onNodeWithText("Müll").assertDoesNotExist()
        }
        assertNotNull(runBlocking { app.todos.get(rubbish) }?.doneAt)
    }

    @Test
    fun `one skipped yesterday stays as it is`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-22" to OccurrenceStatus.Skipped)

        launchApp().use {
            compose.onNodeWithText("‹ Gestern").performClick()

            compose.onNodeWithText("Übersprungen").assertIsDisplayed()
            compose.onNode(isToggleable() and hasText("Vitamine")).assertIsNotEnabled()
            compose.onNodeWithText("Rückgängig").assertDoesNotExist()
        }
    }

    @Test
    fun `another tab, and back to Heute, shows today again`() {
        launchApp().use {
            compose.onNodeWithText("‹ Gestern").performClick()

            tab("Routinen").performClick()
            tab("Heute").performClick()

            compose.onNodeWithText("‹ Gestern").assertIsDisplayed()
        }
    }

    @Test
    fun `after the lock Heute shows today again`() {
        app.authenticator.deviceSecure = true

        launchApp().use { scenario ->
            compose.unlockWithFingerprint(app.authenticator)
            compose.onNodeWithText("‹ Gestern").performClick()

            scenario.leaveAndReturn { ShadowSystemClock.advanceBy(Duration.ofMinutes(5)) }
            compose.unlockWithFingerprint(app.authenticator)

            compose.onNodeWithText("‹ Gestern").assertIsDisplayed()
        }
    }

    private fun statusesOn(date: String) = runBlocking { app.routines.dueOn(LocalDate.parse(date)).first().map { it.status } }

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))
}
