package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * A new place made right in the field "Orte" of the routine and the to-do editor (SPEC.md §4, §8; #112): the place
 * editor opens over the editor, whose draft stays through a rotation and the lock, and the new place is chosen once
 * saved; left off, nothing changes.
 */
@RunWith(AndroidJUnit4::class)
class NewPlaceInFieldTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `a place made from the routine editor is chosen there, and the routine saved with it`() {
        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            field("Name").performTextInput("Tabletten")
            compose.openMoreOptions()
            compose.onNodeWithText("Orte legst du in den Einstellungen", substring = true).assertDoesNotExist()

            makePlace("Zuhause", "FritzBox")

            field("Name").assertTextContains("Tabletten")
            placeChip("Zuhause").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Speichern").performClick()
        }
        val routine = runBlocking { app.routines.inUse().first() }.single()
        assertEquals("Tabletten", routine.name)
        assertEquals(setOf(placeId("Zuhause")), runBlocking { app.routines.placeIdsOf(routine.id) })
    }

    @Test
    fun `a place made from the to-do editor is chosen beside one chosen before, and survives a rotation`() {
        app.place("Arbeit", "Büro-WLAN")
        launchApp().use { scenario ->
            tab("To-dos").performClick()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Titel").performTextInput("Pakete")
            compose.openMoreOptions()
            placeChip("Arbeit").performScrollTo().performClick()

            newPlace()
            field("Name").performTextInput("Post")
            scenario.recreate()
            field("WLAN-Name (SSID)").performTextInput("Post-WLAN")
            compose.onNodeWithText("Hinzufügen").performScrollTo().performClick()
            compose.onNodeWithText("Speichern").performClick()

            field("Titel").assertTextContains("Pakete")
            placeChip("Arbeit").performScrollTo().assertIsSelected()
            placeChip("Post").performScrollTo().assertIsSelected()
        }
    }

    @Test
    fun `leaving the place editor without a place changes nothing in the field`() {
        app.place("Arbeit", "Büro-WLAN")
        launchApp().use {
            tab("To-dos").performClick()
            compose.onNodeWithContentDescription("To-do anlegen").performClick()
            field("Titel").performTextInput("Pakete")
            compose.openMoreOptions()

            newPlace()
            compose.onNodeWithContentDescription("Zurück").performClick()

            field("Titel").assertTextContains("Pakete")
            placeChip("Arbeit").performScrollTo().assertIsNotSelected()
            compose.onNodeWithText("Neuer Ort").performScrollTo().assertIsDisplayed()
        }
        assertEquals(listOf("Arbeit"), runBlocking { app.places.all().first() }.map { it.name })
    }

    @Test
    fun `the routine's draft stays through the lock while a place is made, which is then chosen`() {
        app.authenticator.deviceSecure = true
        launchApp().use { scenario ->
            compose.unlockWithFingerprint(app.authenticator)
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            field("Name").performTextInput("Tabletten")
            compose.openMoreOptions()
            newPlace()
            field("Name").performTextInput("Zuhause")

            scenario.leaveAndReturn { ShadowSystemClock.advanceBy(Duration.ofMinutes(5)) }
            compose.unlockWithFingerprint(app.authenticator)

            field("Name").assertTextContains("Zuhause")
            field("WLAN-Name (SSID)").performTextInput("FritzBox")
            compose.onNodeWithText("Hinzufügen").performScrollTo().performClick()
            compose.onNodeWithText("Speichern").performClick()

            field("Name").assertTextContains("Tabletten")
            placeChip("Zuhause").performScrollTo().assertIsSelected()
        }
    }

    @Test
    fun `a place begun and discarded in the routine editor changes nothing in the field`() {
        app.place("Arbeit", "Büro-WLAN")
        launchApp().use {
            tab("Routinen").performClick()
            compose.onNodeWithContentDescription("Routine anlegen").performClick()
            field("Name").performTextInput("Tabletten")
            compose.openMoreOptions()

            newPlace()
            field("Name").performTextInput("Gym")
            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithText("Verwerfen").performClick()

            field("Name").assertTextContains("Tabletten")
            placeChip("Arbeit").performScrollTo().assertIsNotSelected()
        }
        assertEquals(listOf("Arbeit"), runBlocking { app.places.all().first() }.map { it.name })
    }

    private fun makePlace(name: String, wifi: String) {
        newPlace()
        field("Name").performTextInput(name)
        field("WLAN-Name (SSID)").performTextInput(wifi)
        compose.onNodeWithText("Hinzufügen").performScrollTo().performClick()
        compose.onNodeWithText("Speichern").performClick()
    }

    private fun newPlace() = compose.onNode(hasText("Neuer Ort") and hasClickAction()).performScrollTo().performClick()

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun tab(label: String) = compose.onNode(isSelectable() and hasText(label))

    private fun placeChip(name: String) = compose.onNode(isSelectable() and hasText(name))

    private fun placeId(name: String) = runBlocking { app.places.all().first() }.single { it.name == name }.id
}
