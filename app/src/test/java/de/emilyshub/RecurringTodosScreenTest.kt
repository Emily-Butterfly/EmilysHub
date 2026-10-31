package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.todos.Due
import de.emilyshub.todos.Recurrence
import de.emilyshub.todos.RecurrenceEnd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/**
 * To-dos that come again (SPEC.md §5): set in the editor once due, said in the list and on Heute, and ticked off into
 * the archive with the next one there at once, which undoing takes away again.
 */
@RunWith(AndroidJUnit4::class)
class RecurringTodosScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `a to-do comes again only once it is due, and then monthly on its day, as the list says`() {
        launchApp().use {
            openTodos()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Titel").performTextReplacement("Miete überweisen")
            compose.onNodeWithText("Wiederholung").assertDoesNotExist()

            option("Datum").performClick()
            option("Monatlich am 23.").tap()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Monatlich am 23.").assertIsDisplayed()
        }
        val saved = runBlocking { app.todos.open().first().single().todo }
        assertEquals(Recurrence.Monthly(23), saved.recurrence)
    }

    @Test
    fun `every so many days from the day it is done, as chosen in the editor`() {
        app.todo("Blumen düngen", due = Due.On(today))

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Blumen düngen").performClick()
            option("Alle X Tage").tap()
            field("Abstand in Tagen").performScrollTo().performTextReplacement("14")
            compose.onNode(hasText("Ab Erledigung zählen") and hasClickAction()).tap()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Alle 14 Tage ab Erledigung").assertIsDisplayed()
        }
        assertEquals(Recurrence.EveryNDays(14, fromDone = true), runBlocking { app.todos.open().first().single().todo.recurrence })
    }

    @Test
    fun `put off to another day, a to-do coming yearly keeps its date, unless the new one is chosen for the series`() {
        app.todo("Geburtstag Oma", due = Due.On(LocalDate.parse("2026-10-30")), recurrence = Recurrence.Yearly(MonthDay.of(10, 30)))

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Jährlich am 30.10.").assertIsDisplayed()
            compose.onNodeWithText("Geburtstag Oma").performClick()
            pickDay("28")
            option("Jährlich am 30.10.").performScrollTo().assertIsSelected()
            option("Jährlich am 28.10.").tap()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Jährlich am 28.10.").assertIsDisplayed()
        }
    }

    @Test
    fun `chosen for a new to-do, monthly follows the day it is then put on`() {
        launchApp().use {
            openTodos()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Titel").performTextReplacement("Miete überweisen")
            option("Datum").performClick()
            option("Monatlich am 23.").tap()
            pickDay("28")

            option("Monatlich am 28.").performScrollTo().assertIsSelected()
            option("Monatlich am 23.").assertDoesNotExist()
        }
    }

    @Test
    fun `ticked off in the list, the next is there at once, and undoing takes it away again`() {
        val mondays = Recurrence.Weekdays(setOf(DayOfWeek.MONDAY))
        app.todo("Müll rausstellen", due = Due.On(LocalDate.parse("2026-10-26")), recurrence = mondays)

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Jede Woche Mo").assertIsDisplayed()
            compose.onNodeWithText("Fällig: 26.10.2026").assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("„Müll rausstellen“ abhaken").performClick()
            compose.mainClock.advanceTimeBy(SNACKBAR_SHOWS)
            compose.onNodeWithText("Fällig: 02.11.2026").assertIsDisplayed()
            compose.onNodeWithText("Rückgängig").performClick()
            compose.mainClock.autoAdvance = true

            compose.onNodeWithText("Fällig: 26.10.2026").assertIsDisplayed()
            compose.onNodeWithText("Fällig: 02.11.2026").assertDoesNotExist()
        }
    }

    @Test
    fun `Heute says that a to-do comes again`() {
        app.todo("Pflanzen gießen", due = Due.On(today), recurrence = Recurrence.Daily)

        launchApp().use {
            compose.onNodeWithText("Pflanzen gießen").assertIsDisplayed()
            compose.onNodeWithText("Täglich").assertIsDisplayed()
        }
    }

    @Test
    fun `a series ending after so many times, chosen in the editor, says how many are left and the last one`() {
        val saturdays = Recurrence.Weekdays(setOf(DayOfWeek.SATURDAY))
        app.todo("Markt", due = Due.On(LocalDate.parse("2026-10-24")), recurrence = saturdays)

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Markt").performClick()
            option("Kein Ende").performScrollTo().assertIsSelected()
            option("Nach einer Anzahl").tap()
            field("Anzahl insgesamt").performScrollTo().performTextReplacement("2")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Jede Woche Sa · noch 2 von 2").assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("„Markt“ abhaken").performClick()
            compose.mainClock.advanceTimeBy(SNACKBAR_SHOWS)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText("Jede Woche Sa · letztes Mal").assertIsDisplayed()
        }
        assertEquals(RecurrenceEnd.AfterTimes(2), runBlocking { app.todos.open().first().single().todo.end })
    }

    @Test
    fun `no count below one is saved`() {
        app.todo("Markt", due = Due.On(today), recurrence = Recurrence.Daily)

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Markt").performClick()
            option("Nach einer Anzahl").tap()
            field("Anzahl insgesamt").performScrollTo().performTextReplacement("0")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Von 1 bis 999").assertIsDisplayed()
        }
        assertEquals(RecurrenceEnd.Never, runBlocking { app.todos.open().first().single().todo.end })
    }

    @Test
    fun `a series ending on a day, picked from the due date on, says until when, on Heute too`() {
        app.todo("Pflanzen gießen", due = Due.On(today), recurrence = Recurrence.Daily)

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Pflanzen gießen").performClick()
            option("An einem Datum").tap()
            // A month on at first, then the day picked in that month.
            compose.onNodeWithText("23.11.2026").performScrollTo().assertIsDisplayed()
            pickDay("15", field = "Letzter Tag")
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Täglich · bis 15.11.").assertIsDisplayed()

            compose.onNode(hasText("Heute") and hasClickAction()).performClick()
            compose.onNodeWithText("Täglich · bis 15.11.").assertIsDisplayed()
        }
        assertEquals(RecurrenceEnd.OnDate(LocalDate.parse("2026-11-15")), runBlocking { app.todos.open().first().single().todo.end })
    }

    @Test
    fun `without a repetition, there is no end to choose`() {
        app.todo("Steuern", due = Due.On(today))

        launchApp().use {
            openTodos()
            compose.onNodeWithText("Steuern").performClick()
            compose.onNodeWithText("Endet").assertDoesNotExist()
        }
    }

    private fun openTodos() = compose.onNode(hasText("To-dos") and hasClickAction()).performClick()

    /**
     * Picks the day in the calendar of the date [field], where each day reads as a whole date, such as "Mittwoch, 28.
     * Oktober 2026".
     */
    private fun pickDay(day: String, field: String = "Am") {
        compose.onNode(hasText(field) and hasClickAction()).tap()
        compose.onNode(hasText(day, substring = true) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun option(label: String) = compose.onNode(isSelectable() and hasText(label))

    /** Scrolls the editor to the node first: its form is longer than the test screen. */
    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    private companion object {
        const val SNACKBAR_SHOWS = 1_000L
    }
}
