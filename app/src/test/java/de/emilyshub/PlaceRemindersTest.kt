package de.emilyshub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.AlarmRingService
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.TimeWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Routines bound to places (SPEC.md §4), with the app closed: they pop up at their time where the device is at one of
 * their places, wait elsewhere, and pop up on arrival while their time window is open.
 */
@RunWith(AndroidJUnit4::class)
class PlaceRemindersTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val connectivity = shadowOf(app.getSystemService(ConnectivityManager::class.java))
    private var home = 0L

    @Before
    fun aRoutineBoundToHome() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        home = app.place("Zuhause", "FritzBox")
        app.routine("Tabletten", "08:00", window = TimeWindow(hours = 2), places = setOf(home))
    }

    @Test
    fun `at home at its time, it pops up at once`() {
        app.wifi.ssid = "FritzBox"

        goOffAt("2026-10-23T08:00")

        assertEquals(listOf("Tabletten"), reminders())
    }

    @Test
    fun `elsewhere at its time, it waits, Heute says so, and it pops up on arriving home`() {
        app.wifi.ssid = "Firma"
        goOffAt("2026-10-23T08:00")
        assertEquals(emptyList<String>(), reminders())

        launchApp().use {
            compose.onNodeWithText("Wartet auf Zuhause").assertIsDisplayed()
        }

        app.clock.now = berlin("2026-10-23T09:10")
        app.wifi.ssid = "FritzBox"
        arrive()

        assertEquals(listOf("Tabletten"), reminders())
    }

    @Test
    fun `on arrival it pops up by the alarm, which lets a full-screen alarm ring also with the app closed`() {
        app.fullScreenAlarmsAllowed = true
        app.routine("Arzttermin", "08:00", fullScreen = true, places = setOf(home))
        app.wifi.ssid = null
        goOffAt("2026-10-23T08:00")

        app.clock.now = berlin("2026-10-23T09:10")
        app.wifi.ssid = "FritzBox"
        connectivity.networkCallbackPendingIntents.single().send()
        idle()
        assertEquals(emptyList<String>(), reminders())

        goOffAt("2026-10-23T09:10")
        assertEquals(listOf("Arzttermin", "Tabletten"), reminders().sortedBy { it })
        assertEquals(AlarmRingService::class.java.name, shadowOf(app).nextStartedService?.component?.className)
    }

    @Test
    fun `with its only place deleted, what waited for it pops up`() {
        app.wifi.ssid = null
        goOffAt("2026-10-23T08:00")

        app.clock.now = berlin("2026-10-23T08:05")
        runBlocking { app.places.delete(home) }
        goOffAt("2026-10-23T08:05")

        assertEquals(listOf("Tabletten"), reminders())
    }

    @Test
    fun `arriving home once its time window has ended, it is missed and nothing pops up`() {
        app.wifi.ssid = null
        goOffAt("2026-10-23T08:00")

        app.clock.now = berlin("2026-10-23T10:30")
        app.wifi.ssid = "FritzBox"
        arrive()

        assertEquals(emptyList<String>(), reminders())
        val statuses = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }
        assertEquals(listOf(OccurrenceStatus.Missed), statuses)
    }

    @Test
    fun `while a routine is bound to a place, Android wakes the app on joining a WLAN, also with the app closed`() {
        assertEquals(1, connectivity.networkCallbackPendingIntents.size)

        runBlocking { app.routines.delete(app.routines.inUse().first().single().id) }

        assertEquals(0, connectivity.networkCallbackPendingIntents.size)
    }

    /** Joins a WLAN: Android sends what the app asked it to, as the app may be closed, and the alarm set then goes off. */
    private fun arrive() {
        connectivity.networkCallbackPendingIntents.single().send()
        idle()
        alarmManager.fireAlarm(checkNotNull(alarmManager.peekNextScheduledAlarm()))
        idle()
    }

    /** Moves the clock to [localDateTime] in Berlin and lets the alarm set for the next reminder go off. */
    private fun goOffAt(localDateTime: String) {
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(checkNotNull(alarmManager.peekNextScheduledAlarm()))
        idle()
    }

    private fun reminders() = app.getSystemService(NotificationManager::class.java).activeNotifications
        .map { it.notification }
        .filter { it.channelId == "reminders" }
        .map { it.extras.getString(Notification.EXTRA_TITLE) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun berlin(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Europe/Berlin")).toInstant()
}
