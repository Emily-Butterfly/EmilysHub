package de.emilyshub

import android.text.InputType
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
import org.junit.Assert.assertEquals
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
            assertEquals(false, fingerprint.lastToUnlock)
            fingerprint.succeed()
            compose.waitForIdle()

            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.openDoku()
            compose.waitForIdle()

            assertTrue(fingerprint.isPromptOpen)
            assertEquals(true, fingerprint.lastToUnlock)
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

    private fun ComposeTestRule.openDoku() {
        onNode(isSelectable() and hasText("Sonstiges")).performClick()
        onNode(hasText("Dokumentation") and hasClickAction()).performClick()
    }

    private fun ComposeTestRule.setUpDoku(withFingerprint: Boolean = false) {
        field("Doku-Passwort").performTextInput(PASSWORD)
        field("Doku-Passwort wiederholen").performTextInput(PASSWORD)
        onNodeWithText("Verstanden").performScrollTo().performClick()
        if (!withFingerprint && fingerprint.enrolled) onNodeWithText("Mit Fingerabdruck entsperren").performScrollTo().performClick()
        onNodeWithText("Passwort festlegen").performScrollTo().performClick()
    }

    private fun ComposeTestRule.unlockDoku(password: String = PASSWORD) {
        field("Passwort").performTextReplacement(password)
        onNodeWithText("Entsperren").performClick()
    }

    private fun ComposeTestRule.field(label: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText(label))

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
        const val PASSWORD = "geheim-geheim"
    }
}
