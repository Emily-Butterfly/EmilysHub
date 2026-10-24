package de.emilyshub

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowBuild
import java.time.Duration

/**
 * "Funktioniert alles?" in the settings (SPEC.md §11): what the reminders need from Android, each with whether Android
 * allows it and the way to its page in Android's settings; a hint for Samsung; and a reminder to try out.
 */
@RunWith(AndroidJUnit4::class)
class StatusScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun askedBefore() {
        // The questions for reminders came once already, so that they do not come up over the screens here.
        app.settingsDao.values["reminders-asked"] = "yes"
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
    }

    @Test
    fun `with everything allowed, each of what the reminders need says so`() {
        allowEverything()

        launchApp().use {
            openStatus()

            listOf("Benachrichtigungen", "Exakte Wecker", "Standort „Immer“", "Vollbild-Alarme", "Keine Akku-Einschränkung")
                .forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
            compose.onAllNodesWithText("Erlaubt").assertCountEquals(5)
        }
    }

    @Test
    fun `what is allowed still leads to its page, the battery to the list of apps without limits`() {
        allowEverything()

        launchApp().use {
            openStatus()

            compose.onAllNodesWithText("Einstellung öffnen").assertCountEquals(5)
            compose.onAllNodesWithText("Einstellung öffnen")[4].performScrollTo().performClick()
            assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, shadowOf(app).nextStartedActivity?.action)
        }
    }

    @Test
    fun `with the reminders turned down so that they no longer pop up, it says so and leads to their channel`() {
        allowEverything()
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("reminders", "Erinnerungen", NotificationManager.IMPORTANCE_LOW))

        launchApp().use {
            openStatus()

            compose.onNodeWithText("nicht als Pop-up", substring = true).performScrollTo().assertIsDisplayed()
            val channel = opens("Pop-up einschalten")
            assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channel.action)
            assertEquals("reminders", channel.getStringExtra(Settings.EXTRA_CHANNEL_ID))
        }
    }

    @Test
    fun `each one lacking leads to its own page in Android's settings`() {
        launchApp().use {
            openStatus()

            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, opens("Benachrichtigungen erlauben").action)
            assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, opens("Exakte Wecker erlauben").action)
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, opens("Standort „Immer“ erlauben").action)
            assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, opens("Vollbild-Alarme erlauben").action)
            val battery = opens("Akku-Einschränkung aufheben")
            assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, battery.action)
            assertEquals(Uri.parse("package:${app.packageName}"), battery.data)
        }
    }

    @Test
    fun `with the location allowed always but turned off, it leads to the setting that turns it on`() {
        allowEverything()
        shadowOf(app.getSystemService(LocationManager::class.java)).setLocationEnabled(false)

        launchApp().use {
            openStatus()

            compose.onNodeWithText("Der Standort ist ausgeschaltet.", substring = true).performScrollTo().assertIsDisplayed()
            assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, opens("Standort einschalten").action)
        }
    }

    @Test
    fun `back from Android's settings, it shows what is allowed now`() {
        launchApp().use { scenario ->
            openStatus()
            compose.onNodeWithText("Benachrichtigungen erlauben").performScrollTo().assertIsDisplayed()

            scenario.leaveAndReturn { shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS) }

            compose.onNodeWithText("Benachrichtigungen erlauben").assertDoesNotExist()
            compose.onAllNodesWithText("Erlaubt").assertCountEquals(1)
        }
    }

    @Test
    fun `what the routines in use need is marked as lacking, what they do not need yet as not needed now`() {
        val home = app.place("Zuhause", "FritzBox")
        app.routine("Tabletten", "08:00", places = setOf(home))

        launchApp().use {
            openStatus()

            compose.onAllNodesWithText("Fehlt").assertCountEquals(3)
            compose.onNodeWithText("Fehlt, wird gerade nicht gebraucht").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Empfohlen").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `a to-do waiting for a place needs what a routine bound to one does`() {
        val home = app.place("Zuhause", "FritzBox")
        app.todo("Pakete", places = setOf(home))

        launchApp().use {
            openStatus()

            compose.onAllNodesWithText("Fehlt").assertCountEquals(3)
            compose.onNodeWithText("Für Routinen und To-dos an Orten", substring = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `on a Samsung, it tells how to keep Samsung from putting the app to sleep`() {
        ShadowBuild.setManufacturer("samsung")

        launchApp().use {
            openStatus()

            compose.onNodeWithText("Samsung: „Nie in Standby versetzen“").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Nie in Standby versetzte Apps", substring = true).performScrollTo().assertIsDisplayed()
            assertEquals(Intent.ACTION_POWER_USAGE_SUMMARY, opens("Akku-Einstellungen öffnen").action)
        }
    }

    @Test
    fun `elsewhere there is no Samsung hint`() {
        ShadowBuild.setManufacturer("Google")

        launchApp().use {
            openStatus()

            compose.onNodeWithText("Samsung: „Nie in Standby versetzen“").assertDoesNotExist()
        }
    }

    @Test
    fun `a reminder to try out comes 10 seconds later, as a reminder does, also with the app closed`() {
        allowEverything()
        val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))

        launchApp().use {
            openStatus()
            compose.onNodeWithText("Probe-Erinnerung in 10 Sekunden").performScrollTo().performClick()
            compose.onNodeWithText("Die Probe-Erinnerung kommt in 10 Sekunden", substring = true).performScrollTo()
                .assertIsDisplayed()
        }

        val alarm = checkNotNull(alarmManager.peekNextScheduledAlarm())
        assertEquals((app.clock.now + Duration.ofSeconds(10)).toEpochMilli(), alarm.triggerAtMs)
        app.clock.now = app.clock.now + Duration.ofSeconds(10)
        alarmManager.fireAlarm(alarm)
        shadowOf(Looper.getMainLooper()).idle()

        val shown = app.getSystemService(NotificationManager::class.java).activeNotifications.map { it.notification }
        assertEquals(listOf("Probe-Erinnerung"), shown.map { it.extras.getString(Notification.EXTRA_TITLE) })
        assertEquals(listOf("reminders"), shown.map { it.channelId })
        // On the lock screen as a reminder is: without what it is about.
        assertEquals(listOf(Notification.VISIBILITY_PRIVATE), shown.map { it.visibility })
        assertEquals(listOf("Erinnerung fällig"), shown.map { it.publicVersion?.extras?.getString(Notification.EXTRA_TITLE) })
    }

    @Test
    fun `without notifications allowed, sending a reminder to try out says it will not show`() {
        launchApp().use {
            openStatus()
            compose.onNodeWithText("Probe-Erinnerung in 10 Sekunden").performScrollTo().performClick()

            compose.onNodeWithText("Ohne Benachrichtigungen erscheint auch die Probe-Erinnerung nicht.").performScrollTo()
                .assertIsDisplayed()
        }
    }

    private fun openStatus() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Funktioniert alles?").performScrollTo().performClick()
    }

    /** Taps [button] and tells what it opened in Android's settings. */
    private fun opens(button: String): Intent {
        compose.onNodeWithText(button).performScrollTo().performClick()
        return checkNotNull(shadowOf(app).nextStartedActivity)
    }

    private fun allowEverything() {
        shadowOf(app).grantPermissions(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.fullScreenAlarmsAllowed = true
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
    }
}
