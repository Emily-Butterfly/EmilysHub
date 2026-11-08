package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.doku.PatientDraft
import de.emilyshub.doku.PatientField
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Doku's patients (SPEC.md §14): added from the start page, their page with three tabs and four texts in Markdown,
 * closed into the archive, only to read there, restored, deleted for good, and the hint to delete them after a year.
 */
@RunWith(AndroidJUnit4::class)
class DokuPatientsScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    /** The Doku set up and open on its start page. */
    private fun ComposeTestRule.openNewDoku() {
        openDoku()
        setUpDoku()
    }

    private fun ComposeTestRule.addPatient(name: String, birthYear: String = "") {
        onNodeWithText("+ Patient").performClick()
        field("Name / Kürzel").performTextInput(name)
        if (birthYear.isNotEmpty()) field("Geburtsjahr (optional)").performTextInput(birthYear)
        onNodeWithText("Anlegen").performClick()
    }

    /** A patient added behind the open Doku's back, as by another of its screens. */
    private fun patient(name: String, diagnosis: String = ""): Long = runBlocking {
        val id = checkNotNull(app.patients.create(PatientDraft(name)))
        if (diagnosis.isNotEmpty()) app.patients.setText(id, PatientField.Diagnosis, diagnosis)
        id
    }

    @Test
    fun `a patient is added with name and year of birth, and their page opens on the overview`() {
        launchApp().use {
            compose.openNewDoku()
            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()

            compose.addPatient("Frau M.", "1983")

            compose.onNodeWithText("Frau M.").assertIsDisplayed()
            compose.onNodeWithText("Jg. 1983 (≈ 43 Jahre)").assertIsDisplayed()
            compose.onNode(isSelectable() and hasText("Übersicht")).assertIsSelected()
            listOf("Diagnose", "Anamnese", "Therapeutische Zielsetzung", "Sonstiges").forEach { label ->
                compose.overviewField(label, "–").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `the name is asked for, and the year of birth checked`() {
        launchApp().use {
            compose.openNewDoku()

            compose.addPatient("", "1899")

            compose.onNodeWithText("Gib einen Namen oder ein Kürzel ein.").assertIsDisplayed()
            compose.onNodeWithText("Ein Jahr von 1900 bis 2026").assertIsDisplayed()
        }
    }

    @Test
    fun `the tabs Termine and Tests show that there is nothing yet`() {
        launchApp().use {
            compose.openNewDoku()
            compose.addPatient("Frau M.")

            compose.onNode(isSelectable() and hasText("Termine")).performClick()
            compose.onNodeWithText("Noch keine Termine").assertIsDisplayed()
            compose.onNode(isSelectable() and hasText("Tests")).performClick()
            compose.onNodeWithText("Noch keine Tests").assertIsDisplayed()
        }
    }

    @Test
    fun `a text is written in Markdown and shows as it reads`() {
        launchApp().use {
            compose.openNewDoku()
            compose.addPatient("Frau M.")

            compose.onNodeWithText("Diagnose").performClick()
            compose.field("Diagnose").performTextInput("Impingement **Schulter links**")
            compose.onNodeWithText("Vorschau").performClick()
            compose.onNodeWithText("Impingement Schulter links").assertIsDisplayed()
            compose.onNodeWithText("Fertig").performClick()

            compose.overviewField("Diagnose", "Impingement Schulter links").assertIsDisplayed()
            compose.overviewField("Anamnese", "–").assertIsDisplayed()
        }
    }

    @Test
    fun `what is typed is saved as the Doku locks, and the next unlock starts on the start page`() {
        launchApp().use {
            compose.openNewDoku()
            compose.addPatient("Frau M.")
            compose.onNodeWithText("Anamnese").performClick()
            compose.field("Anamnese").performTextInput("Seit März")

            compose.onNode(isSelectable() and hasText("Heute")).performClick()
            compose.openDoku()
            compose.unlockDoku()

            compose.onNodeWithText("+ Patient").assertIsDisplayed()
            compose.onNodeWithText("Frau M.").performClick()
            compose.onNodeWithText("Seit März").assertIsDisplayed()
        }
    }

    @Test
    fun `back from writing a text saves it, and back again leads to the start page`() {
        launchApp().use {
            compose.openNewDoku()
            compose.addPatient("Frau M.")
            compose.overviewField("Sonstiges", "–").performClick()
            compose.field("Sonstiges").performTextInput("Ruft lieber an")

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.overviewField("Sonstiges", "Ruft lieber an").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNodeWithText("+ Patient").assertIsDisplayed()
        }
    }

    @Test
    fun `name and year of birth are changed by the pencil`() {
        launchApp().use {
            compose.openNewDoku()
            compose.addPatient("Frau M.", "1983")

            compose.onNodeWithContentDescription("Name und Geburtsjahr ändern").performClick()
            compose.field("Name / Kürzel").performTextReplacement("Frau Me.")
            compose.field("Geburtsjahr (optional)").performTextReplacement("")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Frau Me.").assertIsDisplayed()
            compose.onNodeWithText("Jg.", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `the start page lists the active patients added last first, or by name, with no appointment yet`() {
        launchApp().use {
            compose.openNewDoku()
            patient("Zoe")
            app.clock.now = app.clock.now.plusSeconds(60)
            patient("Anna")
            compose.onNode(isSelectable() and hasText("Name")).performClick()
            compose.onNode(isSelectable() and hasText("Zuletzt behandelt")).performClick()

            compose.assertOrder("Anna", "Zoe")
            assertEquals(2, compose.onAllNodesWithText("noch kein Termin").fetchSemanticsNodes().size)
            compose.onNode(isSelectable() and hasText("Name")).performClick()
            compose.assertOrder("Anna", "Zoe")
            app.clock.now = app.clock.now.plusSeconds(60)
            patient("Ben")
            compose.assertOrder("Anna", "Ben", "Zoe")
        }
    }

    @Test
    fun `a closed patient goes to the archive with the day, only to read there, and comes back by restoring`() {
        launchApp().use {
            compose.openNewDoku()
            compose.addPatient("Frau M.")

            compose.onNodeWithContentDescription("Menü des Patienten").performClick()
            compose.onNodeWithText("Abschließen").performClick()
            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()

            compose.onNodeWithContentDescription("Menü der Dokumentation").performClick()
            compose.onNodeWithText("Archiv").performClick()
            compose.onNodeWithText("abgeschlossen am 23.10.2026").assertIsDisplayed()
            compose.onNodeWithText("Frau M.").performClick()
            compose.onNodeWithText("Abgeschlossen am 23.10.2026 – nur zum Lesen").assertIsDisplayed()
            compose.onNodeWithContentDescription("Name und Geburtsjahr ändern").assertDoesNotExist()
            compose.onNodeWithText("Diagnose").performClick()
            compose.onNodeWithText("Fertig").assertDoesNotExist()

            compose.onNodeWithContentDescription("Menü des Patienten").performClick()
            compose.onNodeWithText("Wiederherstellen").performClick()
            compose.onNodeWithContentDescription("Name und Geburtsjahr ändern").assertIsDisplayed()
        }
    }

    @Test
    fun `deleting for good asks first, naming the patient and the backups`() {
        launchApp().use {
            compose.openNewDoku()
            compose.addPatient("Frau M.")

            compose.onNodeWithContentDescription("Menü des Patienten").performClick()
            compose.onNodeWithText("Endgültig löschen").performClick()
            compose.onNodeWithText("„Frau M.“ endgültig löschen?").assertIsDisplayed()
            compose.onNodeWithText("In älteren Backups ist er noch enthalten", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Endgültig löschen").performClick()

            compose.onNodeWithText("Noch keine Patienten").assertIsDisplayed()
            assertEquals(null, runBlocking { app.patients.search("Frau") }.firstOrNull())
        }
    }

    @Test
    fun `the search finds patients by their texts, marking those in the archive`() {
        launchApp().use {
            compose.openNewDoku()
            patient("Frau M.", diagnosis = "Schulter")
            val closed = patient("Herr K.", diagnosis = "Schulter rechts")
            runBlocking { app.patients.close(closed) }

            compose.field("Patienten suchen").performTextInput("schulter")

            compose.onNodeWithText("Frau M.").assertIsDisplayed()
            compose.onNode(hasText("Herr K.") and hasText("Archiv")).assertIsDisplayed()
            compose.field("Patienten suchen").performTextReplacement("knie")
            compose.onNodeWithText("Nichts gefunden").assertIsDisplayed()
        }
    }

    @Test
    fun `twelve months after closing, the start page suggests deleting, and the archive deletes those together`() {
        launchApp().use {
            compose.openNewDoku()
            val old = patient("Alt")
            runBlocking { app.patients.close(old) }
            app.clock.now = Instant.parse("2027-10-23T05:00:00Z")
            val recent = patient("Neu")
            runBlocking { app.patients.close(recent) }

            compose.onNodeWithText("1 Person seit über 12 Monaten abgeschlossen – jetzt löschen?").performClick()
            compose.onNodeWithText("abgeschlossen am 23.10.2026 · seit über 12 Monaten").assertIsDisplayed()
            compose.onNodeWithText("abgeschlossen am 23.10.2027").assertIsDisplayed()
            compose.onNodeWithText("Diese 1 endgültig löschen").performClick()
            compose.onNodeWithText("1 Person endgültig löschen?").assertIsDisplayed()
            compose.onNodeWithText("Endgültig löschen").performClick()

            compose.onNodeWithText("Alt").assertDoesNotExist()
            compose.onNodeWithText("Neu").assertIsDisplayed()
            compose.onNodeWithText("Diese 1 endgültig löschen").assertDoesNotExist()
        }
    }

    @Test
    fun `a rotation keeps the patient's page open`() {
        launchApp().use { scenario ->
            compose.openNewDoku()
            compose.addPatient("Frau M.")
            compose.onNode(isSelectable() and hasText("Termine")).performClick()

            scenario.recreate()

            compose.onNodeWithText("Noch keine Termine").assertIsDisplayed()
        }
    }

    /**
     * The overview's [label]led text, showing [text], scrolled to: the overview's, not the bottom bar's "Sonstiges".
     */
    private fun ComposeTestRule.overviewField(label: String, text: String): SemanticsNodeInteraction {
        val row = hasText(label) and hasText(text) and hasClickAction()
        onNode(hasScrollToNodeAction()).performScrollToNode(row)
        return onNode(row)
    }

    /** That the start page lists [names] in this order, top to bottom. */
    private fun ComposeTestRule.assertOrder(vararg names: String) {
        val tops = names.map { name -> onNode(hasText(name) and hasClickAction()).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }
}
