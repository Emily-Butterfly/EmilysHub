package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.tracking.BaseField
import de.emilyshub.tracking.Deviation
import de.emilyshub.tracking.TrackingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

/**
 * The medications with their timeline and the base data of Tracking (SPEC.md §17, "Grunddaten & Medikamente"), and how
 * the evening entry asks for the intakes. The test clock stands at Friday, 23 October 2026. Made-up names only.
 */
@RunWith(AndroidJUnit4::class)
class TrackingMedicationsScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val modules get() = app.trackingModules
    private val today = LocalDate.parse("2026-10-23")

    @After
    fun closeDatabase() {
        app.tracking.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
    }

    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    private fun openMenu(item: String) {
        compose.onNodeWithContentDescription("Menü von Tracking").performClick()
        compose.onNodeWithText(item).performClick()
    }

    private fun typeTime(hour: String, minute: String) {
        val fields = compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog()))
        fields[0].performTextReplacement(hour)
        fields[1].performTextReplacement(minute)
        compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
    }

    private fun medications() = checkNotNull(runBlocking { modules.medications.list() })

    /** Medication A, taken at 08:00 since the test's day, made on its page. */
    private fun addMedicationA() {
        openMenu("Medikamente")
        compose.onNodeWithText("Medikament hinzufügen").tap()
        compose.field("Name").performTextInput("Medikament A")
        compose.field("Darreichung (optional)").performTextInput("Tablette")
        compose.onNodeWithText("Uhrzeit hinzufügen").tap()
        typeTime("08", "00")
        compose.onNodeWithText("Senkt den Puls").tap()
        compose.done()
    }

    @Test
    fun `a medication is typed in, with the hint to change it only with those treating, and listed`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            openMenu("Medikamente")
            compose.onNodeWithText("Medikamente nur mit den Behandelnden ändern oder absetzen.").assertIsDisplayed()
            compose.onNodeWithText("Noch keine Medikamente").assertIsDisplayed()
            compose.onNodeWithContentDescription("Zurück").performClick()

            addMedicationA()

            compose.onNodeWithText("Medikament A").assertIsDisplayed()
            compose.onNodeWithText("Tablette · 08:00 · seit 23.10.2026").assertIsDisplayed()
            val saved = medications().active.single()
            assertEquals(listOf(LocalTime.of(8, 0)), saved.times)
            assertTrue(saved.lowersPulse)
            assertEquals(today, saved.start)
        }
    }

    @Test
    fun `without a name or a time a medication cannot be finished, and says why`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            openMenu("Medikamente")
            compose.onNodeWithText("Medikament hinzufügen").tap()

            compose.onNodeWithText("Fertig").performClick()

            compose.onNodeWithText("Bitte einen Namen eingeben").assertIsDisplayed()
            compose.field("Name").performTextInput("Medikament A")
            compose.onNodeWithText("Fertig").performClick()
            compose.onNodeWithText("Bitte mindestens eine Uhrzeit").performScrollTo().assertIsDisplayed()
            assertTrue(medications().active.isEmpty())
        }
    }

    @Test
    fun `a medication is changed, gets a dose change and is stopped, then listed below`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            addMedicationA()

            compose.onNodeWithText("Medikament A").performClick()
            compose.field("Wofür (optional)").performScrollTo().performTextInput("Erfundener Zweck")
            compose.onNodeWithText("Dosisänderung hinzufügen").tap()
            compose.field("Dosis vorher").performTextInput("1 Tablette")
            compose.field("Dosis nachher").performTextInput("2 Tabletten")
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("23.10.2026: 1 Tablette → 2 Tabletten").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Beenden").tap()
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
            compose.done()

            val saved = medications().active.single()
            assertEquals("Erfundener Zweck", saved.purpose)
            assertEquals(today, saved.end)
            assertEquals(1, checkNotNull(runBlocking { modules.medications.timeline(saved.id) }).doseChanges.size)

            runBlocking { modules.medications.save(saved.copy(start = today.minusDays(10), end = today.minusDays(1))) }
            compose.waitForIdle()
            compose.onNodeWithText("Beendet").assertIsDisplayed()
        }
    }

    @Test
    fun `the evening entry asks each medication of the day, as planned unless chosen otherwise`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            addMedicationA()
            compose.onNodeWithContentDescription("Zurück").performClick()

            compose.onNode(hasText("Abendeintrag") and hasClickAction()).tap()
            compose.onNodeWithContentDescription("Medikament A: wie geplant").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Medikamente nur mit den Behandelnden", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Medikament A: vergessen").tap()
            compose.done()

            val sheet = checkNotNull(runBlocking { modules.entries.evening(today) })
            assertEquals(Deviation.Forgotten, sheet.deviations.values.single().deviation)

            compose.onNode(hasText("Abendeintrag") and hasClickAction()).tap()
            compose.onNodeWithContentDescription("Medikament A: andere Uhrzeit").tap()
            typeTime("10", "30")
            compose.onNodeWithContentDescription("Medikament A: um 10:30").performScrollTo().assertIsSelected()
            compose.done()

            val changed = checkNotNull(runBlocking { modules.entries.evening(today) }).deviations.values.single()
            assertEquals(Deviation.OtherTime, changed.deviation)
            assertEquals(LocalTime.of(10, 30), changed.time)
        }
    }

    @Test
    fun `the base data are written in their fields, with the questions as placeholders`() {
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            openMenu("Grunddaten")

            compose.onNodeWithText("Gibt es in der Familie Thrombosen", substring = true).assertIsDisplayed()
            compose.field("Familie").performTextInput("Erfundene Angabe")
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.waitForIdle()

            assertEquals("Erfundene Angabe", checkNotNull(runBlocking { modules.baseData.all() })[BaseField.Family])
            assertEquals(1, compose.onAllNodesWithText("Akute Beschwerden?").fetchSemanticsNodes().size)
        }
    }
}
