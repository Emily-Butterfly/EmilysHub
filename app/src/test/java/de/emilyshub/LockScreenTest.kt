package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class LockScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val authenticator = app.authenticator

    @Test
    fun `with a screen lock on the phone, EmilysHub starts locked and asks to unlock right away`() {
        authenticator.deviceSecure = true

        launch().use {
            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
            assertNoContent()
            assertEquals(1, authenticator.prompts)
        }
    }

    @Test
    fun `unlocking shows the app`() {
        authenticator.deviceSecure = true

        launch().use {
            unlockWithFingerprint()

            tab("Heute").assertIsSelected()
            compose.onNodeWithText("EmilysHub ist gesperrt").assertDoesNotExist()
        }
    }

    @Test
    fun `after a minute in the background the app is locked again before anything is shown`() {
        authenticator.deviceSecure = true

        launch().use { scenario ->
            unlockWithFingerprint()

            scenario.moveToState(Lifecycle.State.CREATED)
            ShadowSystemClock.advanceBy(Duration.ofMinutes(1))
            scenario.moveToState(Lifecycle.State.RESUMED)

            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
            assertNoContent()
            assertEquals(2, authenticator.prompts)
        }
    }

    @Test
    fun `a shorter break keeps the app open`() {
        authenticator.deviceSecure = true

        launch().use { scenario ->
            unlockWithFingerprint()

            scenario.moveToState(Lifecycle.State.CREATED)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(59))
            scenario.moveToState(Lifecycle.State.RESUMED)

            tab("Heute").assertIsSelected()
            assertEquals(1, authenticator.prompts)
        }
    }

    @Test
    fun `a minute with the phone asleep counts as a minute in the background`() {
        authenticator.deviceSecure = true

        launch().use { scenario ->
            unlockWithFingerprint()

            scenario.moveToState(Lifecycle.State.CREATED)
            ShadowSystemClock.simulateDeepSleep(Duration.ofMinutes(1))
            scenario.moveToState(Lifecycle.State.RESUMED)

            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
        }
    }

    @Test
    fun `after unlocking again, the app is where it was`() {
        authenticator.deviceSecure = true

        launch().use { scenario ->
            unlockWithFingerprint()
            tab("Notizen").performClick()

            scenario.moveToState(Lifecycle.State.CREATED)
            ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
            scenario.moveToState(Lifecycle.State.RESUMED)
            unlockWithFingerprint()

            tab("Notizen").assertIsSelected()
        }
    }

    @Test
    fun `after the prompt was dismissed, the button asks again`() {
        authenticator.deviceSecure = true

        launch().use {
            compose.onNodeWithText("Entsperren").performClick()

            assertEquals(2, authenticator.prompts)
        }
    }

    @Test
    fun `with the app lock turned off, EmilysHub opens without asking`() {
        authenticator.deviceSecure = true
        app.settingsDao.values["app-sperre"] = "aus"

        launch().use {
            tab("Heute").assertIsSelected()
            assertEquals(0, authenticator.prompts)
        }
    }

    @Test
    fun `on a phone without a screen lock, EmilysHub opens without asking`() {
        authenticator.deviceSecure = false

        launch().use {
            tab("Heute").assertIsSelected()
            assertEquals(0, authenticator.prompts)
        }
    }

    private fun launch() = ActivityScenario.launch(MainActivity::class.java)

    /** Once the prompt is up, the user puts a finger on the sensor and the app shows. */
    private fun unlockWithFingerprint() {
        compose.waitForIdle()
        authenticator.succeed()
        compose.waitForIdle()
    }

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun assertNoContent() {
        compose.onNodeWithText("Heute").assertDoesNotExist()
    }
}
