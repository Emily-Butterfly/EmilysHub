package de.emilyshub

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.tracking.DeviceKind
import de.emilyshub.tracking.GarminSample
import de.emilyshub.tracking.HealthAvailability
import de.emilyshub.tracking.HealthSource
import de.emilyshub.tracking.SampleKind
import de.emilyshub.tracking.TrackingDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Garmin in Tracking (SPEC.md §17, "Garmin über Health Connect"): set up in its menu, read as Tracking opens and by
 * "Jetzt lesen", and where the values come from; and the text Health Connect asks the app to show. Made-up values.
 */
@RunWith(AndroidJUnit4::class)
class TrackingGarminScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val health = app.healthSource

    /** Friday, 23 October 2026, 06:30 in Berlin: half an hour before the test clock. */
    private val earlier = Instant.parse("2026-10-23T04:30:00Z")

    @After
    fun closeDatabase() {
        app.tracking.lock()
        app.deleteDatabase(TrackingDatabase.NAME)
    }

    private fun openGarmin() {
        compose.onNodeWithContentDescription("Menü von Tracking").performClick()
        compose.onNodeWithText("Garmin").performClick()
    }

    private fun count() = checkNotNull(runBlocking { app.tracking.withDatabase { it.garmin().count() } })

    @Test
    fun `before Android 14 or without Health Connect the page says so, and Tracking works without`() {
        health.availability = HealthAvailability.Unavailable
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            openGarmin()

            compose.onNodeWithText("Garmin-Werte gibt es über Health Connect erst ab Android 14", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Einrichten").assertDoesNotExist()
        }
    }

    @Test
    fun `setting up asks Health Connect for the five permissions, then reads`() {
        health.samples += GarminSample.of("hr#0", SampleKind.HeartRate, earlier, earlier, 64, GARMIN, DeviceKind.Watch)
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            openGarmin()

            compose.onNodeWithText("Einrichten").performClick()
            compose.waitForIdle()

            assertEquals(listOf(HealthSource.PERMISSIONS), health.requests)
            compose.onNodeWithText("1 Werte gelesen").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Jetzt lesen").assertIsDisplayed()
            assertEquals(1, count())
        }
    }

    @Test
    fun `without the permissions granted nothing is read, and the page says so`() {
        health.grantOnRequest = emptySet()
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            openGarmin()

            compose.onNodeWithText("Einrichten").performClick()

            compose.onNodeWithText("Ohne die Berechtigungen liest Tracking nichts", substring = true).assertIsDisplayed()
            assertTrue(health.reads.isEmpty())
        }
    }

    @Test
    fun `once set up, Tracking reads as it opens, and the sources show app, device, count and tier`() {
        health.grantedNow = HealthSource.PERMISSIONS
        health.samples += GarminSample.of("strap#0", SampleKind.HeartRate, earlier, earlier, 92, GARMIN, DeviceKind.ChestStrap)
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            compose.waitForIdle()
            assertEquals("read as it opened", 1, count())

            openGarmin()

            compose.onNodeWithText("Woher kommen meine Werte?").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("$GARMIN · Brustgurt · 1 Werte").performScrollTo().assertIsDisplayed()
            // The time of day as the phone's zone shows it, which the test does not set.
            val last = compose.onNodeWithText("zuletzt 92/min am 23.10.2026", substring = true).performScrollTo()
            last.assertIsDisplayed()
            assertTrue(last.fetchSemanticsNode().config[SemanticsProperties.Text].single().text.endsWith(" · Stufe A"))

            health.samples += GarminSample.of("strap#1", SampleKind.HeartRate, earlier.plusSeconds(60), earlier.plusSeconds(60), 95, GARMIN, DeviceKind.ChestStrap)
            compose.onNodeWithText("Jetzt lesen").performClick()
            compose.waitForIdle()
            assertEquals(2, count())
        }
    }

    @Test
    fun `while Tracking is locked, Health Connect is not read`() {
        health.grantedNow = HealthSource.PERMISSIONS
        launchApp().use {
            compose.openTracking()
            compose.setUpTracking()
            compose.waitForIdle()
            val reads = health.reads.size
            app.tracking.lock()

            compose.openTracking()
            compose.waitForIdle()

            assertEquals(reads, health.reads.size)
        }
    }

    @Test
    fun `Health Connect's question why Tracking reads it opens only that text`() {
        val intent = Intent(app, MainActivity::class.java).setAction("android.intent.action.VIEW_PERMISSION_USAGE")
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.onNodeWithText("Health Connect und EmilysHub").assertIsDisplayed()
            compose.onNodeWithText("Gelesen wird nur, solange der Bereich Tracking entsperrt offen ist", substring = true).assertIsDisplayed()
        }
    }

    private companion object {
        const val GARMIN = "com.garmin.android.apps.connectmobile"
    }
}
