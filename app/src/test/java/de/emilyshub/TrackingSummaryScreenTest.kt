package de.emilyshub

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.island.IslandState
import de.emilyshub.tracking.TrackingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The doctor's summary in Tracking's menu (SPEC.md §17, "Arzt-Zusammenfassung"): its weeks up to those tracked, a
 * preview, one's own questions kept, and before saving the warning that the PDF is not encrypted; then the folder picker,
 * Tracking staying open for that trip. The PDF itself is drawn on a device only (SummaryAndPhotosOnDeviceTest).
 */
@RunWith(AndroidJUnit4::class)
class TrackingSummaryScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @After
    fun closeDatabase() {
        app.tracking.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
    }

    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    @Test
    fun `the summary of the weeks tracked is previewed, warns before saving and asks for a folder`() {
        launchApp().use { scenario ->
            compose.openTracking()
            compose.setUpTracking()
            compose.onNodeWithText("+ Anstrengung").tap()
            compose.onNode(hasContentDescription("Stärke: mittel")).tap()
            compose.done()

            compose.onNodeWithContentDescription("Menü von Tracking").performClick()
            compose.onNodeWithText("Arzt-Zusammenfassung").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("1 Woche").assertIsDisplayed()
            compose.onNodeWithContentDescription("Eine Woche mehr").assertIsNotEnabled()
            compose.onNodeWithText("Stärkste Symptome").performScrollTo().assertIsDisplayed()
            compose.field("Eigene Fragen").performScrollTo().performTextInput("- Kipptisch?")
            compose.onNodeWithText("• Kipptisch?").performScrollTo().assertIsDisplayed()

            compose.onNodeWithText("Als PDF speichern").tap()
            compose.onNodeWithText("Die PDF ist nicht verschlüsselt").assertIsDisplayed()
            compose.onNodeWithText("Ordner wählen").performClick()
            compose.waitForIdle()

            var request: Intent? = null
            scenario.onActivity { request = shadowOf(it).nextStartedActivityForResult.intent }
            val asked = checkNotNull(request)
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, asked.action)
            assertEquals("application/pdf", asked.type)
            assertTrue(checkNotNull(asked.getStringExtra(Intent.EXTRA_TITLE)).startsWith("Arzt-Zusammenfassung-"))
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue("Tracking stays open while the folder picker is in front", app.tracking.state is IslandState.Open)
            // The result comes before the activity is in front again, as on Android.
            scenario.onActivity { shadowOf(it).receiveResult(asked, Activity.RESULT_CANCELED, null) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()

            app.tracking.lock()
            runBlocking { app.tracking.unlockWithPassword(TRACKING_PASSWORD) }
            assertEquals("one's own questions are kept", "- Kipptisch?", runBlocking { app.trackingModules.evaluation.questions() })
        }
    }

    @Test
    fun `back in front without the folder picker's answer, as from the launcher, Tracking is locked`() {
        launchApp().use { scenario ->
            compose.openTracking()
            compose.setUpTracking()
            compose.onNodeWithText("+ Anstrengung").tap()
            compose.onNode(hasContentDescription("Stärke: mittel")).tap()
            compose.done()
            compose.onNodeWithContentDescription("Menü von Tracking").performClick()
            compose.onNodeWithText("Arzt-Zusammenfassung").performClick()
            compose.onNodeWithText("Als PDF speichern").tap()
            compose.onNodeWithText("Ordner wählen").performClick()
            compose.waitForIdle()

            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(app.tracking.state is IslandState.Open)
            scenario.moveToState(Lifecycle.State.RESUMED)

            assertTrue(app.tracking.state is IslandState.Locked)
        }
    }
}
