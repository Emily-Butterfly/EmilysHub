package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.tracking.EventKind
import de.emilyshub.tracking.SkinArea
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingDatabase
import de.emilyshub.tracking.TrackingEvent
import de.emilyshub.tracking.WaterTemperature
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/** The events of Tracking (SPEC.md §17, "Ereignisse"): recorded with the pulse timer, added to later, deleted. */
@RunWith(AndroidJUnit4::class)
class TrackingEventScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @After
    fun closeDatabase() {
        app.tracking.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
    }

    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    private fun pass(duration: Duration) {
        app.clock.now = app.clock.now.plus(duration)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun events(): List<TrackingEvent> = checkNotNull(runBlocking { app.tracking.withDatabase { it.events().all() } })

    private fun newShower() {
        compose.openTracking()
        compose.setUpTracking()
        compose.onNodeWithText("+ Ereignis").tap()
        compose.onNodeWithText("Duschen").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `an event is recorded with the pulse timer, and kept on leaving`() {
        launchApp().use {
            newShower()
            compose.onNodeWithText("heiß").tap()
            compose.field("Puls vorher").performScrollTo().performTextInput("78")

            compose.onNodeWithText("Vorbei – Timer starten").tap()
            compose.onNodeWithText("Jetzt: Puls direkt danach").performScrollTo().assertIsDisplayed()
            assertEquals("while the timer runs, the five minutes rest", Tracking.IDLE_MILLIS, app.tracking.millisUntilIdle())
            compose.field("Puls direkt danach").performScrollTo().performTextInput("110")
            compose.onNodeWithText("Puls nach 5 Minuten in 5:00").performScrollTo().assertIsDisplayed()
            pass(Duration.ofMinutes(5))
            compose.onNodeWithText("Jetzt: Puls nach 5 Minuten").performScrollTo().assertIsDisplayed()
            compose.field("Puls nach 5 Minuten").performScrollTo().performTextInput("95")
            compose.done()

            val event = events().single()
            assertEquals(EventKind.Shower, event.kind)
            assertEquals(WaterTemperature.Hot, event.water)
            assertEquals(78, event.pulseBefore)
            assertEquals(110, event.pulseAfter)
            assertEquals(95, event.pulseFive)
            assertTrue(event.endedAt != null)
        }
    }

    @Test
    fun `fields are added later, and an event is deleted after asking`() {
        launchApp().use {
            newShower()
            compose.done()

            compose.onNodeWithText("Ereignisse").tap()
            compose.onNodeWithText("Duschen", substring = true).performClick()
            compose.onNodeWithText("Beine").tap()
            compose.field("Minuten bis zur Rückbildung").performScrollTo().performTextInput("40")
            compose.done()
            assertEquals(setOf(SkinArea.Legs), events().single().skinAreas)
            assertEquals(40, events().single().skinFadeMinutes)

            compose.onNodeWithText("Duschen", substring = true).performClick()
            compose.onNodeWithText("Ereignis löschen").tap()
            compose.onNodeWithText("Ereignis löschen?").assertIsDisplayed()
            compose.onNodeWithText("Löschen").performClick()
            compose.waitForIdle()

            assertEquals(emptyList<TrackingEvent>(), events())
            compose.onNodeWithText("Noch keine Ereignisse.").assertIsDisplayed()
        }
    }
}
