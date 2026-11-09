package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.doku.MeasurementDraft
import de.emilyshub.doku.PatientDraft
import de.emilyshub.doku.TestDraft
import de.emilyshub.doku.ValueDraft
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Doku's tests (SPEC.md §14), set up in its menu: one with several values and a sum, its values reordered, a
 * measured value retired with the hint how many measurements it touches, archived and deleted.
 */
@RunWith(AndroidJUnit4::class)
class DokuTestsScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    private val barthel = TestDraft(
        "Barthel-Index",
        listOf(ValueDraft(name = "Essen", unit = "Punkte", min = "0", max = "10"), ValueDraft(name = "Baden", unit = "Punkte", min = "0", max = "5")),
        sum = true,
    )

    private fun ComposeTestRule.openTests() {
        openDoku()
        setUpDoku()
        onNodeWithContentDescription("Menü der Dokumentation").performClick()
        onNodeWithText("Tests").performClick()
    }

    /** The [index]th text field labelled [label], scrolled to. */
    private fun ComposeTestRule.nthField(label: String, index: Int): SemanticsNodeInteraction =
        onAllNodes(hasSetTextAction() and hasText(label))[index].performScrollTo()

    private fun ComposeTestRule.tapScrolled(text: String) {
        onNodeWithText(text).performScrollTo().performClick()
    }

    /** A test saved behind the page's back, measured once for a patient; its id. */
    private fun measured(draft: TestDraft): Long = runBlocking {
        val test = checkNotNull(app.tests.save(null, draft))
        val patient = checkNotNull(app.patients.create(PatientDraft("Frau M.")))
        val appointment = checkNotNull(app.appointments.create(patient))
        val form = checkNotNull(app.measurements.newForm(appointment, test))
        app.measurements.save(appointment, null, form, MeasurementDraft(mapOf(form.fields.last().id to "5")))
        test
    }

    @Test
    fun `a test is added with several values and a sum`() {
        launchApp().use {
            compose.openTests()
            compose.onNodeWithText("Noch keine Tests").assertIsDisplayed()
            compose.onNodeWithText("+ Test").performClick()

            compose.field("Name des Tests").performTextInput("Barthel-Index")
            compose.nthField("Name des Werts", 0).performTextInput("Essen")
            compose.nthField("Einheit (optional)", 0).performTextInput("Punkte")
            compose.nthField("Bereich von", 0).performTextInput("0")
            compose.nthField("bis", 0).performTextInput("10")
            compose.tapScrolled("+ Wert")
            compose.nthField("Name des Werts", 1).performTextInput("Baden")
            compose.nthField("bis", 1).performTextInput("5")
            compose.tapScrolled("Summe bilden")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNode(hasText("Barthel-Index") and hasText("mit Summe")).assertIsDisplayed()
            val details = runBlocking { checkNotNull(app.tests.details(checkNotNull(app.tests.list()).active.single().id)) }
            assertEquals(listOf("Essen", "Baden"), details.values.map { it.name })
            assertEquals(listOf(1000L, 500L), details.values.map { it.max })
            assertEquals(true, details.formsSum)
        }
    }

    @Test
    fun `the name is asked for, and a range from high to low is refused`() {
        launchApp().use {
            compose.openTests()
            compose.onNodeWithText("+ Test").performClick()
            compose.nthField("Bereich von", 0).performTextInput("10")
            compose.nthField("bis", 0).performTextInput("5")

            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Gib dem Test einen Namen.").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("„von“ ist größer als „bis“.").performScrollTo().assertIsDisplayed()
            assertEquals(emptyList<Any>(), runBlocking { app.tests.list() }?.active)
        }
    }

    @Test
    fun `the values are reordered`() {
        launchApp().use {
            compose.openTests()
            runBlocking { app.tests.save(null, barthel) }
            compose.onNodeWithText("Barthel-Index").performClick()

            compose.onNodeWithContentDescription("Baden nach oben").performScrollTo().performClick()
            compose.onNodeWithText("Speichern").performClick()

            val id = runBlocking { checkNotNull(app.tests.list()).active.single().id }
            assertEquals(listOf("Baden", "Essen"), runBlocking { checkNotNull(app.tests.details(id)) }.values.map { it.name })
        }
    }

    @Test
    fun `a measured value removed is retired, and saving says how many measurements it touches`() {
        launchApp().use {
            compose.openTests()
            val id = measured(barthel)
            compose.onNodeWithText("Barthel-Index").performClick()

            // The second value, "Baden", was measured.
            compose.onAllNodesWithText("Entfernen")[1].performScrollTo().performClick()
            compose.onNodeWithText("stillgelegt: bei neuen Messungen nicht mehr abgefragt").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Wieder aktivieren").assertIsDisplayed()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Betrifft 1 Messung bei 1 Patienten.").assertIsDisplayed()
            compose.onAllNodesWithText("Speichern")[1].performClick()

            assertEquals(listOf(false, true), runBlocking { checkNotNull(app.tests.details(id)) }.values.map { it.retired })
        }
    }

    @Test
    fun `a test is archived, restored, and deleted for good after asking`() {
        launchApp().use {
            compose.openTests()
            measured(barthel)
            compose.onNodeWithText("Barthel-Index").performClick()
            compose.onNodeWithContentDescription("Menü des Tests").performClick()
            compose.onNodeWithText("Archivieren").performClick()

            compose.onNodeWithText("Archiviert").assertIsDisplayed()
            compose.onNodeWithText("Barthel-Index").performClick()
            compose.onNodeWithText("Archiviert am 23.10.2026: für neue Messungen nicht wählbar").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()
            // In the section "Archiviert" itself.
            compose.onNodeWithText("Wiederherstellen").performClick()
            compose.onNodeWithText("Archiviert").assertDoesNotExist()

            compose.onNodeWithText("Barthel-Index").performClick()
            compose.onNodeWithContentDescription("Menü des Tests").performClick()
            compose.onNodeWithText("Endgültig löschen").performClick()
            compose.onNodeWithText("„Barthel-Index“ endgültig löschen?").assertIsDisplayed()
            compose.onNodeWithText("Löscht 1 Messung bei 1 Patienten.").assertIsDisplayed()
            compose.onNodeWithText("Endgültig löschen").performClick()

            compose.onNodeWithText("Noch keine Tests").assertIsDisplayed()
        }
    }
}
