package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.island.IslandState
import de.emilyshub.tracking.AbortCause
import de.emilyshub.tracking.StandTest
import de.emilyshub.tracking.Tracking
import de.emilyshub.tracking.TrackingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

/**
 * The Stehtest assistant of Tracking (SPEC.md §17, "Stehtest"): run through, aborted, and locked as the screen goes
 * off. Made-up values only.
 */
@RunWith(AndroidJUnit4::class)
class TrackingStandTestScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @After
    fun closeDatabase() {
        app.tracking.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
    }

    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    /** Lets [duration] pass on the app's clock, and the timers see it. */
    private fun pass(duration: Duration) {
        app.clock.now = app.clock.now.plus(duration)
        compose.mainClock.advanceTimeBy(TICKS_MILLIS)
        compose.waitForIdle()
    }

    private fun tests(): List<StandTest> = checkNotNull(runBlocking { app.tracking.withDatabase { it.standTests().all() } })

    /** Opens a new Stehtest of [variant], ticks the whole checklist and lies down. */
    private fun start(variant: String = "Stehen") {
        compose.openTracking()
        compose.setUpTracking()
        compose.onNodeWithText("+ Stehtest").tap()
        compose.onNodeWithText(variant).tap()
        compose.onNodeWithText("Hinlegen und starten").assertIsNotEnabled()
        listOf(
            "Eine zweite Person ist dabei",
            "Ich bin neben Bett oder Sofa",
            "Heute keine akuten Beschwerden",
            "Nicht direkt nach heißem Duschen, Alkohol oder großer Mahlzeit",
            "Medikamente wie gewohnt genommen",
        ).forEach { compose.onNodeWithText(it).tap() }
        compose.field("Koffein heute bisher (Anzahl)").performScrollTo().performTextInput("1")
        compose.onNodeWithText("Hinlegen und starten").assertIsEnabled().tap()
    }

    /** Lies the five minutes, enters the lying pulse and stands up. */
    private fun standUp() {
        compose.onNodeWithText("Gemessen – aufstehen").assertIsNotEnabled()
        pass(Duration.ofMinutes(5))
        compose.field("Puls (pro Minute)").performTextInput("62")
        compose.onNodeWithText("Gemessen – aufstehen").assertIsEnabled().tap()
    }

    @Test
    fun `a test runs through lying and each point to its result and the list`() {
        launchApp().use {
            start()
            assertEquals(Tracking.IDLE_MILLIS, app.tracking.millisUntilIdle())
            standUp()

            var minute = 0
            listOf(1 to "80", 3 to "95", 5 to "96", 8 to "90", 10 to "88").forEach { (next, pulse) ->
                compose.onNodeWithText("Messung bei Minute $next").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Messung speichern").performScrollTo().assertIsNotEnabled()
                pass(Duration.ofMinutes((next - minute).toLong()))
                minute = next
                compose.onNodeWithText("Jetzt messen").performScrollTo().assertIsDisplayed()
                compose.field("Puls (pro Minute)").performTextInput(pulse)
                compose.onNodeWithText("Messung speichern").tap()
            }

            compose.onNodeWithText("Abgeschlossen").assertIsDisplayed()
            compose.onNodeWithText("Ein Heimtest ersetzt keine ärztliche Untersuchung", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Größter Pulsanstieg gegenüber dem Liegen: +34").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Messungen mit Anstieg ≥ 30/min: Minute 3, 5").performScrollTo().assertIsDisplayed()
            val test = tests().single()
            assertTrue(test.finished)
            assertEquals(1, test.caffeine)

            app.tracking.lock()
            compose.unlockTracking()
            compose.onNodeWithText("Stehtests").tap()
            compose.onNodeWithText("Stehen · Abgeschlossen · größter Anstieg +34").assertIsDisplayed()
        }
    }

    @Test
    fun `aborted, it keeps what was measured with the minute and the reason`() {
        launchApp().use {
            start("Anlehnen")
            standUp()
            pass(Duration.ofMinutes(1))
            compose.field("Puls (pro Minute)").performTextInput("110")
            compose.onNodeWithText("Messung speichern").tap()
            pass(Duration.ofSeconds(90))

            compose.onNodeWithText("Abbrechen – hinlegen").tap()
            compose.field("Grund (optional)").performTextInput("schwindelig")
            compose.onNodeWithText("Abbrechen").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("Abgebrochen nach 2 min").assertIsDisplayed()
            compose.onNodeWithText("Grund: schwindelig").assertIsDisplayed()
            val test = tests().single()
            assertEquals(AbortCause.Pressed, test.abortCause)
            assertEquals(2, test.abortedMinute)
            assertEquals(110, runBlocking { app.tracking.withDatabase { it.standTests().points(test.id) } }!!.single().pulse)
        }
    }

    @Test
    fun `the screen going off locks Tracking, and the test is kept as aborted`() {
        launchApp().use { scenario ->
            start()
            standUp()
            pass(Duration.ofMinutes(1))
            compose.field("Puls (pro Minute)").performTextInput("99")
            // A frame for what was typed, as there always is before the screen can go off.
            compose.waitForIdle()

            scenario.moveToState(Lifecycle.State.CREATED)
            compose.waitForIdle()

            assertTrue(app.tracking.state is IslandState.Locked)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.unlockTracking()
            val test = tests().single()
            assertEquals(AbortCause.Locked, test.abortCause)
            assertEquals(1, test.abortedMinute)
            assertEquals("typed, kept", 99, runBlocking { app.tracking.withDatabase { it.standTests().points(test.id) } }!!.single().pulse)
        }
    }

    @Test
    fun `back while a test runs asks to abort it`() {
        launchApp().use { scenario ->
            start()
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()

            compose.onNodeWithText("Test abbrechen?").assertIsDisplayed()
            compose.onNodeWithText("Weiter messen").performClick()
            compose.onNodeWithText("Liegen").assertIsDisplayed()
        }
    }

    private companion object {
        /** Long enough for the timers of the page to tick. */
        const val TICKS_MILLIS = 1_000L
    }
}
