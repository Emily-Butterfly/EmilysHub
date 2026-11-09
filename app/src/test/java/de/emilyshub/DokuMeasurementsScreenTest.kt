package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.doku.Direction
import de.emilyshub.doku.MeasurementDraft
import de.emilyshub.doku.PatientDraft
import de.emilyshub.doku.TestDraft
import de.emilyshub.doku.ValueDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The measurements at appointments (SPEC.md §14): taken and changed in an appointment, with the live sum; the card of
 * each test measured in the patient's tab Tests, its history with charts and table, and a row of it opening the
 * appointment.
 */
@RunWith(AndroidJUnit4::class)
class DokuMeasurementsScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    private val barthel = TestDraft(
        "Barthel-Index",
        listOf(ValueDraft(name = "Essen", unit = "Punkte", min = "0", max = "10"), ValueDraft(name = "Baden", unit = "Punkte", min = "0", max = "5")),
        sum = true,
        sumDirection = Direction.HigherIsBetter,
    )

    /** The Doku open, with the test of [draft] and patient "Frau M."; their ids. */
    private fun ComposeTestRule.openWith(draft: TestDraft = barthel): Pair<Long, Long> {
        openDoku()
        setUpDoku()
        return runBlocking { checkNotNull(app.tests.save(null, draft)) to checkNotNull(app.patients.create(PatientDraft("Frau M."))) }
    }

    /** An appointment of [patient] on [date], measuring [testId] with [typed] by value name. */
    private fun measured(patient: Long, testId: Long, date: String, vararg typed: Pair<String, String>, remark: String = ""): Long = runBlocking {
        val appointment = checkNotNull(app.appointments.create(patient)).also { app.appointments.setDate(it, LocalDate.parse(date)) }
        val form = checkNotNull(app.measurements.newForm(appointment, testId))
        val ids = form.fields.associate { it.name to it.id }
        app.measurements.save(appointment, null, form, MeasurementDraft(typed.associate { (name, number) -> ids.getValue(name) to number }, remark))
        appointment
    }

    private fun ComposeTestRule.openTab(tab: String) {
        onNodeWithText("Frau M.").performClick()
        onNode(isSelectable() and hasText(tab)).performClick()
    }

    @Test
    fun `a measurement is taken in an appointment, the sum counting along, and changed`() {
        launchApp().use {
            val (_, patient) = compose.openWith()
            runBlocking { app.appointments.create(patient) }
            compose.openTab("Termine")
            compose.onNodeWithText("(leer)").performClick()

            compose.onNodeWithText("+ Test").performScrollTo().performClick()
            compose.onNodeWithText("Barthel-Index").performClick()
            compose.field("Essen (Punkte)").performTextInput("11")
            compose.onNodeWithText("Außerhalb des Bereichs: 0 bis 10").assertIsDisplayed()
            compose.field("Essen (Punkte)").performTextReplacement("10")
            compose.onNodeWithText("Summe: –").assertIsDisplayed()
            compose.field("Baden (Punkte)").performTextInput("5")
            compose.onNodeWithText("Summe: 15").assertIsDisplayed()
            compose.field("Bemerkung (optional)").performTextInput("mit Hilfe beim Aufstehen")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Essen 10 Punkte · Baden 5 Punkte · Summe: 15", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("mit Hilfe beim Aufstehen", substring = true).assertIsDisplayed()

            compose.onNodeWithText("Barthel-Index").performScrollTo().performClick()
            compose.field("Baden (Punkte)").performTextReplacement("")
            compose.onNodeWithText("Summe: –").assertIsDisplayed()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Essen 10 Punkte · Baden – · Summe: –", substring = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `a measurement needs one value, and is deleted after asking`() {
        launchApp().use {
            val (test, patient) = compose.openWith()
            measured(patient, test, "2026-10-23", "Essen" to "10")
            compose.openTab("Termine")
            compose.onNodeWithText("(leer)").performClick()
            compose.onNodeWithText("Barthel-Index").performScrollTo().performClick()

            compose.field("Essen (Punkte)").performTextReplacement("")
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Trag mindestens einen Wert ein.").assertIsDisplayed()
            compose.onNodeWithContentDescription("Menü der Messung").performClick()
            compose.onNodeWithText("Löschen").performClick()
            compose.onNodeWithText("Messung „Barthel-Index“ löschen?").assertIsDisplayed()
            compose.onNodeWithText("Endgültig löschen").performClick()

            compose.onNodeWithText("Essen", substring = true).assertDoesNotExist()
            assertEquals(0, runBlocking { checkNotNull(app.measurements.at(checkNotNull(app.appointments.of(patient)).single().id)).size })
        }
    }

    @Test
    fun `deleting an appointment names its measurements`() {
        launchApp().use {
            val (test, patient) = compose.openWith()
            measured(patient, test, "2026-10-23", "Essen" to "10")
            compose.openTab("Termine")
            compose.onNodeWithText("(leer)").performClick()

            compose.onNodeWithContentDescription("Menü des Termins").performClick()
            compose.onNodeWithText("Löschen").performClick()

            compose.onNodeWithText("Mit seiner Dokumentation und 1 Messung.").assertIsDisplayed()
        }
    }

    @Test
    fun `the tab Tests has a card for each test measured, which opens its history, whose rows open their appointment`() {
        launchApp().use {
            val (test, patient) = compose.openWith()
            measured(patient, test, "2026-09-01", "Essen" to "5", "Baden" to "0", remark = "Eingang")
            measured(patient, test, "2026-10-01", "Essen" to "10", "Baden" to "5")

            compose.openTab("Tests")

            compose.onNodeWithText("zuletzt am 01.10.2026").assertIsDisplayed()
            compose.onNodeWithText("Summe: 15 Punkte ▲ +10 Punkte besser seit 01.09.2026").assertIsDisplayed()
            compose.onNodeWithText("Barthel-Index").performClick()

            compose.onNodeWithText("Frau M.").assertIsDisplayed()
            compose.onNodeWithContentDescription("Verlauf von Essen", substring = true).assertIsDisplayed()
            val history = compose.onNode(hasScrollToNodeAction())
            history.performScrollToNode(hasContentDescription("Verlauf von Summe", substring = true))
            compose.onNodeWithContentDescription("Verlauf von Summe", substring = true).assertIsDisplayed()
            val row = hasText("01.09.2026") and hasText("Eingang") and hasClickAction()
            history.performScrollToNode(row)
            compose.onNode(row).performClick()

            compose.onNodeWithText("01.09.2026").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Barthel-Index").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(isSelectable() and hasText("Tests")).assertIsSelected()
        }
    }

    @Test
    fun `the search finds a measurement's remark and opens its appointment`() {
        launchApp().use {
            val (test, patient) = compose.openWith()
            measured(patient, test, "2026-10-20", "Essen" to "10", remark = "nach Treppensteigen")

            compose.field("Patienten suchen").performTextInput("treppen")
            compose.onNode(hasText("Frau M.") and hasText("Termin 20.10.2026")).performClick()

            compose.onNodeWithText("nach Treppensteigen", substring = true).performScrollTo().assertIsDisplayed()
        }
    }
}
