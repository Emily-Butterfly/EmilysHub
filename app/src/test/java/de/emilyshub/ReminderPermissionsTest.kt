package de.emilyshub

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class ReminderPermissionsTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun allowExactAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `once the first routine is saved, the app explains in German and then asks for notifications`() {
        launchApp().use { scenario ->
            compose.onNodeWithText("Erinnerungen erlauben?").assertDoesNotExist()

            createRoutine("Vitamine")

            compose.onNodeWithText("Damit EmilysHub dich zur Uhrzeit", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Weiter").performClick()

            scenario.onActivity { activity ->
                val request = checkNotNull(shadowOf(activity).lastRequestedPermission)
                assertArrayEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions)
                shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
                val granted = intArrayOf(PackageManager.PERMISSION_GRANTED)
                activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, granted)
            }
            compose.waitForIdle()

            compose.onNodeWithText("Erinnerungen erlauben?").assertDoesNotExist()
        }
        assertEquals("yes", app.settingsDao.values["reminders-asked"])
    }

    @Test
    fun `asking can wait, and is not repeated`() {
        app.routine("Vitamine", "07:30")

        launchApp().use {
            compose.onNodeWithText("Nicht jetzt").performClick()
            compose.onNodeWithText("Erinnerungen erlauben?").assertDoesNotExist()
        }
        launchApp().use {
            compose.waitForIdle()
            compose.onNodeWithText("Erinnerungen erlauben?").assertDoesNotExist()
        }
    }

    @Test
    fun `when exact alarms are not allowed, the app explains why and opens Android's setting`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        app.routine("Vitamine", "07:30")

        launchApp().use {
            compose.onNodeWithText("Damit Erinnerungen auf die Minute genau kommen", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Zur Einstellung").performClick()

            compose.onNodeWithText("Pünktlich erinnern?").assertDoesNotExist()
        }
        val started = checkNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, started.action)
        assertEquals(Uri.parse("package:de.emilyshub"), started.data)
    }

    @Test
    fun `with everything allowed already, nothing is asked`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.routine("Vitamine", "07:30")

        launchApp().use {
            compose.waitForIdle()
            compose.onNodeWithText("Erinnerungen erlauben?").assertDoesNotExist()
            compose.onNodeWithText("Pünktlich erinnern?").assertDoesNotExist()
        }
    }

    private fun createRoutine(name: String) {
        compose.onNode(isSelectable() and hasText("Routinen")).performClick()
        compose.onNodeWithContentDescription("Routine anlegen").performClick()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput(name)
        compose.onNodeWithText("Speichern").performClick()
    }

}
