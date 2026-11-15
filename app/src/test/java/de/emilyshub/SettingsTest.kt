package de.emilyshub

import android.app.UiModeManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.DefaultTones
import de.emilyshub.reminders.Tone
import de.emilyshub.reminders.ToneKind
import de.emilyshub.ui.theme.FliederColorScheme
import de.emilyshub.ui.theme.LilaColorScheme
import kotlinx.coroutines.runBlocking
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
    fun `the settings end with the version and commit of the build installed`() {
        launchApp().use {
            openSettings()

            compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME} · ${BuildConfig.COMMIT}").performScrollTo().assertIsDisplayed()
        }
    }

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

    @Test
    fun `looking around every 30 minutes for to-dos waiting for a place is on by default, and can be turned off`() {
        launchApp().use {
            openSettings()
            lookAroundSwitch().performScrollTo().assertIsOn()
            compose.onNodeWithText("auch an Orten ohne WLAN", substring = true).performScrollTo().assertIsDisplayed()

            lookAroundSwitch().performClick()

            lookAroundSwitch().assertIsOff()
            assertEquals("off", app.settingsDao.values["look-around"])
        }
    }

    @Test
    fun `the default tones are the phone's own at first, and each can be one of the phone's tones or none`() {
        val morning = app.tones.add("content://media/internal/audio/media/12", "Morgentau")

        launchApp().use { scenario ->
            openSettings()
            alarmTone().performScrollTo().assertTextContains("Ton des Handys")
            notificationTone().performScrollTo().assertTextContains("Ton des Handys")

            alarmTone().performClick()
            val alarm = scenario.pickTone(Uri.parse(morning.uri))
            assertEquals(RingtoneManager.TYPE_ALARM, alarm.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, 0))
            assertEquals(true, alarm.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false))
            assertEquals(true, alarm.getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false))
            alarmTone().assertTextContains("Morgentau")

            notificationTone().performScrollTo().performClick()
            val notification = scenario.pickTone(picked = null)
            assertEquals(RingtoneManager.TYPE_NOTIFICATION, notification.getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, 0))
            notificationTone().assertTextContains("Lautlos")
            assertEquals(DefaultTones(alarm = morning, notification = Tone.Silent), app.settings.current?.tones)

            alarmTone().performClick()
            scenario.pickTone(Settings.System.DEFAULT_ALARM_ALERT_URI)
            alarmTone().assertTextContains("Ton des Handys")
        }
        assertEquals(Tone.Standard, runBlocking { app.settings.defaultTones() }.alarm)
    }

    @Test
    fun `a default tone can be an audio file of one's own, copied as a tone of its kind`() {
        app.toneFiles.add("content://downloads/1", "Gong.ogg")

        launchApp().use { scenario ->
            openSettings()
            compose.onAllNodesWithText("Eigene Datei…")[1].performScrollTo().performClick()
            scenario.pickFile(Uri.parse("content://downloads/1"))
            notificationTone().assertTextContains("Gong")
        }
        val copy = app.toneFiles.copies.single()
        assertEquals(ToneKind.Notification, copy.kind)
        assertEquals(Tone.Of(copy.uri), runBlocking { app.settings.defaultTones() }.notification)
    }

    private fun openSettings() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
    }

    private fun design(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun applicationNightMode(): Int {
        compose.waitForIdle()
        return shadowOf(app.getSystemService(UiModeManager::class.java)).applicationNightMode
    }

    private fun alarmTone() = compose.onNode(hasClickAction() and hasText("Ton für Vollbild-Alarme"))

    private fun notificationTone() = compose.onNode(hasClickAction() and hasText("Ton für Benachrichtigungen"))

    private fun appLockSwitch() = compose.onNode(isToggleable() and hasText("App-Sperre"))

    private fun lookAroundSwitch() = compose.onNode(isToggleable() and hasText("Alle 30 Minuten nachsehen"))

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
