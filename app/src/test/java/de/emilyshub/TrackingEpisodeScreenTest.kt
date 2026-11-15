package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.tracking.Certainty
import de.emilyshub.tracking.EpisodeKind
import de.emilyshub.tracking.Exertion
import de.emilyshub.tracking.ExertionKind
import de.emilyshub.tracking.FollowUpAnswer
import de.emilyshub.tracking.TrackingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * Episodes of PEM and crash (SPEC.md §17): begun from the evening entry, a trigger chosen, ended from the start page;
 * and the evening asking after a strong exertion. The test clock stands at Friday, 23 October 2026, 07:00 in Berlin.
 */
@RunWith(AndroidJUnit4::class)
class TrackingEpisodeScreenTest {

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
    fun `an episode from the evening entry, with a trigger chosen, ended from the start page`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            val exertion = checkNotNull(
                runBlocking {
                    app.trackingModules.exertions.save(Exertion(date = today.minusDays(1), time = LocalTime.of(18, 0), kind = ExertionKind.Physical, strength = 3))
                },
            )

            compose.onNode(hasText("Abendeintrag") and hasClickAction()).tap()
            compose.onNodeWithText("Schlechter als vor der Anstrengung am 22.10.2026 (körperlich, Stärke 3)?").performScrollTo().assertIsDisplayed()
            compose.onNode(hasContentDescription("Schlechter als vor der Anstrengung am 22.10.2026 (körperlich, Stärke 3)?: ja")).tap()
            compose.onNodeWithText("Crash oder PEM?").tap()
            compose.onNodeWithText("PEM").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("22.10.2026 · körperlich · Stärke 3").tap()
            compose.onNodeWithText("Beginn etwa 18 Stunden nach der Anstrengung").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("wahrscheinlich").tap()
            compose.done()
            compose.done()

            val episode = checkNotNull(runBlocking { app.trackingModules.episodes.running() }).single()
            assertEquals(EpisodeKind.Pem, episode.kind)
            assertEquals(today, episode.startDate)
            assertEquals(mapOf(exertion to Certainty.Likely), checkNotNull(runBlocking { app.trackingModules.episodes.sheet(episode.id) }).triggers)
            assertEquals(mapOf(exertion to FollowUpAnswer.Yes), checkNotNull(runBlocking { app.trackingModules.entries.evening(today) }).followUpAnswers)

            compose.onNodeWithText("PEM seit 23.10.2026 läuft noch").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("beenden").tap()
            compose.waitForIdle()

            assertEquals(today, checkNotNull(runBlocking { app.trackingModules.episodes.sheet(episode.id) }).episode.endDate)
        }
    }
}
