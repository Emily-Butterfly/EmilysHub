package de.emilyshub

import android.Manifest
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.OccurrenceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowToast

/** The Ablauf-Modus: a routine's checklist, step by step (SPEC.md §2; #101). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h915dp") // the Galaxy A52
class RunScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun allowReminders() {
        // Heute warns while the reminders lack something from Android, which is not what these tests are about.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.settingsDao.values["reminders-asked"] = "yes"
    }

    @Test
    fun `an open routine with a checklist has Starten on Heute, one without has not`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser", "Zähne"))
        app.routine("Joggen", "18:00")

        launchApp().use {
            assertEquals(1, compose.onAllNodes(hasText("Starten") and hasClickAction()).fetchSemanticsNodes().size)
            start()
            compose.onNodeWithText("Wasser").assertIsDisplayed()
        }
    }

    @Test
    fun `Fertig runs through the checklist to Geschafft, which leads back to Heute`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser", "Zähne"))

        launchApp().use {
            start()
            compose.onNodeWithText("Schritt 1 von 2", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Morgens").assertIsDisplayed()
            compose.onNodeWithText("Schritt zurück").assertIsNotEnabled()
            fertig()
            compose.onNodeWithText("Zähne").assertIsDisplayed()
            compose.onNodeWithText("Schritt 2 von 2", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Schritt zurück").assertIsEnabled()
            fertig()

            compose.onNodeWithText("Geschafft").assertIsDisplayed()
            compose.onNodeWithText("Streak: 1 Tag").assertIsDisplayed()
            compose.onNodeWithText("Dauer", substring = true).assertIsDisplayed()
            assertEquals(listOf(OccurrenceStatus.Done), statuses())

            compose.onNodeWithText("Zu Heute").performClick()
            compose.onNodeWithText("Für heute ist alles erledigt").assertIsDisplayed()
        }
    }

    @Test
    fun `Schritt zurueck takes the last step back`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser", "Zähne"))

        launchApp().use {
            start()
            fertig()
            compose.onNodeWithText("Schritt zurück").performClick()

            compose.onNodeWithText("Wasser").assertIsDisplayed()
            assertEquals(listOf(listOf(false, false)), ticks())
        }
    }

    @Test
    fun `left, the run keeps its ticks, and Starten takes it up again`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser", "Zähne", "Dehnen"))

        launchApp().use { scenario ->
            start()
            fertig()
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            assertEquals(listOf(listOf(true, false, false)), ticks())
            start()
            compose.onNodeWithText("Zähne").assertIsDisplayed()
            compose.onNodeWithText("Schritt 2 von 3", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `taken up again, the stopwatch counts on from the first start`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser", "Zähne"))

        launchApp().use { scenario ->
            start()
            compose.onNodeWithText("Schritt 1 von 2 · 0:00").assertIsDisplayed()
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            app.clock.now = app.clock.now.plusSeconds(125)

            start()

            compose.onNodeWithText("Schritt 1 von 2 · 2:05").assertIsDisplayed()
        }
    }

    @Test
    fun `a step with minutes shows a ring with its time left, and when all is done by`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser", "Zähne"), minutes = listOf(5, 3))

        launchApp().use {
            start()

            compose.onNodeWithContentDescription("5:00").assertIsDisplayed()
            compose.onNodeWithText("noch 2 Schritte, fertig gegen 07:08").assertIsDisplayed()
        }
    }

    @Test
    fun `its time over, the ring counts on as overtime, and only Fertig goes on`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser", "Zähne"), minutes = listOf(1, null))

        launchApp().use {
            start()
            app.clock.now = app.clock.now.plusSeconds(105)
            compose.mainClock.advanceTimeBy(2_000)

            compose.onNodeWithContentDescription("Überzeit +0:45").assertIsDisplayed()
            compose.onNodeWithText("Wasser").assertIsDisplayed()
            compose.onNodeWithText("noch 2 Schritte").assertIsDisplayed()
        }
    }

    @Test
    fun `Starten at another routine asks whether to end the run running`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser"))
        app.routine("Abends", "20:00", checklist = listOf("Lesen"))

        launchApp().use { scenario ->
            compose.onAllNodes(hasText("Starten") and hasClickAction())[0].performClick()
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onAllNodes(hasText("Starten") and hasClickAction())[1].performClick()

            compose.onNodeWithText("„Morgens“ läuft noch. Beenden und „Abends“ starten?", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Beenden und starten").performClick()

            compose.onNodeWithText("Lesen").assertIsDisplayed()
            assertEquals("Abends", runBlocking { app.routines.get(checkNotNull(app.runs.current()).routineId) }?.name)
        }
    }

    @Test
    fun `tapping the notification opens the run behind the lock`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser"))
        val due = runBlocking { app.routines.dueOn(app.routines.today()).first().single() }
        runBlocking { app.runs.start(due.occurrenceId, due.routineId) }

        ActivityScenario.launch<MainActivity>(MainActivity.openRun(app, due.occurrenceId, due.routineId)).use {
            compose.onNodeWithText("Wasser").assertIsDisplayed()
            compose.onNodeWithText("Fertig").assertIsDisplayed()
        }
    }

    @Test
    fun `the run outlives the activity being made anew`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser", "Zähne", "Dehnen"))

        launchApp().use { scenario ->
            start()
            fertig()
            scenario.recreate()

            compose.onNodeWithText("Zähne").assertIsDisplayed()
            compose.onNodeWithText("Schritt zurück").assertIsEnabled()
        }
    }

    @Test
    fun `the run closes, saying why, once the routine is skipped meanwhile`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser", "Zähne"))

        launchApp().use {
            start()
            runBlocking { app.routines.skip(occurrence()) }

            compose.waitForIdle()
            assertEquals("Der Ablauf ist beendet: Die Routine wurde übersprungen.", ShadowToast.getTextOfLatestToast())
            compose.onNodeWithText("Für heute ist alles erledigt").assertIsDisplayed()
        }
    }

    @Test
    fun `the screen stays on while the run shows`() {
        app.routine("Morgens", "08:00", checklist = listOf("Wasser"))

        launchApp().use { scenario ->
            start()
            compose.onNodeWithText("Fertig").assertExists()
            scenario.onActivity { assertEquals(true, it.keepsScreenOn()) }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            scenario.onActivity { assertEquals(false, it.keepsScreenOn()) }
        }
    }

    @Test
    @Config(fontScale = 1.5f)
    fun `a long item wraps, and Fertig stays in view at a font of 150 percent`() {
        val long = "Die Fenster im Wohnzimmer und in der Küche weit öffnen und zehn Minuten lüften"
        app.routine("Morgens", "08:00", checklist = listOf(long))

        launchApp().use {
            start()
            compose.onNodeWithText(long).assertExists()
            compose.onNodeWithText("Fertig").assertIsDisplayed()
        }
    }

    private fun start() = compose.onNode(hasText("Starten") and hasClickAction()).performClick()

    private fun fertig() = compose.onNode(hasText("Fertig") and hasClickAction()).performClick()

    private fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }

    private fun ticks() = runBlocking { app.routines.dueOn(app.routines.today()).first().map { r -> r.checklist.map { it.ticked } } }

    private fun occurrence() = runBlocking { app.routines.dueOn(app.routines.today()).first().single().occurrenceId }

    /** Whether some view of the activity keeps the screen on. */
    private fun MainActivity.keepsScreenOn(): Boolean {
        fun View.keeps(): Boolean = keepScreenOn || (this is ViewGroup && (0 until childCount).any { getChildAt(it).keeps() })
        return window.decorView.keeps()
    }
}
