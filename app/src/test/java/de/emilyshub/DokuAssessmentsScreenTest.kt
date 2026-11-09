package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.doku.PatientDraft
import de.emilyshub.doku.assessments.Answer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The built-in forms (SPEC.md §14): chosen at an appointment by search and from those last used, filled in with the
 * result counting along, and changed; the Anamnesebogen by body regions; the card and history in the tab Bögen; and
 * hiding a form in the Doku's menu.
 */
@RunWith(AndroidJUnit4::class)
class DokuAssessmentsScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    /** The Doku open, with patient "Frau M."; their id. */
    private fun ComposeTestRule.openWithPatient(): Long {
        openDoku()
        setUpDoku()
        return runBlocking { checkNotNull(app.patients.create(PatientDraft("Frau M."))) }
    }

    private fun appointment(patient: Long, date: String): Long = runBlocking {
        checkNotNull(app.appointments.create(patient)).also { app.appointments.setDate(it, LocalDate.parse(date)) }
    }

    /** The PHQ-9 answered with [points], item by item. */
    private fun phq9(vararg points: Int): Map<String, Answer> = points.withIndex().associate { (i, p) -> "${i + 1}" to Answer.Chosen("$p") }

    private fun ComposeTestRule.openTab(tab: String) {
        onNodeWithText("Frau M.").performClick()
        onNode(isSelectable() and hasText(tab)).performClick()
    }

    /** "+ Bogen" in the appointment shown. */
    private fun ComposeTestRule.addForm() {
        onNodeWithText("+ Bogen").performScrollTo().performClick()
    }

    @Test
    fun `a form is found by its abbreviation, filled in with the result counting along, and changed`() {
        launchApp().use {
            val patient = compose.openWithPatient()
            appointment(patient, "2026-10-23")
            compose.openTab("Termine")
            compose.onNodeWithText("(leer)").performClick()

            compose.addForm()
            compose.field("Bogen suchen").performTextInput("phq-9")
            compose.onNodeWithText("Patient Health Questionnaire 9").performClick()

            compose.onNodeWithText("23.10.2026").assertIsDisplayed()
            compose.onNodeWithText("Quelle: PHQ-9, Stand 10/2026").assertIsDisplayed()
            compose.onNode(hasText("Ergebnis: –")).performScrollTo().assertIsDisplayed()
            (1..9).forEach { n ->
                val option = hasText(if (n == 4) "Beinahe jeden Tag" else "Überhaupt nicht") and hasClickAction()
                // The n-th of each answer: they repeat for every item.
                compose.onAllNodes(option)[n - 1].performScrollTo().performClick()
            }
            compose.onNode(hasText("Summenwert: 3 Punkte · gesund")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Fertig").performClick()

            compose.onNodeWithText("Summenwert: 3 Punkte · gesund").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Patient Health Questionnaire 9").performScrollTo().performClick()
            compose.onAllNodes(hasText("Beinahe jeden Tag") and hasClickAction())[0].performScrollTo().performClick()
            compose.onNode(hasText("Summenwert: 6 Punkte · unauffällig")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Fertig").performClick()

            compose.onNodeWithText("Summenwert: 6 Punkte · unauffällig").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `the forms used last come first when choosing`() {
        launchApp().use {
            val patient = compose.openWithPatient()
            runBlocking { app.assessments.save(appointment(patient, "2026-10-01"), null, "phq-9", phq9(0)) }
            appointment(patient, "2026-10-23")
            compose.openTab("Termine")
            compose.onNodeWithText("23.10.2026").performClick()

            compose.addForm()

            compose.onNodeWithText("Zuletzt benutzt").assertIsDisplayed()
            compose.onNodeWithText("Anamnesebogen").assertIsDisplayed()
            compose.onNodeWithText("Kiefer / CMD").performScrollTo().assertIsDisplayed()
            compose.onAllNodes(hasText("Patient Health Questionnaire 9"))[0].performClick()

            compose.onNodeWithText("Quelle: PHQ-9, Stand 10/2026").assertIsDisplayed()
        }
    }

    @Test
    fun `the Anamnesebogen asks where by body regions`() {
        launchApp().use {
            val patient = compose.openWithPatient()
            val at = appointment(patient, "2026-10-23")
            compose.openTab("Termine")
            compose.onNodeWithText("(leer)").performClick()

            compose.addForm()
            compose.field("Bogen suchen").performTextInput("anamnese")
            compose.onNodeWithText("Anamnesebogen Version 8").performClick()

            compose.onNodeWithText("1.) Wo haben Sie Ihre Beschwerden?").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Knie links").performScrollTo().performClick()
            compose.field("Wo noch? (Freitext)").performScrollTo().performTextInput("Leiste")
            compose.onNodeWithText("Fertig").performClick()

            compose.onNodeWithText("Anamnesebogen Version 8").performScrollTo().assertIsDisplayed()
            val filled = runBlocking { checkNotNull(app.assessments.at(at)) }.single()
            assertEquals(mapOf("1" to Answer.Regions(setOf(de.emilyshub.doku.assessments.BodyRegion.KneeLeft), "Leiste")), filled.answers)

            compose.onNodeWithText("Anamnesebogen Version 8").performScrollTo().performClick()
            compose.onNodeWithText("Knie links").performScrollTo().assertIsSelected()
        }
    }

    @Test
    fun `a form chosen and left empty goes again`() {
        launchApp().use {
            val patient = compose.openWithPatient()
            val at = appointment(patient, "2026-10-23")
            compose.openTab("Termine")
            compose.onNodeWithText("(leer)").performClick()

            compose.addForm()
            compose.field("Bogen suchen").performTextInput("phq-9")
            compose.onNodeWithText("Patient Health Questionnaire 9").performClick()
            compose.onNodeWithText("Fertig").performClick()

            compose.waitForIdle()
            assertEquals(0, runBlocking { checkNotNull(app.assessments.at(at)) }.size)
        }
    }

    @Test
    fun `an RMDQ with nothing ticked stays as 0 by Fertig, and goes again by back`() {
        launchApp().use {
            val patient = compose.openWithPatient()
            val at = appointment(patient, "2026-10-23")
            compose.openTab("Termine")
            compose.onNodeWithText("(leer)").performClick()

            compose.addForm()
            compose.field("Bogen suchen").performTextInput("rmdq")
            compose.onNodeWithText("Roland and Morris Disability Questionnaire").performClick()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.waitForIdle()
            assertEquals(0, runBlocking { checkNotNull(app.assessments.at(at)) }.size)

            compose.addForm()
            compose.field("Bogen suchen").performTextInput("rmdq")
            compose.onNodeWithText("Roland and Morris Disability Questionnaire").performClick()
            compose.onNode(hasText("Score: 0 Punkte")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Fertig").performClick()

            compose.onNodeWithText("Score: 0 Punkte").performScrollTo().assertIsDisplayed()
            assertEquals(1, runBlocking { checkNotNull(app.assessments.at(at)) }.size)
        }
    }

    @Test
    fun `the tab of the forms has a card, which opens the history, whose rows open the form`() {
        launchApp().use {
            val patient = compose.openWithPatient()
            runBlocking {
                app.assessments.save(appointment(patient, "2026-09-01"), null, "phq-9", phq9(3, 3, 3, 3, 3, 0, 0, 0, 0))
                app.assessments.save(appointment(patient, "2026-10-01"), null, "phq-9", phq9(1, 1, 1, 1, 1, 0, 0, 0, 0))
                app.assessments.save(appointment(patient, "2026-10-02"), null, "anamnese-v8", mapOf("8" to Answer.Text("Kopfweh")))
            }

            compose.openTab("Bögen")

            compose.onNodeWithText("zuletzt am 01.10.2026").assertIsDisplayed()
            compose.onNodeWithText("Summenwert: 5 Punkte · unauffällig ▼ −10 Punkte besser seit 01.09.2026").assertIsDisplayed()
            compose.onNodeWithText("Anamnesebogen Version 8").assertIsDisplayed()
            compose.onNodeWithText("Patient Health Questionnaire 9").performClick()

            compose.onNodeWithContentDescription("Verlauf von Summenwert", substring = true).assertIsDisplayed()
            val row = hasText("01.09.2026") and hasText("Summenwert 15 Punkte") and hasClickAction()
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(row)
            compose.onNode(row).performClick()

            compose.onNode(hasText("Summenwert: 15 Punkte · „mittelgradige“ Depression")).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(isSelectable() and hasText("Bögen")).assertIsDisplayed()
            compose.onNode(hasText("Anamnesebogen Version 8") and hasClickAction()).performClick()
            compose.onNodeWithText("8.) Haben Sie noch weitere Beschwerden?").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `a form hidden in the menu is not offered, but one filled in stays`() {
        launchApp().use {
            val patient = compose.openWithPatient()
            val at = appointment(patient, "2026-10-23")
            runBlocking { app.assessments.save(at, null, "phq-9", phq9(0)) }

            compose.onNodeWithContentDescription("Menü der Dokumentation").performClick()
            compose.onNode(hasText("Bögen") and hasClickAction()).performClick()
            // Under "Kiefer / CMD" first, below the forms on pain there.
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Quelle: PHQ-9, Stand 10/2026", substring = true))
            compose.onAllNodesWithText("Quelle: PHQ-9, Stand 10/2026", substring = true)[0].assertIsDisplayed()
            // By its action: scrolled to, the row lies under the bottom bar, which a touch would hit.
            compose.onAllNodes(hasText("Patient Health Questionnaire 9") and hasClickAction())[0].performScrollTo()
                .performSemanticsAction(SemanticsActions.OnClick)
            compose.onAllNodes(hasText("Patient Health Questionnaire 9") and hasClickAction())[0].assertIsOn()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.openTab("Termine")
            compose.onNodeWithText("(leer)").performClick()
            compose.onNodeWithText("Patient Health Questionnaire 9").performScrollTo().assertIsDisplayed()
            compose.addForm()
            compose.onNodeWithText("Anamnesebogen Version 8").assertIsDisplayed()
            compose.onAllNodes(hasText("Patient Health Questionnaire 9")).fetchSemanticsNodes().size.let { assertEquals(1, it) }
            compose.field("Bogen suchen").performTextInput("phq-9")
            compose.onNodeWithText("Kein Bogen gefunden").assertIsDisplayed()
        }
    }

    @Test
    fun `deleting an appointment names its measurements and forms`() {
        launchApp().use {
            val patient = compose.openWithPatient()
            val at = appointment(patient, "2026-10-23")
            runBlocking {
                app.assessments.save(at, null, "phq-9", phq9(0))
                app.assessments.save(at, null, "anamnese-v8", emptyMap())
            }
            compose.openTab("Termine")
            compose.onNodeWithText("(leer)").performClick()

            compose.onNodeWithContentDescription("Menü des Termins").performClick()
            compose.onNodeWithText("Löschen").performClick()

            compose.onNodeWithText("Mit seiner Dokumentation und 2 Bögen.").assertIsDisplayed()
        }
    }

    @Test
    fun `the search finds the words of a form and opens it`() {
        launchApp().use {
            val patient = compose.openWithPatient()
            runBlocking { app.assessments.save(appointment(patient, "2026-10-20"), null, "anamnese-v8", mapOf("39" to Answer.Text("wieder joggen"))) }

            compose.field("Patienten suchen").performTextInput("joggen")
            compose.onNode(hasText("Frau M.") and hasText("Anamnesebogen Version 8 vom 20.10.2026")).performClick()

            compose.onNodeWithText("Quelle: Anamnesebogen Version 8, Stand 07/2026").assertIsDisplayed()
        }
    }
}
