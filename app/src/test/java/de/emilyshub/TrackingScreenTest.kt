package de.emilyshub

import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.island.FingerprintPurpose
import de.emilyshub.island.IslandState
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingDatabase
import de.emilyshub.tracking.TrackingSetting
import de.emilyshub.ui.island.WhileMeasuring
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Tracking under Sonstiges (SPEC.md §17): a password of its own set on first opening, the fingerprint as a shortcut, its
 * lock as the Doku's, and on its start page first of all "Akute Beschwerden?".
 */
@RunWith(AndroidJUnit4::class)
class TrackingScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val fingerprint = app.trackingFingerprint

    @After
    fun closeDatabases() {
        app.tracking.lock()
        app.doku.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
        app.deleteDatabase(DokuDatabase.NAME)
    }

    @Test
    fun `Sonstiges lists Tracking below Sprachen`() {
        launchApp().use {
            compose.onNode(isSelectable() and hasText("Sonstiges")).performClick()

            val languages = compose.onNodeWithText("Sprachen").fetchSemanticsNode().boundsInRoot
            val tracking = compose.onNodeWithText("Tracking").fetchSemanticsNode().boundsInRoot
            assertTrue(tracking.top >= languages.bottom)
        }
    }

    @Test
    fun `the Tracking password is set on first opening, at least eight characters typed twice, the warning confirmed`() {
        launchApp().use {
            compose.openTracking()

            compose.onNodeWithText("Tracking-Passwort festlegen").assertIsDisplayed()
            compose.onNodeWithText("Das Passwort lässt sich nicht wiederherstellen.", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Passwort festlegen").performScrollTo().assertIsNotEnabled()
            compose.field("Tracking-Passwort").performTextInput("1234567")
            compose.field("Tracking-Passwort wiederholen").performTextInput("1234567")
            compose.onNodeWithText("Verstanden").performScrollTo().performClick()
            compose.onNodeWithText("Passwort festlegen").performScrollTo().performClick()
            compose.onNodeWithText("Mindestens 8 Zeichen").assertIsDisplayed()
            compose.onNodeWithText("Akute Beschwerden?").assertDoesNotExist()

            compose.field("Tracking-Passwort").performTextInput("8")
            compose.field("Tracking-Passwort wiederholen").performTextInput("8")
            compose.onNodeWithText("Passwort festlegen").performScrollTo().performClick()

            compose.onNodeWithText("Akute Beschwerden?").assertIsDisplayed()
        }
    }

    @Test
    fun `leaving for another tab locks Tracking, and only its own password opens it, not the Doku's`() {
        runBlocking {
            app.doku.load()
            app.doku.setUp(DOKU_PASSWORD)
        }
        app.doku.lock()
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()

            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.waitForIdle()
            assertTrue(app.tracking.state is IslandState.Locked)
            compose.openTracking()

            compose.onNodeWithText("Tracking ist gesperrt").assertIsDisplayed()
            compose.onNodeWithText("Akute Beschwerden?").assertDoesNotExist()
            compose.unlockTracking(DOKU_PASSWORD)
            compose.onNodeWithText("Falsches Passwort").assertIsDisplayed()
            compose.onNodeWithText("Akute Beschwerden?").assertDoesNotExist()

            compose.unlockTracking()
            compose.onNodeWithText("Akute Beschwerden?").assertIsDisplayed()
        }
    }

    @Test
    fun `back to the list of Sonstiges locks Tracking`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.waitForIdle()

            assertTrue(app.tracking.state is IslandState.Locked)
            compose.onNodeWithText("Tracking").assertIsDisplayed()
        }
    }

    @Test
    fun `Tracking locks as soon as the app leaves the screen`() {
        launchApp().use { scenario ->
            compose.openTracking()
            compose.setUpTracking()

            scenario.leaveAndReturn { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }

            compose.onNodeWithText("Tracking ist gesperrt").assertIsDisplayed()
            compose.onNodeWithText("Akute Beschwerden?").assertDoesNotExist()
        }
    }

    @Test
    fun `a rotation keeps Tracking open`() {
        launchApp().use { scenario ->
            compose.openTracking()
            compose.setUpTracking()

            scenario.recreate()

            compose.onNodeWithText("Akute Beschwerden?").assertIsDisplayed()
        }
    }

    @Test
    fun `a touch in Tracking starts its five minutes anew`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()

            ShadowSystemClock.advanceBy(Duration.ofMinutes(4))
            compose.onNodeWithText("Akute Beschwerden?").performClick()

            assertTrue(app.tracking.millisUntilIdle() > Duration.ofMinutes(4).toMillis())
        }
    }

    @Test
    fun `the fingerprint is turned on in Tracking's menu, opens Tracking, and is turned off again`() {
        fingerprint.enrolled = true
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()

            compose.onNodeWithContentDescription("Menü von Tracking").performClick()
            compose.onNodeWithText("Fingerabdruck").performClick()
            compose.waitForIdle()
            assertEquals(FingerprintPurpose.TurnOn, fingerprint.lastPurpose)
            fingerprint.succeed()
            compose.waitForIdle()
            assertEquals(IslandState.Open(fingerprint = true), app.tracking.state)
            assertEquals("the Doku's fingerprint is not asked", 0, app.fingerprint.prompts)

            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.openTracking()
            compose.waitForIdle()
            assertTrue(fingerprint.isPromptOpen)
            compose.onNodeWithText("Akute Beschwerden?").assertDoesNotExist()
            fingerprint.succeed()
            compose.waitForIdle()
            compose.onNodeWithText("Akute Beschwerden?").assertIsDisplayed()

            compose.onNodeWithContentDescription("Menü von Tracking").performClick()
            compose.onNodeWithText("Fingerabdruck").performClick()
            compose.waitForIdle()
            assertEquals(IslandState.Open(fingerprint = false), app.tracking.state)
        }
    }

    @Test
    fun `the Tracking password is changed in its menu`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()

            compose.onNodeWithContentDescription("Menü von Tracking").performClick()
            compose.onNodeWithText("Passwort ändern").performClick()
            compose.field("Bisheriges Passwort").performTextInput(TRACKING_PASSWORD)
            compose.field("Neues Passwort").performTextInput("ganz neues Passwort")
            compose.field("Neues Passwort wiederholen").performTextInput("ganz neues Passwort")
            compose.onNodeWithText("Ändern").performClick()

            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.openTracking()
            compose.unlockTracking("ganz neues Passwort")
            compose.onNodeWithText("Akute Beschwerden?").assertIsDisplayed()
        }
    }

    @Test
    fun `Alles loeschen in the menu asks first, and for the password`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            runBlocking { app.tracking.withDatabase { it.settings().put(TrackingSetting("probe", "Wert A")) } }

            compose.onNodeWithContentDescription("Menü von Tracking").performClick()
            compose.onNodeWithText("Alles löschen").performClick()
            compose.onNodeWithText("Alles löschen?").assertIsDisplayed()
            compose.onNodeWithText("Endgültig löschen").assertIsNotEnabled()
            passwordInDialog().performTextInput("falsches Passwort")
            compose.onNodeWithText("Endgültig löschen").performClick()
            compose.onNodeWithText("Falsches Passwort").assertIsDisplayed()
            assertEquals("Wert A", probe())

            passwordInDialog().performTextReplacement(TRACKING_PASSWORD)
            compose.onNodeWithText("Endgültig löschen").performClick()

            compose.onNodeWithText("Alles löschen?").assertDoesNotExist()
            assertNull(probe())
            compose.onNodeWithText("Akute Beschwerden?").assertIsDisplayed()
        }
    }

    @Test
    fun `Akute Beschwerden lists the warning signs with their sources and the medication hint`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()

            compose.onNodeWithText("Sofort 112 anrufen bei:").assertIsDisplayed()
            compose.onNodeWithText("Brustschmerz", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Quellen: ESC-LE-2019, ESC-SYNK-2018").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Zeitnah ärztlich abklären lassen bei:").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Ruhepuls dauerhaft über 100/min", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Quellen: HRS-2015").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Medikamente nur mit den Behandelnden ändern oder absetzen.").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `112 anrufen opens the phone app with the number, without calling`() {
        launchApp().use { scenario ->
            compose.openTracking()
            compose.setUpTracking()

            compose.onNodeWithText("112 anrufen").performClick()

            var started: Intent? = null
            scenario.onActivity { started = shadowOf(it).nextStartedActivity }
            assertEquals(Intent.ACTION_DIAL, started?.action)
            assertEquals(Uri.parse("tel:112"), started?.data)
        }
    }

    @Test
    fun `the keyboard is asked to learn nothing typed in Tracking`() {
        launchApp().use { scenario ->
            compose.openTracking()
            compose.field("Tracking-Passwort").performClick()

            val editorInfo = scenario.editorInfo(compose)

            assertTrue(editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            assertTrue(editorInfo.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        }
    }

    @Test
    fun `while a measurement shows, the screen stays on and the five minutes rest, and leaving still locks`() {
        launchApp().use { scenario ->
            compose.openTracking()
            compose.setUpTracking()
            var measuring by mutableStateOf(true)
            // A measuring assistant, as the Stehtest will have (#127), on the island Tracking is.
            scenario.onActivity { activity ->
                activity.setContent { if (measuring) WhileMeasuring(app.tracking) }
            }
            compose.waitForIdle()

            ShadowSystemClock.advanceBy(Duration.ofMinutes(10))
            var keptOn = false
            scenario.onActivity { keptOn = it.window.decorView.findViewById<View>(android.R.id.content).let(::anyKeptOn) }
            assertTrue(keptOn)
            assertEquals(Tracking.IDLE_MILLIS, app.tracking.millisUntilIdle())

            measuring = false
            compose.waitForIdle()
            scenario.onActivity { keptOn = it.window.decorView.findViewById<View>(android.R.id.content).let(::anyKeptOn) }
            assertFalse(keptOn)
            app.tracking.lock()
            assertTrue(app.tracking.state is IslandState.Locked)
        }
    }

    /** Whether [view] or one within it keeps the screen on. */
    private fun anyKeptOn(view: View): Boolean =
        view.keepScreenOn || (view is ViewGroup && (0 until view.childCount).any { anyKeptOn(view.getChildAt(it)) })

    private fun passwordInDialog() = compose.onNode(hasSetTextAction() and hasText("Tracking-Passwort") and hasAnyAncestor(isDialog()))

    private fun probe(): String? = runBlocking { app.tracking.withDatabase { it.settings().get("probe") } }

    private companion object {
        const val DOKU_PASSWORD = "doku-geheim-geheim"
    }
}
