package de.emilyshub

import android.app.UiModeManager
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.ui.theme.FliederColorScheme
import de.emilyshub.ui.theme.LilaColorScheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
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
        launchApp().use {
            openSettings()
            design("System").assertIsSelected()

            design("Dunkel").performClick()

            design("Dunkel").assertIsSelected()
            assertEquals("dark", app.settingsDao.values["design"])
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the chosen design switches between Flieder and Lila right away`() {
        launchApp().use {
            openSettings()

            design("Dunkel").performClick()
            assertEquals(LILA_BACKGROUND, backgroundColor())

            design("Hell").performClick()
            assertEquals(FLIEDER_BACKGROUND, backgroundColor())
        }
    }

    @Test
    fun `Android draws its window background and starting window in the chosen design too`() {
        app.settingsDao.values["design"] = "dark"

        launchApp().use {
            assertEquals(UiModeManager.MODE_NIGHT_YES, applicationNightMode())

            openSettings()
            design("Hell").performClick()
            assertEquals(UiModeManager.MODE_NIGHT_NO, applicationNightMode())

            design("System").performClick()
            assertEquals(UiModeManager.MODE_NIGHT_AUTO, applicationNightMode())
        }
    }

    @Test
    fun `the app lock is on by default, and turning it off is saved and takes effect right away`() {
        app.authenticator.deviceSecure = true

        launchApp().use { scenario ->
            compose.unlockWithFingerprint(app.authenticator)
            openSettings()
            appLockSwitch().assertIsOn()

            appLockSwitch().performClick()

            appLockSwitch().assertIsOff()
            assertEquals("off", app.settingsDao.values["app-lock"])

            scenario.leaveAndReturn { ShadowSystemClock.advanceBy(Duration.ofMinutes(5)) }
            compose.onNodeWithText("EmilysHub ist gesperrt").assertDoesNotExist()

            appLockSwitch().performClick()
            appLockSwitch().assertIsOn()
            assertEquals("on", app.settingsDao.values["app-lock"])
        }
    }

    @Test
    fun `without a screen lock on the phone, the app lock shows a hint instead of turning on`() {
        app.authenticator.deviceSecure = false

        launchApp().use {
            openSettings()

            appLockSwitch().assertIsOff().assertIsNotEnabled()
            compose.onNodeWithText("Displaysperre", substring = true).assertIsDisplayed()
        }
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
    }

    private fun design(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun applicationNightMode(): Int {
        compose.waitForIdle()
        return shadowOf(app.getSystemService(UiModeManager::class.java)).applicationNightMode
    }

    private fun appLockSwitch() = compose.onNode(isToggleable() and hasText("App-Sperre"))

    /**
     * The top left corner: the top bar, in the background's color while nothing is scrolled, and
     * away from the ripple of the tapped choice.
     */
    private fun backgroundColor(): Color = compose.onRoot().captureToImage().toPixelMap()[0, 0]

    private companion object {
        val FLIEDER_BACKGROUND = FliederColorScheme.background
        val LILA_BACKGROUND = LilaColorScheme.background
    }
}
