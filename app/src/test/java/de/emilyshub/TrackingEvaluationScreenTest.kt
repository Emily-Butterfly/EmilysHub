package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.tracking.EpisodeKind
import de.emilyshub.tracking.HintRule
import de.emilyshub.tracking.Medication
import de.emilyshub.tracking.MorningEntry
import de.emilyshub.tracking.TrackingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * The week evaluation, the history and the hints of Tracking (SPEC.md §17): changing the week, the history over 4 and 12
 * weeks, a hint opened with its source and marked done. Made-up values; the test clock stands at Friday, 23 October
 * 2026, 07:00 in Berlin.
 */
@RunWith(AndroidJUnit4::class)
class TrackingEvaluationScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val today = LocalDate.parse("2026-10-23")

    @After
    fun closeDatabase() {
        app.tracking.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
    }

    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    @Test
    fun `the week evaluation, to the week before and back`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            runBlocking { app.trackingModules.entries.saveMorning(MorningEntry(date = today, restingPulse = 104, recordedOn = today), replacing = null) }

            compose.onNode(hasText("Wochenauswertung") and hasClickAction()).tap()
            compose.onNodeWithText("19.10.2026 – 25.10.2026").assertIsDisplayed()
            compose.onNodeWithText("Maximum (/min): 104").assertIsDisplayed()
            compose.onNodeWithText("Tage über 100/min: 1").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Mittel (/min): 104").assertIsDisplayed()
            compose.onAllNodesWithText("aus 1 Eintrag").onFirst().assertIsDisplayed()
            compose.onNodeWithContentDescription("Woche danach").assertIsNotEnabled()

            compose.onNodeWithContentDescription("Woche davor").tap()
            compose.waitForIdle()
            compose.onNodeWithText("12.10.2026 – 18.10.2026").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Maximum (/min): –").performScrollTo().assertIsDisplayed()

            compose.onNodeWithContentDescription("Woche danach").tap()
            compose.waitForIdle()
            compose.onNodeWithText("Maximum (/min): 104").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `the history over 4 and 12 weeks, with a medication's start and a running episode`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            runBlocking {
                app.trackingModules.entries.saveMorning(MorningEntry(date = today, restingPulse = 72, recordedOn = today), replacing = null)
                app.trackingModules.medications.save(Medication(name = "Medikament A", times = listOf(LocalTime.of(8, 0)), start = today.minusDays(3)))
                app.trackingModules.episodes.create(EpisodeKind.Pem, today.minusDays(1))
            }

            compose.onNode(hasText("Verlauf") and hasClickAction()).tap()
            compose.onNode(hasText("4 Wochen") and hasClickAction()).assertIsSelected()
            compose.onNodeWithText("Ruhepuls am Morgen (/min)").performScrollTo().assertIsDisplayed()
            compose.onNode(hasContentDescription("Diagramm Ruhepuls am Morgen (/min): 23.10.2026 72")).assertExists()
            compose.onAllNodesWithText("28.09.2026").onFirst().assertExists()
            compose.onNodeWithText("20.10.2026: Medikament A Beginn").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("seit 22.10.2026: PEM, läuft noch").performScrollTo().assertIsDisplayed()

            compose.onNode(hasText("12 Wochen") and hasClickAction()).tap()
            compose.waitForIdle()
            compose.onNode(hasText("12 Wochen") and hasClickAction()).assertIsSelected()
            compose.onAllNodesWithText("03.08.2026").onFirst().assertExists()
            compose.onAllNodesWithText("28.09.2026").fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
        }
    }

    @Test
    fun `a hint opened, its source shown in full, then marked done`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()

            compose.onNodeWithText("Noch zu wenig Daten").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Stehtest: noch 3 Stehtests").performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("Basisuntersuchungen") and hasClickAction()).tap()
            compose.onNodeWithText("Frage: Sind ein EKG mit QTc-Zeit und die Elektrolyte schon bestimmt?").assertIsDisplayed()
            compose.onNodeWithText("Ein Hinweis für das Arztgespräch, keine Diagnose.").assertIsDisplayed()

            compose.onNodeWithContentDescription("Quelle HRS-2015 anzeigen").tap()
            compose.onNodeWithText("Heart Rhythm Society", substring = true).assertIsDisplayed()
            compose.onNodeWithText("OK").performClick()

            compose.onNodeWithText("Erledigt").tap()
            compose.waitForIdle()
            compose.onNodeWithText("Gerade kein Hinweis.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Basisuntersuchungen").assertDoesNotExist()
            assertTrue(checkNotNull(runBlocking { app.trackingModules.evaluation.hints() }).shown.none { it.rule == HintRule.Basics })
        }
    }
}
