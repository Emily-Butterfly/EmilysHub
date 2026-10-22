package de.emilyshub

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class SettingsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `the design follows the system until another one is chosen, and the choice is saved`() {
        launch().use {
            openSettings()
            design("System").assertIsSelected()

            design("Dunkel").performClick()

            design("Dunkel").assertIsSelected()
            assertEquals("dunkel", app.settingsDao.values["design"])
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the chosen design switches between Flieder and Lila right away`() {
        launch().use {
            openSettings()

            design("Dunkel").performClick()
            assertEquals(LILA_BACKGROUND, backgroundColor())

            design("Hell").performClick()
            assertEquals(FLIEDER_BACKGROUND, backgroundColor())
        }
    }

    @Test
    fun `the app lock is on by default, and turning it off is saved and takes effect right away`() {
        app.authenticator.deviceSecure = true

        launch().use { scenario ->
            compose.waitForIdle()
            app.authenticator.succeed()
            openSettings()
            appLockSwitch().assertIsOn()

            appLockSwitch().performClick()

            appLockSwitch().assertIsOff()
            assertEquals("aus", app.settingsDao.values["app-sperre"])

            scenario.moveToState(Lifecycle.State.CREATED)
            ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("EmilysHub ist gesperrt").assertDoesNotExist()

            appLockSwitch().performClick()
            appLockSwitch().assertIsOn()
            assertEquals("an", app.settingsDao.values["app-sperre"])
        }
    }

    @Test
    fun `without a screen lock on the phone, the app lock shows a hint instead of turning on`() {
        app.authenticator.deviceSecure = false

        launch().use {
            openSettings()

            appLockSwitch().assertIsOff().assertIsNotEnabled()
            compose.onNodeWithText("Displaysperre", substring = true).assertIsDisplayed()
        }
    }

    private fun launch() = ActivityScenario.launch(MainActivity::class.java)

    private fun openSettings() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
    }

    private fun design(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun appLockSwitch() = compose.onNode(isToggleable() and hasText("App-Sperre"))

    /**
     * The top left corner: the top bar, in the background's color while nothing is scrolled, and
     * away from the ripple of the tapped choice.
     */
    private fun backgroundColor(): Color = compose.onRoot().captureToImage().toPixelMap()[0, 0]

    private companion object {
        val FLIEDER_BACKGROUND = Color(0xFFFEF7FF)
        val LILA_BACKGROUND = Color(0xFF17111F)
    }
}
