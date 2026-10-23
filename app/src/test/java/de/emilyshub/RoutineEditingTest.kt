package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
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
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.Routine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RoutineEditingTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a routine created in the Routinen tab is listed there and due on Heute`() {
        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            heading("Neue Routine").assertIsDisplayed()

            field("Name").performTextInput("Zähne putzen")
            field("Beschreibung (optional)").performTextInput("Zwei Minuten")
            changeTime(from = "08:00", hour = "07", minute = "30")
            compose.onNodeWithText("Speichern").performClick()

            heading("Routinen").assertIsDisplayed()
            compose.onNodeWithText("Zähne putzen").assertIsDisplayed()
            compose.onNodeWithText("Täglich um 07:30").assertIsDisplayed()

            tab("Heute").performClick()
            compose.onNodeWithText("Zähne putzen").assertIsDisplayed()
            compose.onNodeWithText("07:30").assertIsDisplayed()
        }
        assertEquals(listOf("Zähne putzen" to "Zwei Minuten"), savedRoutines().map { it.name to it.description })
    }

    @Test
    fun `a routine is only saved with a name`() {
        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()

            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Bitte gib einen Namen ein.").assertIsDisplayed()
            heading("Neue Routine").assertIsDisplayed()
        }
        assertEquals(emptyList<String>(), savedRoutines())
    }

    @Test
    fun `tapping a routine opens it for editing`() {
        app.routine("Vitamine", "08:00")

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithText("Vitamine").performClick()
            heading("Routine bearbeiten").assertIsDisplayed()

            field("Name").performTextReplacement("Vitamin D")
            changeTime(from = "08:00", hour = "09", minute = "15")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Vitamin D").assertIsDisplayed()
            compose.onNodeWithText("Täglich um 09:15").assertIsDisplayed()
        }
    }

    @Test
    fun `a routine can have several times on certain weekdays`() {
        launchApp().use {
            newRoutine("Tabletten")
            changeTime(from = "08:00", hour = "07", minute = "30")
            addTime(hour = "20", minute = "00")
            option("An bestimmten Wochentagen").tap()
            // Monday to Friday to start with.
            weekday("Di").tap()
            weekday("Do").tap()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Mo, Mi, Fr um 07:30 und 20:00").assertIsDisplayed()
        }
        val saved = savedRoutines().single()
        assertEquals(listOf(LocalTime.of(7, 30), LocalTime.of(20, 0)), saved.times)
        assertEquals(Repetition.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), saved.repetition)
    }

    @Test
    fun `a time can be taken away again, but not the last one`() {
        launchApp().use {
            newRoutine("Wasser trinken")
            addTime(hour = "12", minute = "00")

            compose.onNodeWithContentDescription("08:00 entfernen").tap()

            compose.onNodeWithContentDescription("12:00 entfernen").assertDoesNotExist()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Täglich um 12:00").assertIsDisplayed()
        }
    }

    @Test
    fun `certain weekdays need at least one day`() {
        launchApp().use {
            newRoutine("Gießen")
            option("An bestimmten Wochentagen").tap()
            listOf("Mo", "Di", "Mi", "Do", "Fr").forEach { weekday(it).tap() }

            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Wähle mindestens einen Tag.").performScrollTo().assertIsDisplayed()
            heading("Neue Routine").assertIsDisplayed()
        }
        assertEquals(emptyList<Routine>(), savedRoutines())
    }

    @Test
    fun `a routine every X days takes 2 to 365 days, counted from today`() {
        launchApp().use {
            newRoutine("Bettwäsche")
            option("Alle X Tage").tap()

            field("Abstand in Tagen").performScrollTo().performTextReplacement("1")
            compose.onNodeWithText("Speichern").performClick()
            heading("Neue Routine").assertIsDisplayed()

            field("Abstand in Tagen").performTextReplacement("14")
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Alle 14 Tage um 08:00").assertIsDisplayed()
        }
        assertEquals(Repetition.EveryNDays(days = 14, from = LocalDate.parse("2026-10-23")), savedRoutines().single().repetition)
    }

    @Test
    fun `a one-time routine is due on the day chosen in the calendar`() {
        launchApp().use {
            newRoutine("Arzttermin")
            option("Einmalig").tap()

            compose.onNode(hasText("Am") and hasClickAction()).tap()
            // Each day in the calendar reads as a whole date, such as "Freitag, 30. Oktober 2026".
            compose.onNode(hasText("30", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Einmalig am 30.10.2026 um 08:00").assertIsDisplayed()
        }
        assertEquals(Repetition.Once(LocalDate.parse("2026-10-30")), savedRoutines().single().repetition)
    }

    @Test
    fun `the editor shows a routine's times and repetition`() {
        val everyThreeDays = Repetition.EveryNDays(days = 3, from = LocalDate.parse("2026-10-20"))
        app.routine("Tabletten", "08:00", "20:00", repetition = everyThreeDays)

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithText("Tabletten").performClick()

            option("Alle X Tage").assertIsSelected()
            field("Abstand in Tagen").assertTextContains("3")
            compose.onNode(hasText("Ab") and hasClickAction()).assertTextContains("20.10.2026")
            compose.onNodeWithContentDescription("20:00 entfernen").assertExists()
            changeTime(from = "20:00", hour = "21", minute = "00")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Alle 3 Tage um 08:00 und 21:00").assertIsDisplayed()
        }
    }

    @Test
    fun `deleting a routine asks first`() {
        app.routine("Vitamine", "08:00")

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithText("Vitamine").performClick()

            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNodeWithText("Routine löschen?").assertIsDisplayed()
            compose.onNodeWithText("Abbrechen").performClick()
            heading("Routine bearbeiten").assertIsDisplayed()
            assertEquals(1, savedRoutines().size)

            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

            heading("Routinen").assertIsDisplayed()
            compose.onNodeWithText("Noch keine Routinen", substring = true).assertIsDisplayed()
        }
        assertEquals(emptyList<String>(), savedRoutines())
    }

    @Test
    fun `back leaves the editor without saving`() {
        launchApp().use { scenario ->
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            field("Name").performTextInput("Halb fertig")

            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

            heading("Routinen").assertIsDisplayed()
        }
        assertEquals(emptyList<String>(), savedRoutines())
    }

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun heading(title: String) = compose.onNode(isHeading() and hasText(title))

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    /** Scrolls the editor to the node first: its form is longer than the test screen. */
    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    private fun option(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun weekday(label: String) = compose.onNode(hasText(label) and hasClickAction())

    /** Opens the editor for a new routine from the Routinen tab and names it. */
    private fun newRoutine(name: String) {
        tab("Routinen").performClick()
        compose.onNodeWithContentDescription("Routine anlegen").performClick()
        field("Name").performTextInput(name)
    }

    /** Changes the time [from] ("08:00") to another one. */
    private fun changeTime(from: String, hour: String, minute: String) {
        compose.onNode(hasText(from) and hasClickAction()).tap()
        typeTime(hour, minute)
    }

    private fun addTime(hour: String, minute: String) {
        compose.onNodeWithText("Uhrzeit hinzufügen").tap()
        typeTime(hour, minute)
    }

    /** Types the time into the open time dialog and confirms it. */
    private fun typeTime(hour: String, minute: String) {
        val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        fields[0].performTextReplacement(hour)
        fields[1].performTextReplacement(minute)
        compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
    }

    private fun savedRoutines() = runBlocking { app.routines.all().first() }
}
