package de.emilyshub

import android.content.ClipboardManager
import android.text.InputType
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import de.emilyshub.ui.doku.DokuInsel
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.doku.DokuState
import de.emilyshub.doku.FingerprintPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * The Doku under Sonstiges (SPEC.md §14): its own password set on first opening, the fingerprint as a shortcut, and
 * its lock as soon as it is left, nothing of it shown before it is unlocked.
 */
@RunWith(AndroidJUnit4::class)
class DokuScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val fingerprint = app.fingerprint

    @Test
    fun `Sonstiges lists the Doku below the goals`() {
        launchApp().use {
            compose.onNode(isSelectable() and hasText("Sonstiges")).performClick()

            val goals = compose.onNodeWithText("Ziele").fetchSemanticsNode().boundsInRoot
            val doku = compose.onNodeWithText("Dokumentation").fetchSemanticsNode().boundsInRoot
            assertTrue(doku.top >= goals.bottom)
        }
    }

    @Test
    fun `the Doku password is set on first opening, at least eight characters typed twice, the warning confirmed`() {
        launchApp().use {
            compose.openDoku()

            compose.onNodeWithText("Doku-Passwort festlegen").assertIsDisplayed()
            compose.onNodeWithText("Das Passwort lässt sich nicht wiederherstellen. Ohne Passwort und Fingerabdruck sind die Daten verloren.")
                .assertIsDisplayed()
            compose.onNodeWithText("Mit Fingerabdruck entsperren").assertDoesNotExist()
            compose.onNodeWithText("Passwort festlegen").performScrollTo().assertIsNotEnabled()

            compose.field("Doku-Passwort").performTextInput("1234567")
            compose.field("Doku-Passwort wiederholen").performTextInput("1234567")
            compose.onNodeWithText("Verstanden").performScrollTo().performClick()
            compose.onNodeWithText("Passwort festlegen").performScrollTo().performClick()
            compose.onNodeWithText("Mindestens 8 Zeichen").assertIsDisplayed()

            compose.field("Doku-Passwort").performTextInput("8")
            compose.onNodeWithText("Passwort festlegen").performScrollTo().performClick()
            compose.onNodeWithText("Die beiden Passwörter sind nicht gleich").assertIsDisplayed()

            compose.field("Doku-Passwort wiederholen").performTextInput("8")
            compose.onNodeWithText("Passwort festlegen").performScrollTo().performClick()

            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()
        }
    }

    @Test
    fun `leaving for another tab locks the Doku, and it opens again only with its password`() {
        launchApp().use {
            compose.openDoku()
            compose.setUpDoku()

            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.waitForIdle()
            assertTrue(app.doku.state is DokuState.Locked)
            compose.openDoku()

            compose.onNodeWithText("Die Dokumentation ist gesperrt").assertIsDisplayed()
            compose.onNodeWithText("Noch keine Patienten").assertDoesNotExist()
            compose.field("Passwort").performTextInput("falsches Passwort")
            compose.onNodeWithText("Entsperren").performClick()
            compose.onNodeWithText("Falsches Passwort").assertIsDisplayed()
            compose.onNodeWithText("Noch keine Patienten").assertDoesNotExist()

            compose.unlockDoku()
            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()
        }
    }

    @Test
    fun `back to the list of Sonstiges locks the Doku`() {
        launchApp().use {
            compose.openDoku()
            compose.setUpDoku()

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.waitForIdle()

            assertTrue(app.doku.state is DokuState.Locked)
            compose.onNodeWithText("Dokumentation").assertIsDisplayed()
        }
    }

    @Test
    fun `the Doku locks as soon as the app leaves the screen, unlike the app lock`() {
        launchApp().use { scenario ->
            compose.openDoku()
            compose.setUpDoku()

            scenario.leaveAndReturn { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }

            compose.onNodeWithText("Die Dokumentation ist gesperrt").assertIsDisplayed()
            compose.onNodeWithText("Noch keine Patienten").assertDoesNotExist()
        }
    }

    @Test
    fun `a rotation keeps the Doku open`() {
        launchApp().use { scenario ->
            compose.openDoku()
            compose.setUpDoku()

            scenario.recreate()

            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()
        }
    }

    @Test
    fun `with the fingerprint, the Doku asks for it at once, and nothing of it shows before`() {
        fingerprint.enrolled = true
        launchApp().use {
            compose.openDoku()
            compose.setUpDoku(withFingerprint = true)
            compose.waitForIdle()
            assertEquals(FingerprintPurpose.TurnOn, fingerprint.lastPurpose)
            fingerprint.succeed()
            compose.waitForIdle()

            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.openDoku()
            compose.waitForIdle()

            assertTrue(fingerprint.isPromptOpen)
            assertEquals(FingerprintPurpose.Unlock, fingerprint.lastPurpose)
            compose.onNodeWithText("Noch keine Patienten").assertDoesNotExist()
            fingerprint.succeed()
            compose.waitForIdle()
            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()
        }
    }

    @Test
    fun `the prompt's "Passwort" leads to the password field`() {
        fingerprint.enrolled = true
        launchApp().use {
            compose.openDoku()
            compose.setUpDoku(withFingerprint = true)
            compose.waitForIdle()
            fingerprint.succeed()
            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.openDoku()

            compose.waitForIdle()
            fingerprint.chooseOther()

            compose.unlockDoku()
            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()
        }
    }

    @Test
    fun `after a new fingerprint on the phone the password is asked for, and the fingerprint offered again`() {
        fingerprint.enrolled = true
        launchApp().use {
            compose.openDoku()
            compose.setUpDoku(withFingerprint = true)
            compose.waitForIdle()
            fingerprint.succeed()
            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            fingerprint.enrollNew()

            compose.openDoku()

            compose.onNodeWithText("Am Handy wurde ein neuer Fingerabdruck eingerichtet.", substring = true).assertIsDisplayed()
            compose.unlockDoku()
            compose.onNodeWithText("Fingerabdruck wieder einschalten?").assertIsDisplayed()
            compose.onNodeWithText("Einschalten").performClick()
            compose.waitForIdle()
            fingerprint.succeed()
            compose.waitForIdle()
            assertEquals(DokuState.Open(fingerprint = true), app.doku.state)
        }
    }

    @Test
    fun `the Doku password is changed in its menu, the data key staying`() {
        launchApp().use {
            compose.openDoku()
            compose.setUpDoku()

            compose.onNodeWithContentDescription("Menü der Dokumentation").performClick()
            compose.onNodeWithText("Doku-Passwort ändern").performClick()
            compose.onNodeWithText("Ältere Backups brauchen weiter das alte Passwort.").assertIsDisplayed()
            compose.field("Bisheriges Passwort").performTextInput("falsch-falsch")
            compose.field("Neues Passwort").performTextInput("ganz neues Passwort")
            compose.field("Neues Passwort wiederholen").performTextInput("ganz neues Passwort")
            compose.onNodeWithText("Ändern").performClick()
            compose.onNodeWithText("Falsches Passwort").assertIsDisplayed()
            compose.field("Bisheriges Passwort").performTextReplacement(PASSWORD)
            compose.onNodeWithText("Ändern").performClick()
            compose.onNodeWithText("Ältere Backups brauchen weiter das alte Passwort.").assertDoesNotExist()

            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.openDoku()
            compose.unlockDoku("ganz neues Passwort")
            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()
        }
    }

    @Test
    fun `the fingerprint is turned on and off in the Doku's menu`() {
        fingerprint.enrolled = true
        launchApp().use {
            compose.openDoku()
            compose.setUpDoku()

            compose.onNodeWithContentDescription("Menü der Dokumentation").performClick()
            compose.onNodeWithText("Fingerabdruck").performClick()
            compose.waitForIdle()
            fingerprint.succeed()
            compose.waitForIdle()
            assertEquals(DokuState.Open(fingerprint = true), app.doku.state)

            compose.onNodeWithContentDescription("Menü der Dokumentation").performClick()
            compose.onNodeWithText("Fingerabdruck").performClick()
            compose.waitForIdle()
            assertEquals(DokuState.Open(fingerprint = false), app.doku.state)
        }
    }

    @Test
    fun `the keyboard is asked to learn nothing typed in the Doku and to suggest nothing`() {
        launchApp().use { scenario ->
            compose.openDoku()
            compose.field("Doku-Passwort").performClick()

            val editorInfo = scenario.editorInfo()

            assertTrue(editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
            assertTrue(editorInfo.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
        }
    }

    @Test
    fun `elsewhere the keyboard may learn`() {
        launchApp().use { scenario ->
            compose.onNodeWithContentDescription("Suchen").performClick()
            compose.onNode(hasSetTextAction()).performClick()

            val editorInfo = scenario.editorInfo()

            assertEquals(0, editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        }
    }

    @Test
    fun `the fingerprint switch at setup is off until turned on`() {
        fingerprint.enrolled = true
        launchApp().use {
            compose.openDoku()
            compose.setUpDoku()

            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()
            assertEquals(0, fingerprint.prompts)
        }
    }

    @Test
    fun `the fingerprint prompt, gone as the app leaves the screen, comes again on return`() {
        fingerprint.enrolled = true
        launchApp().use { scenario ->
            compose.openDoku()
            compose.setUpDoku(withFingerprint = true)
            compose.waitForIdle()
            fingerprint.succeed()
            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.openDoku()
            compose.waitForIdle()
            assertEquals(2, fingerprint.prompts)

            scenario.leaveAndReturn {}
            compose.waitForIdle()

            assertEquals(3, fingerprint.prompts)
            assertTrue(fingerprint.isPromptOpen)
            compose.field("Passwort").assertDoesNotExist()
        }
    }

    @Test
    fun `a touch in a dialog of the Doku starts its five minutes anew`() {
        launchApp().use {
            compose.openDoku()
            compose.setUpDoku()
            compose.onNodeWithContentDescription("Menü der Dokumentation").performClick()
            compose.onNodeWithText("Doku-Passwort ändern").performClick()

            ShadowSystemClock.advanceBy(Duration.ofMinutes(4))
            compose.field("Bisheriges Passwort").performClick()

            assertTrue(app.doku.millisUntilIdle() > Duration.ofMinutes(4).toMillis())
        }
    }

    @Test
    fun `text copied from a text field of the Doku is marked sensitive, and gone from the clipboard once it locks`() {
        launchApp().use { scenario ->
            compose.openDoku()
            compose.setUpDoku()
            // A text field such as the patients' editors will have (#59), on the island the Doku is.
            scenario.onActivity { activity ->
                activity.setContent {
                    DokuInsel(app.doku) {
                        var text by remember { mutableStateOf("") }
                        TextField(value = text, onValueChange = { text = it }, label = { Text("Diagnose") })
                    }
                }
            }
            compose.field("Diagnose").performTextInput("Schulter links")
            compose.field("Diagnose").performTextInputSelection(TextRange(0, "Schulter links".length))
            compose.field("Diagnose").performSemanticsAction(SemanticsActions.CopyText)
            compose.waitForIdle()

            val clipboard = app.getSystemService(ClipboardManager::class.java)
            val clip = checkNotNull(clipboard.primaryClip)
            assertEquals("Schulter links", clip.getItemAt(0).text.toString())
            assertTrue(clip.description.extras.getBoolean("android.content.extra.IS_SENSITIVE"))

            app.doku.lock()

            assertFalse(clipboard.hasPrimaryClip())
        }
    }

    /** What the focused text field tells the keyboard, as Android asks for it. */
    private fun ActivityScenario<MainActivity>.editorInfo(): EditorInfo {
        compose.waitForIdle()
        val info = EditorInfo()
        onActivity { activity ->
            val content = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as AbstractComposeView
            val owner = content.getChildAt(0)
            checkNotNull(owner.onCreateInputConnection(info)) { "No text field has the focus" }
        }
        return info
    }

    private companion object {
        const val PASSWORD = DOKU_PASSWORD
    }
}
