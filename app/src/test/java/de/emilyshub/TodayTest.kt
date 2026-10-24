package de.emilyshub

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.OccurrenceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TodayTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `Heute lists today's routines in the order they are due`() {
        app.routine("Tagebuch", "20:00")
        app.routine("Vitamine", "07:30")

        launchApp().use {
            assertEquals(listOf("07:30 Vitamine", "20:00 Tagebuch"), rows())
        }
    }

    @Test
    fun `a routine left open until its time window ended shows as missed, and stays so`() {
        app.routine("Vitamine", "07:30") // open until 11:30
        app.clock.now = Instant.parse("2026-10-23T10:00:00Z") // 12:00 in Berlin
        runBlocking { app.reminders.update() } // as the app does when it starts

        launchApp().use {
            compose.onNodeWithText("Verpasst").assertIsDisplayed()
            row("Vitamine").assertIsNotEnabled()
            compose.onNodeWithText("Überspringen").assertDoesNotExist()
            assertEquals(listOf(OccurrenceStatus.Missed), statuses())
        }
    }

    @Test
    fun `after midnight, the day before's routines stay on Heute while their time window is open`() {
        app.routine("Lesen", "22:00") // open until 02:00
        app.clock.now = Instant.parse("2026-10-23T23:30:00Z") // 01:30 on Saturday in Berlin

        launchApp().use {
            assertEquals(listOf("Gestern, 22:00 Lesen", "22:00 Lesen"), rows())

            compose.onNode(isToggleable() and hasText("Gestern, 22:00")).performClick().assertIsOn()
        }
        val friday = runBlocking { app.routines.dueOn(LocalDate.parse("2026-10-23")).first().single() }
        assertEquals(OccurrenceStatus.Done, friday.status)
    }

    @Test
    fun `once its time window has ended, the day before's routine leaves Heute`() {
        app.routine("Lesen", "22:00") // open until 02:00
        app.clock.now = Instant.parse("2026-10-24T00:30:00Z") // 02:30 on Saturday in Berlin
        runBlocking { app.reminders.update() } // as the app does when it starts

        launchApp().use {
            assertEquals(listOf("22:00 Lesen"), rows())
        }
    }

    @Test
    fun `ticking a routine off marks it done, and unticking it undoes that`() {
        app.routine("Vitamine", "07:30")

        launchApp().use {
            row("Vitamine").performClick().assertIsOn()
            assertEquals(listOf(OccurrenceStatus.Done), statuses())

            row("Vitamine").performClick().assertIsOff()
            assertEquals(listOf(OccurrenceStatus.Open), statuses())
        }
    }

    @Test
    fun `each time of a routine is a row of its own, ticked off on its own`() {
        app.routine("Tabletten", "20:00", "08:00")

        launchApp().use {
            assertEquals(listOf("08:00 Tabletten", "20:00 Tabletten"), rows())

            compose.onNode(isToggleable() and hasText("08:00")).performClick().assertIsOn()
            compose.onNode(isToggleable() and hasText("20:00")).assertIsOff()
        }
    }

    @Test
    fun `a skipped routine says so and can be undone`() {
        app.routine("Joggen", "18:00")

        launchApp().use {
            compose.onNodeWithText("Überspringen").performClick()
            assertEquals(listOf("18:00 Joggen Übersprungen"), rows())

            compose.onNodeWithText("Rückgängig").performClick()
            assertEquals(listOf("18:00 Joggen"), rows())
            assertEquals(listOf(OccurrenceStatus.Open), statuses())
        }
    }

    @Test
    fun `a day without routines says so`() {
        launchApp().use {
            compose.onNodeWithText("Heute steht nichts an", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `at midnight the list moves on to the new day`() {
        app.routine("Vitamine", "07:30")
        app.clock.now = Instant.parse("2026-10-23T21:59:00Z") // 23:59 in Berlin

        launchApp().use {
            row("Vitamine").performClick().assertIsOn()

            app.clock.now = Instant.parse("2026-10-23T22:01:00Z") // 00:01 on Saturday
            compose.mainClock.advanceTimeBy(3 * 60_000L)

            row("Vitamine").assertIsOff()
        }
    }

    @Test
    fun `when the phone changes time zone, the list shows the day it is there`() {
        app.routine("Vitamine", "07:30")
        app.clock.now = Instant.parse("2026-10-23T20:00:00Z") // Friday 22:00 in Berlin

        launchApp().use {
            row("Vitamine").performClick().assertIsOn()

            app.clock.timeZone = ZoneId.of("Asia/Tokyo") // Saturday 05:00 there
            app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))

            row("Vitamine").assertIsOff()
        }
    }

    private fun row(name: String) = compose.onNode(isToggleable() and hasText(name))

    private fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }

    /** Each row of the Heute list, as its texts read out together. */
    private fun rows() = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.Text].joinToString(" ") { it.text } }
}
