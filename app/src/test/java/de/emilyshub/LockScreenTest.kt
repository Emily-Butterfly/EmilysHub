package de.emilyshub

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.AlarmActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class LockScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val authenticator = app.authenticator

    @Test
    fun `with a screen lock on the phone, EmilysHub starts locked and asks to unlock right away`() {
        authenticator.deviceSecure = true

        launchApp().use {
            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
            assertNoContent()
            assertEquals(1, authenticator.prompts)
        }
    }

    @Test
    fun `unlocking shows the app`() {
        authenticator.deviceSecure = true

        launchApp().use {
            compose.unlockWithFingerprint(authenticator)

            tab("Heute").assertIsSelected()
            compose.onNodeWithText("EmilysHub ist gesperrt").assertDoesNotExist()
        }
    }

    @Test
    fun `after a minute in the background the app is locked again before anything is shown`() {
        authenticator.deviceSecure = true

        launchApp().use { scenario ->
            compose.unlockWithFingerprint(authenticator)

            scenario.leaveAndReturn { ShadowSystemClock.advanceBy(Duration.ofMinutes(1)) }

            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
            assertNoContent()
            assertEquals(2, authenticator.prompts)
        }
    }

    @Test
    fun `a shorter break keeps the app open`() {
        authenticator.deviceSecure = true

        launchApp().use { scenario ->
            compose.unlockWithFingerprint(authenticator)

            scenario.leaveAndReturn { ShadowSystemClock.advanceBy(Duration.ofSeconds(59)) }

            tab("Heute").assertIsSelected()
            assertEquals(1, authenticator.prompts)
        }
    }

    @Test
    fun `a minute with the phone asleep counts as a minute in the background`() {
        authenticator.deviceSecure = true

        launchApp().use { scenario ->
            compose.unlockWithFingerprint(authenticator)

            scenario.leaveAndReturn { ShadowSystemClock.simulateDeepSleep(Duration.ofMinutes(1)) }

            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
        }
    }

    @Test
    fun `time with a full-screen alarm up counts as time in the background, since the alarm shows nothing of the app`() {
        authenticator.deviceSecure = true
        app.routine("Arzttermin", "07:30", fullScreen = true)
        val occurrence = runBlocking { app.routines.dueOn(app.routines.today()).first().single().occurrenceId }
        app.clock.now = Instant.parse("2026-10-23T05:30:00Z") // 07:30 in Berlin, when it is due

        launchApp().use { scenario ->
            compose.unlockWithFingerprint(authenticator)

            scenario.leaveAndReturn {
                ShadowSystemClock.advanceBy(Duration.ofSeconds(30))
                val alarm = Intent(app, AlarmActivity::class.java).putExtra(AlarmActivity.EXTRA_OCCURRENCE, occurrence)
                ActivityScenario.launch<AlarmActivity>(alarm).use { ShadowSystemClock.advanceBy(Duration.ofSeconds(30)) }
            }

            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
        }
    }

    @Test
    fun `after unlocking again, the app is where it was`() {
        authenticator.deviceSecure = true

        launchApp().use { scenario ->
            compose.unlockWithFingerprint(authenticator)
            tab("Notizen").performClick()

            scenario.leaveAndReturn { ShadowSystemClock.advanceBy(Duration.ofMinutes(5)) }
            compose.unlockWithFingerprint(authenticator)

            tab("Notizen").assertIsSelected()
        }
    }

    @Test
    fun `after the prompt was dismissed, the button asks again`() {
        authenticator.deviceSecure = true

        launchApp().use {
            compose.onNodeWithText("Entsperren").performClick()

            assertEquals(2, authenticator.prompts)
        }
    }

    @Test
    fun `with the app lock turned off, EmilysHub opens without asking`() {
        authenticator.deviceSecure = true
        app.settingsDao.values["app-lock"] = "off"

        launchApp().use {
            tab("Heute").assertIsSelected()
            assertEquals(0, authenticator.prompts)
        }
    }

    @Test
    fun `on a phone without a screen lock, EmilysHub opens without asking`() {
        authenticator.deviceSecure = false

        launchApp().use {
            tab("Heute").assertIsSelected()
            assertEquals(0, authenticator.prompts)
        }
    }

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun assertNoContent() {
        compose.onNodeWithText("Heute").assertDoesNotExist()
    }
}
