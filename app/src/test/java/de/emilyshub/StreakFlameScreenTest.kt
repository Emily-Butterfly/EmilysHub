package de.emilyshub

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Missed
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** The streak's flame atop Heute (SPEC.md §1): the overall streak of the statistics, grey while today is open. */
@RunWith(AndroidJUnit4::class)
class StreakFlameScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun allowReminders() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp") // the Galaxy A52, where it all fits below the line for thoughts
    fun `the flame shows the streak, grey while today is open, and follows what is done and undone`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-21" to Done, "2026-10-22" to Done)

        launchApp().use {
            compose.onNodeWithContentDescription("Streak 2 Tage, heute noch offen").assertIsDisplayed()

            compose.onNode(isToggleable() and hasText("Vitamine")).performClick()
            compose.onNodeWithContentDescription("Streak 3 Tage").assertIsDisplayed()

            compose.openPast()
            compose.onNode(isToggleable() and hasText("Vitamine")).performClick()
            compose.onNodeWithContentDescription("Streak 2 Tage, heute noch offen").assertIsDisplayed()
        }
    }

    @Test
    fun `tapping the flame opens the statistics, with the same streak`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-22" to Done)

        launchApp().use {
            compose.onNodeWithContentDescription("Streak 1 Tag, heute noch offen").performClick()

            compose.onNodeWithText("Aktueller Streak: 1 Tag").assertIsDisplayed()
        }
    }

    @Test
    fun `without a streak the flame stands at 0, grey, naming the last one, and only Heute has one`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-21" to Done, "2026-10-22" to Missed)

        launchApp().use {
            compose.onNodeWithContentDescription("Kein Streak. Letzter: 1 Tag").assertIsDisplayed()

            compose.onNode(isToggleable() and hasText("Vitamine")).performClick()
            compose.onNodeWithContentDescription("Streak 1 Tag").assertIsDisplayed()

            compose.onNode(hasText("Routinen") and hasClickAction()).performClick()
            compose.onNode(hasContentDescription("Streak", substring = true)).assertDoesNotExist()
        }
    }

    @Test
    fun `with something due but never a streak, the flame says only that there is none`() {
        app.routine("Vitamine", "07:30")

        launchApp().use {
            compose.onNodeWithContentDescription("Kein Streak").assertIsDisplayed()
        }
    }

    @Test
    fun `without any routine there is no flame`() {
        launchApp().use {
            compose.onNode(hasContentDescription("Streak", substring = true)).assertDoesNotExist()
        }
    }

    @Test
    fun `tapped at 0, the flame opens the statistics, which name the last streak, overall and for the routine`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-20" to Done, "2026-10-21" to Done, "2026-10-22" to Missed)

        launchApp().use {
            compose.onNodeWithContentDescription("Kein Streak. Letzter: 2 Tage").performClick()

            compose.onNodeWithText("Aktueller Streak: 0 Tage").assertIsDisplayed()
            compose.onNodeWithText("Letzter Streak: 2 Tage, bis Mi., 21. Okt.").assertIsDisplayed()

            compose.onNodeWithText("Vitamine").performClick()
            compose.onNodeWithText("Letzter Streak: 2 Tage, bis Mi., 21. Okt.").assertIsDisplayed()
        }
    }

    @Test
    fun `with a streak running, the statistics name no last one`() {
        val vitamins = app.routine("Vitamine", "07:30")
        app.history(vitamins, "2026-10-20" to Done, "2026-10-21" to Missed, "2026-10-22" to Done)

        launchApp().use {
            compose.onNodeWithContentDescription("Streak 1 Tag, heute noch offen").performClick()

            compose.onNodeWithText("Letzter Streak", substring = true).assertDoesNotExist()
        }
    }
}
