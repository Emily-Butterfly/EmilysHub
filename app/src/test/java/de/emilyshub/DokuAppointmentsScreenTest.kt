package de.emilyshub

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.doku.PatientDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * The patients' appointments (SPEC.md §14): added in the tab Termine, filled in by the template, documented in
 * Markdown, changed and deleted; only to read in the archive; found by the Doku's search.
 */
@RunWith(AndroidJUnit4::class)
class DokuAppointmentsScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    /** The Doku set up and open on its start page. */
    private fun ComposeTestRule.openNewDoku() {
        openDoku()
        setUpDoku()
    }

    /** A patient added behind the open Doku's back, with an appointment of [text] if given; their ids. */
    private fun patient(name: String, text: String? = null, date: String? = null, time: String? = null): Pair<Long, Long?> = runBlocking {
        val id = checkNotNull(app.patients.create(PatientDraft(name)))
        val appointment = text?.let {
            checkNotNull(app.appointments.create(id)).also { appointment ->
                app.appointments.setText(appointment, it)
                date?.let { app.appointments.setDate(appointment, LocalDate.parse(it)) }
                time?.let { app.appointments.setTime(appointment, LocalTime.parse(it)) }
            }
        }
        id to appointment
    }

    /** That what [upper] matches is shown above what [lower] does. */
    private fun ComposeTestRule.assertAbove(upper: SemanticsMatcher, lower: SemanticsMatcher) {
        val top = { matcher: SemanticsMatcher -> onNode(matcher).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(top(upper) < top(lower))
    }

        private fun ComposeTestRule.openAppointments(name: String) {
        onNodeWithText(name).performClick()
        onNode(isSelectable() and hasText("Termine")).performClick()
    }

    @Test
    fun `a new appointment starts with the template, and its line shows the first that is no heading`() {
        launchApp().use {
            compose.openNewDoku()
            compose.onNodeWithContentDescription("Menü der Dokumentation").performClick()
            compose.onNodeWithText("Termin-Vorlage").performClick()
            compose.field("Termin-Vorlage").performTextInput("## Befund\n\n## Maßnahmen")
            compose.onNodeWithText("Fertig").performClick()
            patient("Frau M.")

            compose.openAppointments("Frau M.")
            compose.onNodeWithText("Noch keine Termine").assertIsDisplayed()
            compose.onNodeWithText("+ Termin").performClick()

            compose.field("Dokumentation").assertTextContains("## Befund\n\n## Maßnahmen", substring = true)
            compose.onNodeWithText("23.10.2026").assertIsDisplayed()
            compose.field("Dokumentation").performTextReplacement("## Befund\nSchulter **frei**\n\n## Maßnahmen")
            compose.onNodeWithText("Fertig").performClick()

            compose.onNode(isSelectable() and hasText("Termine")).assertIsSelected()
            compose.onNode(hasText("23.10.2026") and hasText("Schulter frei")).assertIsDisplayed()
        }
    }

    @Test
    fun `a new appointment left untouched goes again, one written in stays`() {
        launchApp().use {
            compose.openNewDoku()
            val (patient, _) = patient("Frau M.")

            compose.openAppointments("Frau M.")
            compose.onNodeWithText("+ Termin").performClick()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Noch keine Termine").assertIsDisplayed()

            compose.onNodeWithText("+ Termin").performClick()
            compose.field("Dokumentation").performTextInput("Mobilisation")
            compose.onNodeWithText("Fertig").performClick()
            compose.onNode(hasText("23.10.2026") and hasText("Mobilisation")).assertIsDisplayed()
            assertEquals(1, runBlocking { app.appointments.of(patient) }?.size)
        }
    }

    @Test
    fun `an appointment's text and time are changed, and the list keeps the latest first`() {
        launchApp().use {
            compose.openNewDoku()
            val (id, older) = patient("Frau M.", text = "Früher", date = "2026-10-01")
            val appointment = runBlocking { checkNotNull(app.appointments.create(id)) }
            runBlocking { app.appointments.setTime(appointment, LocalTime.parse("09:30")) }

            compose.openAppointments("Frau M.")
            compose.assertAbove(hasText("23.10.2026, 09:30") and hasText("(leer)"), hasText("01.10.2026") and hasText("Früher"))
            compose.onNode(hasText("23.10.2026, 09:30") and hasText("(leer)")).performClick()
            compose.onNodeWithText("09:30").assertIsDisplayed()
            compose.onNodeWithText("Ohne Uhrzeit").performClick()
            compose.onNodeWithText("ohne").assertIsDisplayed()
            compose.field("Dokumentation").performTextInput("Mobilisation")
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNode(hasText("23.10.2026") and hasText("Mobilisation")).assertIsDisplayed()
            assertEquals(null, runBlocking { app.appointments.get(appointment) }?.time)
            assertEquals("Früher", runBlocking { app.appointments.get(checkNotNull(older)) }?.text)
        }
    }

    @Test
    fun `deleting an appointment asks first`() {
        launchApp().use {
            compose.openNewDoku()
            val (_, appointment) = patient("Frau M.", text = "Mobilisation")

            compose.openAppointments("Frau M.")
            compose.onNodeWithText("Mobilisation").performClick()
            compose.onNodeWithContentDescription("Menü des Termins").performClick()
            compose.onNodeWithText("Löschen").performClick()
            compose.onNodeWithText("Termin vom 23.10.2026 löschen?").assertIsDisplayed()
            compose.onNodeWithText("Löschen").performClick()

            compose.onNodeWithText("Noch keine Termine").assertIsDisplayed()
            assertEquals(null, runBlocking { app.appointments.get(checkNotNull(appointment)) })
        }
    }

    @Test
    fun `what is typed in a new appointment is saved as the Doku locks`() {
        launchApp().use {
            compose.openNewDoku()
            patient("Frau M.")
            compose.openAppointments("Frau M.")
            compose.onNodeWithText("+ Termin").performClick()
            compose.field("Dokumentation").performTextInput("Wärme, dann Dehnen")

            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.openDoku()
            compose.unlockDoku()

            compose.onNodeWithText("letzter Termin 23.10.2026").assertIsDisplayed()
            compose.openAppointments("Frau M.")
            compose.onNodeWithText("Wärme, dann Dehnen").assertIsDisplayed()
        }
    }

    @Test
    fun `a closed patient's appointments are only to read`() {
        launchApp().use {
            compose.openNewDoku()
            val (id, _) = patient("Frau M.", text = "Mobilisation")
            runBlocking { app.patients.close(id) }

            compose.onNodeWithContentDescription("Menü der Dokumentation").performClick()
            compose.onNodeWithText("Archiv").performClick()
            compose.openAppointments("Frau M.")
            compose.onNodeWithText("+ Termin").assertDoesNotExist()
            compose.onNodeWithText("Mobilisation").performClick()

            compose.onNodeWithText("23.10.2026").assertIsDisplayed()
            compose.onNodeWithText("Fertig").assertDoesNotExist()
            compose.onNodeWithText("Bearbeiten").assertDoesNotExist()
            compose.onNodeWithContentDescription("Menü des Termins").assertDoesNotExist()
        }
    }

    @Test
    fun `the search finds an appointment's text and opens it, back leading to the patient's appointments`() {
        launchApp().use {
            compose.openNewDoku()
            patient("Frau M.", text = "Knie geschwollen", date = "2026-10-20")

            compose.field("Patienten suchen").performTextInput("knie")
            compose.onNode(hasText("Frau M.") and hasText("Termin 20.10.2026")).performClick()

            compose.field("Dokumentation").assertTextContains("Knie geschwollen")
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(isSelectable() and hasText("Termine")).assertIsSelected()
            compose.onNode(hasText("20.10.2026") and hasText("Knie geschwollen")).assertIsDisplayed()
        }
    }
}
