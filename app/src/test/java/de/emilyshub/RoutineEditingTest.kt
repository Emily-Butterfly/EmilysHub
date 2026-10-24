package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.routines.FollowUps
import de.emilyshub.routines.Priority
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.Routine
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.routines.TimeWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
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
    fun `a one-time routine is only saved while one of its times is still ahead`() {
        app.clock.now = Instant.parse("2026-10-23T10:00:00Z") // 12:00 in Berlin

        launchApp().use {
            newRoutine("Paket abholen")
            option("Einmalig").tap()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Schon vorbei", substring = true).performScrollTo().assertIsDisplayed()
            heading("Neue Routine").assertIsDisplayed()

            changeTime(from = "08:00", hour = "18", minute = "00")
            compose.onNodeWithText("Schon vorbei", substring = true).assertDoesNotExist()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Einmalig am 23.10.2026 um 18:00").assertIsDisplayed()
        }
    }

    @Test
    fun `a routine's priority is normal unless chosen low or high, and shows in the list`() {
        launchApp().use {
            newRoutine("Arzttermin")
            option("Normal").performScrollTo().assertIsSelected()

            option("Hoch").tap()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Hohe Priorität").assertIsDisplayed()
        }
        assertEquals(Priority.High, savedRoutines().single().priority)
    }

    @Test
    fun `the higher a routine's priority, the bolder its name in the list, and larger at high`() {
        app.routine("Arzttermin", "09:00", priority = Priority.High)
        app.routine("Blumen", "10:00", priority = Priority.Low)
        app.routine("Vitamine", "07:30")

        launchApp().use {
            tab("Routinen").performClick()

            compose.assertNamesStandOutByPriority(low = "Blumen", normal = "Vitamine", high = "Arzttermin")
        }
    }

    @Test
    fun `a routine can be caught up on for as many hours as chosen, by default four`() {
        launchApp().use {
            newRoutine("Tabletten")
            field("Zeitfenster in Stunden").performScrollTo().assertTextContains("4")

            field("Zeitfenster in Stunden").performTextReplacement("25")
            compose.onNodeWithText("Speichern").performClick()
            heading("Neue Routine").assertIsDisplayed()

            field("Zeitfenster in Stunden").performTextReplacement("6")
            compose.onNodeWithText("Speichern").performClick()
            heading("Routinen").assertIsDisplayed()
        }
        assertEquals(TimeWindow(hours = 6), savedRoutines().single().window)
    }

    @Test
    fun `a routine can be caught up on until midnight instead, with no hours to choose`() {
        launchApp().use {
            newRoutine("Lesen")

            untilMidnight().performScrollTo().assertIsOff().performClick()

            field("Zeitfenster in Stunden").assertDoesNotExist()
            compose.onNodeWithText("Speichern").performClick()
            heading("Routinen").assertIsDisplayed()
        }
        assertEquals(TimeWindow(untilMidnight = true), savedRoutines().single().window)
    }

    @Test
    fun `the editor shows a routine's time window`() {
        app.routine("Lesen", "21:00", window = TimeWindow(untilMidnight = true))

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithText("Lesen").performClick()

            untilMidnight().performScrollTo().assertIsOn()
        }
    }

    @Test
    fun `a routine reminds again as often and as far apart as chosen, by default three times every 30 minutes`() {
        launchApp().use {
            newRoutine("Tabletten")
            option("3").performScrollTo().assertIsSelected()
            field("Abstand in Minuten").performScrollTo().assertTextContains("30")

            option("5").tap()
            field("Abstand in Minuten").performTextReplacement("10")
            compose.onNodeWithText("Speichern").performClick()

            heading("Routinen").assertIsDisplayed()
        }
        assertEquals(FollowUps(count = 5, everyMinutes = 10), savedRoutines().single().followUps)
    }

    @Test
    fun `follow-ups come from 5 to 120 minutes apart, and with none there is no distance to choose`() {
        launchApp().use {
            newRoutine("Lüften")
            field("Abstand in Minuten").performScrollTo().performTextReplacement("2")
            compose.onNodeWithText("Speichern").performClick()
            heading("Neue Routine").assertIsDisplayed()
            compose.onNodeWithText("Von 5 bis 120").assertIsDisplayed()

            option("Nie").tap()
            field("Abstand in Minuten").assertDoesNotExist()
            compose.onNodeWithText("Speichern").performClick()

            heading("Routinen").assertIsDisplayed()
        }
        assertEquals(FollowUps(count = 0), savedRoutines().single().followUps)
    }

    @Test
    fun `a routine can sound a full-screen alarm instead, which is off by default`() {
        app.fullScreenAlarmsAllowed = true

        launchApp().use {
            newRoutine("Arzttermin")
            fullScreenAlarm().performScrollTo().assertIsOff()

            fullScreenAlarm().performClick()
            compose.onNodeWithText("Speichern").performClick()

            heading("Routinen").assertIsDisplayed()
        }
        assertEquals(true, savedRoutines().single().fullScreen)
    }

    @Test
    fun `the editor shows a routine's times, repetition and follow-ups`() {
        val everyThreeDays = Repetition.EveryNDays(days = 3, from = LocalDate.parse("2026-10-20"))
        val onceAnHourLater = FollowUps(count = 1, everyMinutes = 60)
        app.routine("Tabletten", "08:00", "20:00", repetition = everyThreeDays, followUps = onceAnHourLater)

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithText("Tabletten").performClick()

            option("Alle X Tage").assertIsSelected()
            field("Abstand in Tagen").assertTextContains("3")
            compose.onNode(hasText("Ab") and hasClickAction()).assertTextContains("20.10.2026")
            option("1").performScrollTo().assertIsSelected()
            field("Abstand in Minuten").performScrollTo().assertTextContains("60")
            compose.onNodeWithContentDescription("20:00 entfernen").assertExists()
            changeTime(from = "20:00", hour = "21", minute = "00")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Alle 3 Tage um 08:00 und 21:00").assertIsDisplayed()
        }
    }

    @Test
    fun `a routine can be paused and resumed from the editor, and the list says it is paused`() {
        app.routine("Vitamine", "08:00")

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithText("Vitamine").performClick()
            compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
            compose.onNodeWithText("Pausieren").performClick()

            heading("Routinen").assertIsDisplayed()
            compose.onNodeWithText("Pausiert").assertIsDisplayed()

            compose.onNodeWithText("Vitamine").performClick()
            compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
            compose.onNodeWithText("Fortsetzen").performClick()

            heading("Routinen").assertIsDisplayed()
            compose.onNodeWithText("Pausiert").assertDoesNotExist()
        }
        assertEquals(RoutineStatus.Active, savedRoutines().single().status)
    }

    @Test
    fun `a routine archived from the editor leaves the list, and comes back from the archive`() {
        app.routine("Vitamine", "08:00")
        app.routine("Lesen", "21:00")

        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithText("Vitamine").performClick()
            compose.onNodeWithContentDescription("Weitere Aktionen").performClick()
            compose.onNodeWithText("Archivieren").performClick()

            heading("Routinen").assertIsDisplayed()
            compose.onNodeWithText("Vitamine").assertDoesNotExist()

            compose.onNodeWithText("Archiv (1)").performClick()
            heading("Archiv").assertIsDisplayed()
            compose.onNodeWithText("Vitamine").assertIsDisplayed()
            compose.onNodeWithText("Wiederherstellen").performClick()

            compose.onNodeWithText("Nichts archiviert.").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Vitamine").assertIsDisplayed()
            compose.onNodeWithText("Archiv", substring = true).assertDoesNotExist()
        }
        assertEquals(listOf(RoutineStatus.Active, RoutineStatus.Active), savedRoutines().map { it.status })
    }

    @Test
    fun `a routine can have a checklist, with items added, changed and taken away`() {
        launchApp().use {
            newRoutine("Zähne")
            compose.onNodeWithText("Unterpunkt hinzufügen").tap()
            field("Unterpunkt").performTextInput("Putzen")
            compose.onNodeWithText("Unterpunkt hinzufügen").tap()
            compose.onAllNodes(hasSetTextAction() and hasText("Unterpunkt"))[1].performTextInput("Zahnseide")
            compose.onNodeWithText("Speichern").performClick()
            heading("Routinen").assertIsDisplayed()

            compose.onNodeWithText("Zähne").performClick()
            compose.onNodeWithContentDescription("Putzen entfernen").tap()
            compose.onNodeWithText("Speichern").performClick()
            heading("Routinen").assertIsDisplayed()
        }
        val teeth = savedRoutines().single()
        assertEquals(listOf("Zahnseide"), runBlocking { app.routines.checklist(teeth.id) }.map { it.text })
    }

    @Test
    fun `a routine can have several tags, also one made in the editor, and the list shows them`() {
        app.tag("Gesundheit")
        app.tag("Arbeit")

        launchApp().use {
            newRoutine("Tabletten")
            option("Gesundheit").tap()
            compose.onNodeWithText("Neuer Tag").tap()
            field("Name des Tags").performTextInput("Morgens")
            compose.onNodeWithText("Anlegen").performClick()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Gesundheit · Morgens").assertIsDisplayed()

            compose.onNodeWithText("Tabletten").performClick()
            option("Gesundheit").performScrollTo().assertIsSelected()
            option("Arbeit").assertIsNotSelected()
        }
    }

    @Test
    fun `the routine list can be filtered by tag`() {
        val health = app.tag("Gesundheit")
        app.tag("Arbeit")
        app.routine("Tabletten", "08:00", tags = setOf(health))
        app.routine("Lesen", "21:00")

        launchApp().use {
            tab("Routinen").performClick()
            option("Gesundheit").performClick()

            compose.onNodeWithText("Tabletten").assertIsDisplayed()
            compose.onNodeWithText("Lesen").assertDoesNotExist()

            option("Arbeit").performClick()
            compose.onNodeWithText("Keine Routine mit dem Tag „Arbeit“.").assertIsDisplayed()

            option("Alle").performClick()
            compose.onNodeWithText("Tabletten").assertIsDisplayed()
            compose.onNodeWithText("Lesen").assertIsDisplayed()
        }
    }

    @Test
    fun `the tag filter stays while a routine is changed, and on return from another tab`() {
        val health = app.tag("Gesundheit")
        app.routine("Tabletten", "08:00", tags = setOf(health))
        app.routine("Lesen", "21:00")

        launchApp().use {
            tab("Routinen").performClick()
            option("Gesundheit").performClick()

            compose.onNodeWithText("Tabletten").performClick()
            compose.onNodeWithText("Speichern").performClick()
            tab("Heute").performClick()
            tab("Routinen").performClick()

            option("Gesundheit").assertIsSelected()
            compose.onNodeWithText("Lesen").assertDoesNotExist()
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


    private fun untilMidnight() = compose.onNode(isToggleable() and hasText("Bis Mitternacht"))

    private fun fullScreenAlarm() = compose.onNode(isToggleable() and hasText("Vollbild-Alarm"))

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

    private fun savedRoutines() = runBlocking { app.routines.inUse().first() }
}
