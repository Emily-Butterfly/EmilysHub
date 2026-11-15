package de.emilyshub

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.tracking.TrackingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * The morning and evening entries of Tracking (SPEC.md §17, "Tageseinträge"), on the start page and in their pages, and
 * the symptoms in the menu. The test clock stands at Friday, 23 October 2026, 07:00 in Berlin. Made-up values only.
 */
@RunWith(AndroidJUnit4::class)
class TrackingEntriesScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val entries get() = app.trackingModules.entries
    private val today = LocalDate.parse("2026-10-23")

    @After
    fun closeDatabase() {
        app.tracking.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
    }

    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()

    private fun openUnlocked() {
        compose.openTracking()
        compose.setUpTracking()
    }

    private fun morning(day: LocalDate = today) = checkNotNull(runBlocking { entries.morning(day) })

    private fun evening(day: LocalDate = today) = checkNotNull(runBlocking { entries.evening(day) })

    @Test
    fun `the start page shows the day's entries, open until made, and the morning entry is made and changed`() {
        launchApp().use {
            openUnlocked()
            compose.onNodeWithText("Einträge für 23.10.2026").performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("Morgeneintrag") and hasClickAction()).tap()

            compose.onNodeWithText("Nach 5 Minuten ruhig liegen messen", substring = true).assertIsDisplayed()
            compose.field("Ruhepuls im Liegen (/min)").performTextInput("64")
            compose.field("Schlafdauer (Stunden)").performScrollTo().performTextInput("7,5")
            compose.onNodeWithContentDescription("Schlafqualität: gut").tap()
            compose.onNodeWithContentDescription("Erholt aufgewacht: nein").tap()
            compose.done()

            compose.onNodeWithText("erledigt").performScrollTo().assertIsDisplayed()
            val saved = morning().entry
            assertEquals(64, saved.restingPulse)
            assertEquals(450, saved.sleepMinutes)
            assertEquals(1, saved.sleepQuality)
            assertEquals(false, saved.rested)
            assertNull("left empty, not 0", saved.systolic)

            compose.onNode(hasText("Morgeneintrag") and hasClickAction()).tap()
            compose.field("Ruhepuls im Liegen (/min)").performTextReplacement("66")
            compose.onNodeWithContentDescription("Schlafqualität: gut").tap()
            compose.done()

            assertEquals(66, morning().entry.restingPulse)
            assertNull("tapped again, taken back", morning().entry.sleepQuality)
        }
    }

    @Test
    fun `a value out of its range is shown, and keeps Fertig until put right`() {
        launchApp().use {
            openUnlocked()
            compose.onNode(hasText("Morgeneintrag") and hasClickAction()).tap()

            compose.field("Ruhepuls im Liegen (/min)").performTextInput("20")

            compose.onNodeWithText("Zwischen 30 und 220").assertIsDisplayed()
            compose.onNodeWithText("Fertig").assertIsNotEnabled()
        }
    }

    @Test
    fun `an entry opened and left without a change is not made`() {
        launchApp().use {
            openUnlocked()
            compose.onNode(hasText("Abendeintrag") and hasClickAction()).tap()
            compose.done()

            assertFalse(evening().stored)
            compose.onNode(hasText("Abendeintrag") and hasClickAction()).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `the evening entry asks each symptom in words, the load, the day and a note`() {
        launchApp().use {
            openUnlocked()
            compose.onNode(hasText("Abendeintrag") and hasClickAction()).tap()

            compose.onNodeWithContentDescription("Kopfschmerz: mittel").tap()
            compose.onNodeWithContentDescription("Erschöpfung: nicht vorhanden").tap()
            compose.onNodeWithContentDescription("Körperlich: schwer").tap()
            compose.onNodeWithContentDescription("Arbeitstag: ja").tap()
            compose.field("Trinkmenge (Liter)").performScrollTo().performTextInput("1,5")
            compose.field("Koffein (Anzahl)").performScrollTo().performTextInput("2")
            compose.field("Notiz (Markdown)").performScrollTo().performTextInput("Ein **erfundener** Tag")
            compose.done()

            val sheet = evening()
            val byName = sheet.symptoms.associate { it.name to it.id }
            assertEquals(2, sheet.levels[byName.getValue("Kopfschmerz")])
            assertEquals(0, sheet.levels[byName.getValue("Erschöpfung")])
            assertNull("not recorded", sheet.levels[byName.getValue("Atemnot")])
            assertEquals(3, sheet.entry.loadPhysical)
            assertEquals(true, sheet.entry.workday)
            assertNull(sheet.entry.heatOrStanding)
            assertEquals(15, sheet.entry.fluidDecilitres)
            assertEquals(2, sheet.entry.caffeine)
            assertEquals("Ein **erfundener** Tag", sheet.entry.note)
        }
    }

    @Test
    fun `what is typed is saved before Tracking locks`() {
        launchApp().use {
            openUnlocked()
            compose.onNode(hasText("Morgeneintrag") and hasClickAction()).tap()
            compose.field("Ruhepuls im Liegen (/min)").performTextInput("61")
            // As the app leaves the screen: what was typed has reached the page.
            compose.waitForIdle()

            app.tracking.lock()
            compose.waitForIdle()

            runBlocking { assertTrue(app.tracking.unlockWithPassword(TRACKING_PASSWORD)) }
            assertEquals(61, morning().entry.restingPulse)
        }
    }

    @Test
    fun `until 04 00 the day's entries are those of the day before`() {
        app.clock.now = Instant.parse("2026-10-23T01:30:00Z") // 03:30 in Berlin
        try {
            launchApp().use {
                openUnlocked()

                compose.onNodeWithText("Einträge für 22.10.2026").performScrollTo().assertIsDisplayed()
            }
        } finally {
            app.clock.now = Instant.parse("2026-10-23T05:00:00Z")
        }
    }

    @Test
    fun `an entry is made up for one of the seven days before, and silently marked so`() {
        launchApp().use {
            openUnlocked()
            compose.onNodeWithText("Nachtragen").tap()
            compose.onNode(hasText("Tuesday, October 20, 2026", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Welcher Eintrag für 20.10.2026?").assertIsDisplayed()
            compose.onNode(hasText("Morgeneintrag") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("20.10.2026").assertIsDisplayed()
            compose.field("Ruhepuls im Liegen (/min)").performTextInput("70")
            compose.done()

            val made = morning(LocalDate.parse("2026-10-20")).entry
            assertEquals(70, made.restingPulse)
            assertTrue(made.backfilled)
            compose.onNodeWithText("nachgetragen", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `the symptoms are renamed, hidden and added in the menu, and the evening follows`() {
        launchApp().use {
            openUnlocked()
            compose.onNodeWithContentDescription("Menü von Tracking").performClick()
            compose.onNodeWithText("Symptome").performClick()

            compose.onNodeWithContentDescription("Kopfschmerz umbenennen").tap()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("Kopfweh")
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithContentDescription("Übelkeit abfragen").tap()
            compose.onNodeWithText("Symptom hinzufügen").tap()
            compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Symptom A")
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithContentDescription("Symptom A nach oben").tap()

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNode(hasText("Abendeintrag") and hasClickAction()).tap()

            compose.onNodeWithContentDescription("Kopfweh: leicht").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Symptom A: leicht").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Übelkeit: leicht").assertDoesNotExist()
            val names = evening().symptoms.map { it.name }
            assertEquals(listOf("Symptom A", "Angst/innere Unruhe"), names.takeLast(2))
        }
    }

    @Test
    fun `a choice reads in words, and the one chosen shows as chosen`() {
        launchApp().use {
            openUnlocked()
            compose.onNode(hasText("Abendeintrag") and hasClickAction()).tap()

            compose.onNodeWithContentDescription("Atemnot: leicht").tap()

            compose.onNodeWithContentDescription("Atemnot: leicht").assertIsSelected()
        }
    }
}
