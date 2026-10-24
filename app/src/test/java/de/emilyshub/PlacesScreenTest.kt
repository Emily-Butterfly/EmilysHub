package de.emilyshub

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The places, kept in the settings, and the WLANs they are recognised by (SPEC.md §4). */
@RunWith(AndroidJUnit4::class)
class PlacesScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `places are created with a WLAN typed in, renamed and deleted in the settings, and deleting one asks first`() {
        launchApp().use {
            openPlaces()
            compose.onNodeWithText("Noch keine Orte.").assertIsDisplayed()

            compose.onNodeWithContentDescription("Ort anlegen").performClick()
            field("Name").performTextInput("Zuhause")
            field("WLAN-Name (SSID)").performTextInput("FritzBox 7590")
            compose.onNodeWithText("Hinzufügen").performScrollTo().performClick()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("FritzBox 7590").assertIsDisplayed()

            compose.onNodeWithText("Zuhause").performClick()
            field("Name").performTextReplacement("Daheim")
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Daheim").performClick()
            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNodeWithText("Abbrechen").performClick()
            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNode(hasText("Löschen") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Noch keine Orte.").assertIsDisplayed()
        }
        assertEquals(emptyList<Any>(), places())
    }

    @Test
    fun `a place needs a name and a WLAN before it is saved`() {
        launchApp().use {
            openPlaces()
            compose.onNodeWithContentDescription("Ort anlegen").performClick()

            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Bitte gib einen Namen ein.").assertIsDisplayed()
            compose.onNodeWithText("Ein Ort braucht mindestens ein WLAN.").performScrollTo().assertIsDisplayed()
        }
        assertEquals(emptyList<Any>(), places())
    }

    @Test
    fun `a WLAN that belongs to another place already says so, and the place is not saved`() {
        app.place("Arbeit", "Firma")

        launchApp().use {
            openPlaces()
            compose.onNodeWithContentDescription("Ort anlegen").performClick()
            field("Name").performTextInput("Büro")
            field("WLAN-Name (SSID)").performTextInput("Firma")
            compose.onNodeWithText("Hinzufügen").performScrollTo().performClick()

            compose.onNodeWithText("Gehört schon zu „Arbeit“").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Ort bearbeiten").assertDoesNotExist()
            compose.onNodeWithText("Neuer Ort").assertIsDisplayed()
        }
        assertEquals(listOf("Arbeit" to listOf("Firma")), places())
    }

    @Test
    fun `the location permission is asked for only here, with why the WLAN name needs it`() {
        launchApp().use { scenario ->
            scenario.onActivity { assertNull(shadowOf(it).lastRequestedPermission) }

            openPlaces()
            compose.onNodeWithText("Android verrät den Namen des WLANs", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Erlauben").performClick()

            scenario.onActivity { activity ->
                val request = checkNotNull(shadowOf(activity).lastRequestedPermission)
                assertEquals(listOf(FINE, COARSE), request.requestedPermissions.toList())
                shadowOf(app).grantPermissions(FINE, COARSE)
                val granted = intArrayOf(PackageManager.PERMISSION_GRANTED, PackageManager.PERMISSION_GRANTED)
                activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, granted)
            }
            app.wifi.ssid = "Café"

            compose.onNodeWithText("Kein bekannter Ort – WLAN „Café“").assertIsDisplayed()
        }
    }

    @Test
    fun `the place recognised now is the one with the WLAN connected to`() {
        allowLocation()
        app.place("Zuhause", "FritzBox", "Mesh")
        app.wifi.ssid = "Mesh"

        launchApp().use {
            openPlaces()
            compose.onNodeWithText("Aktuell erkannter Ort").assertIsDisplayed()
            compose.onNodeWithText("Zuhause, über das WLAN „Mesh“").assertIsDisplayed()

            app.wifi.ssid = "Café"
            compose.onNodeWithText("Kein bekannter Ort – WLAN „Café“").assertIsDisplayed()

            app.wifi.ssid = null
            compose.onNodeWithText("Kein WLAN verbunden").assertIsDisplayed()
        }
    }

    @Test
    fun `with the location off, the place recognised says so and leads to the setting`() {
        allowLocation()
        shadowOf(app.getSystemService(LocationManager::class.java)).setLocationEnabled(false)

        launchApp().use {
            openPlaces()
            compose.onNodeWithText("Der Standort ist ausgeschaltet", substring = true).assertIsDisplayed()

            compose.onNodeWithText("Standort einschalten").performClick()

            assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, shadowOf(app).nextStartedActivity?.action)
        }
    }

    @Test
    fun `the WLAN connected to is taken over into a place`() {
        allowLocation()
        app.wifi.ssid = "FritzBox"

        launchApp().use {
            openPlaces()
            compose.onNodeWithContentDescription("Ort anlegen").performClick()
            field("Name").performTextInput("Zuhause")

            compose.onNodeWithText("Aktuelles WLAN übernehmen („FritzBox“)").performScrollTo().performClick()
            compose.onNodeWithText("Speichern").performClick()
        }
        assertEquals(listOf("Zuhause" to listOf("FritzBox")), places())
    }

    private fun openPlaces() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Orte verwalten").performScrollTo().performClick()
    }

    private fun allowLocation() = shadowOf(app).grantPermissions(FINE, COARSE)

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun places() = runBlocking { app.places.all().first().map { it.place.name to it.ssids } }

    private companion object {
        const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
        const val COARSE = Manifest.permission.ACCESS_COARSE_LOCATION
    }
}
