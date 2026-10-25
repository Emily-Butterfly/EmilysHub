package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.OccurrenceStatus.Done
import de.emilyshub.routines.OccurrenceStatus.Missed
import de.emilyshub.routines.OccurrenceStatus.Skipped
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The statistics of the routines (SPEC.md §8), from the Routinen tab: streaks and done rates over all routines and of
 * each, and per routine a calendar of its due days.
 */
@RunWith(AndroidJUnit4::class)
class StatisticsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `the statistics show the streaks and done rates over all routines, and of each`() {
        twoRoutines()

        launchApp().use {
            openStatistics()

            compose.onNodeWithText("Alle Routinen").assertIsDisplayed()
            compose.onNodeWithText("Aktueller Streak: 1 Tag").assertIsDisplayed()
            compose.onNodeWithText("Längster Streak: 2 Tage").assertIsDisplayed()
            compose.onNodeWithText("7 Tage: 80 %").assertIsDisplayed()
            compose.onNodeWithText("Streak: 1 Tag · 30 Tage: 75 %").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Streak: 1 Tag · 30 Tage: 100 %").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `a routine's statistics show its streaks, done rates, and a calendar of how its due days ended`() {
        twoRoutines()

        launchApp().use {
            openStatistics()
            compose.onNode(hasText("Vitamine") and hasClickAction()).performScrollTo().performClick()

            compose.onNodeWithText("Längster Streak: 2 Tage").assertIsDisplayed()
            compose.onNodeWithText("90 Tage: 75 %").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Oktober 2026").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("20. Oktober: erledigt").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("21. Oktober: verpasst").performScrollTo().assertIsDisplayed()

            compose.onNodeWithContentDescription("Vorheriger Monat").performScrollTo().performClick()
            compose.onNodeWithText("September 2026").assertIsDisplayed()
            compose.onNodeWithContentDescription("Nächster Monat").performClick()
            compose.onNodeWithText("Oktober 2026").assertIsDisplayed()
        }
    }

    @Test
    fun `a skipped day shows as such in the calendar, and an archived routine keeps its statistics`() {
        val reading = app.routine("Lesen", "21:00")
        app.history(reading, "2026-10-21" to Skipped, "2026-10-22" to Done)
        runBlocking { app.routines.archive(reading) }

        launchApp().use {
            openStatistics()
            compose.onNode(hasText("Lesen") and hasClickAction()).performScrollTo().performClick()

            compose.onNodeWithContentDescription("21. Oktober: übersprungen").performScrollTo().assertIsDisplayed()
        }
    }

    /**
     * Vitamine: done on the 19th and 20th, missed on the 21st, done on the 22nd. Yoga: skipped on the 21st, done on the
     * 22nd. Both are still open today, the 23rd.
     */
    private fun twoRoutines() {
        val vitamins = app.routine("Vitamine", "07:30")
        val yoga = app.routine("Yoga", "18:00")
        app.history(vitamins, "2026-10-19" to Done, "2026-10-20" to Done, "2026-10-21" to Missed, "2026-10-22" to Done)
        app.history(yoga, "2026-10-21" to Skipped, "2026-10-22" to Done)
    }

    private fun openStatistics() {
        compose.onNode(hasText("Routinen") and hasClickAction()).performClick()
        compose.onNodeWithText("Statistik").performScrollTo().performClick()
    }
}
