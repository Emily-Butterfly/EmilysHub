package de.emilyshub

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
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
import de.emilyshub.routines.RoutineDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class TodayTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `Heute lists today's routines in the order they are due`() {
        routine("Tagebuch", "20:00")
        routine("Vitamine", "07:30")

        launchApp().use {
            assertEquals(listOf("07:30 Vitamine", "20:00 Tagebuch"), rows())
        }
    }

    @Test
    fun `ticking a routine off marks it done, and unticking it undoes that`() {
        routine("Vitamine", "07:30")

        launchApp().use {
            row("Vitamine").performClick().assertIsOn()
            assertEquals(listOf(OccurrenceStatus.Done), statuses())

            row("Vitamine").performClick().assertIsOff()
            assertEquals(listOf(OccurrenceStatus.Open), statuses())
        }
    }

    @Test
    fun `a skipped routine says so and can be undone`() {
        routine("Joggen", "18:00")

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
        routine("Vitamine", "07:30")
        app.clock.now = Instant.parse("2026-10-23T21:59:00Z") // 23:59 in Berlin

        launchApp().use {
            row("Vitamine").performClick().assertIsOn()

            app.clock.now = Instant.parse("2026-10-23T22:01:00Z") // 00:01 on Saturday
            compose.mainClock.advanceTimeBy(3 * 60_000L)

            row("Vitamine").assertIsOff()
        }
    }

    private fun row(name: String) = compose.onNode(isToggleable() and hasText(name))

    private fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }

    private fun routine(name: String, time: String) = runBlocking {
        app.routines.create(RoutineDraft(name = name, description = "", time = LocalTime.parse(time)))
    }

    /** Each row of the Heute list, as its texts read out together. */
    private fun rows() = compose.onAllNodes(isToggleable()).fetchSemanticsNodes()
        .map { node -> node.config[SemanticsProperties.Text].joinToString(" ") { it.text } }
}
