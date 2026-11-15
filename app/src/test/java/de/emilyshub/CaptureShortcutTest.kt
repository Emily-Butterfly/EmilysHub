package de.emilyshub

import android.content.Intent
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Neuer Gedanke" on a long press of the app's icon (SPEC.md §16, #94): it opens the launcher activity with an extra,
 * which shows the line for a thought over what was open, behind the app's lock.
 */
@RunWith(AndroidJUnit4::class)
class CaptureShortcutTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `the app offers the shortcut, which opens the launcher activity with its extra over what is open`() {
        launchApp().use {
            val shortcut = app.getSystemService(ShortcutManager::class.java).dynamicShortcuts.single()
            assertEquals("capture", shortcut.id)
            assertEquals("Neuer Gedanke", shortcut.shortLabel)
            val intent = checkNotNull(shortcut.intent)
            assertEquals(MainActivity::class.java.name, intent.component?.className)
            assertTrue(intent.getBooleanExtra("de.emilyshub.extra.CAPTURE", false))
            // Not a cleared task: what was open stays.
            assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        }
    }

    @Test
    fun `the intent with the extra opens the line with the keyboard, Festhalten keeps the thought, back leaves`() {
        ActivityScenario.launch<MainActivity>(MainActivity.capture(app)).use {
            compose.onNode(isHeading() and hasText("Neuer Gedanke")).assertIsDisplayed()
            line().assertIsFocused()

            line().performTextInput("Zahnarzt anrufen")
            compose.onNode(hasText("Festhalten") and hasClickAction()).performClick()

            line().assertTextEquals("Was geht dir durch den Kopf?", "")
            assertEquals(listOf("Zahnarzt anrufen"), texts())

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(isHeading() and hasText("Heute")).assertIsDisplayed()
        }
    }

    @Test
    fun `locked, the line shows only once the app is unlocked`() {
        app.authenticator.deviceSecure = true

        ActivityScenario.launch<MainActivity>(MainActivity.capture(app)).use {
            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
            line().assertDoesNotExist()

            compose.unlockWithFingerprint(app.authenticator)

            line().assertIsDisplayed()
        }
    }

    @Test
    fun `an editor open below keeps its draft`() {
        launchApp().use { scenario ->
            compose.onNode(hasText("To-dos") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            compose.onNode(hasSetTextAction() and hasText("Titel")).performTextInput("Steuer")

            // As Android hands the shortcut to the activity already open.
            scenario.onActivity { activity ->
                android.app.Activity::class.java.getDeclaredMethod("onNewIntent", Intent::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, MainActivity.capture(activity))
            }
            line().performTextInput("Belege suchen")
            compose.onNode(hasText("Festhalten") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNode(isHeading() and hasText("Neues To-do")).assertIsDisplayed()
            compose.onNode(hasSetTextAction() and hasText("Titel")).assertTextContains("Steuer")
        }
        assertEquals(listOf("Belege suchen"), texts())
    }

    @Test
    fun `the line stays through rotation`() {
        ActivityScenario.launch<MainActivity>(MainActivity.capture(app)).use { scenario ->
            line().performTextInput("Halb getippt")

            scenario.recreate()

            line().assertTextContains("Halb getippt")
        }
    }

    private fun line() = compose.onNode(hasSetTextAction() and hasText("Was geht dir durch den Kopf?"))

    private fun texts() = runBlocking { app.inbox.entries().first().map { it.text } }
}
