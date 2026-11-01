package de.emilyshub

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import de.emilyshub.reminders.AlarmActivity
import de.emilyshub.reminders.AlarmRingService
import de.emilyshub.todos.Due
import de.emilyshub.todos.Recurrence
import de.emilyshub.todos.WakeTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * A to-do with a full-screen alarm (SPEC.md §3, §5): its reminder rings like an alarm clock wherever it comes, at its
 * time, after "Später" and on arrival at one of its places, with "Erledigt · Später" only.
 */
@RunWith(AndroidJUnit4::class)
class TodoAlarmTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val today = LocalDate.parse("2026-10-23")

    @Before
    fun fullScreenAlarmsAreAllowed() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.fullScreenAlarmsAllowed = true
    }

    @Test
    fun `at its time, a full-screen to-do comes as an alarm that rings`() {
        app.todo("Arzt anrufen", Due.At(today, LocalTime.of(9, 0)), fullScreen = true)

        goOffAt("2026-10-23T09:00")

        assertNotNull("full-screen", reminder("Arzt anrufen").fullScreenIntent)
        assertEquals(Notification.CATEGORY_ALARM, reminder("Arzt anrufen").category)
        assertRings()
    }

    @Test
    fun `without the switch, a to-do only comes as a notification`() {
        app.todo("Arzt anrufen", Due.At(today, LocalTime.of(9, 0)))

        goOffAt("2026-10-23T09:00")

        assertNull(reminder("Arzt anrufen").fullScreenIntent)
        assertEquals(Notification.CATEGORY_REMINDER, reminder("Arzt anrufen").category)
        assertNull(shadowOf(app).nextStartedService)
    }

    @Test
    fun `put off, it rings again when it comes back`() {
        app.todo("Arzt anrufen", Due.At(today, LocalTime.of(9, 0)), fullScreen = true)
        goOffAt("2026-10-23T09:00")
        openAlarm("Arzt anrufen").use { scenario ->
            compose.onNodeWithText("Später").performClick()
            compose.onNodeWithText("30 Min.").performClick()
            idle()
            scenario.onActivity { assertTrue("closed", it.isFinishing) }
        }
        assertEquals(emptyList<String>(), titles())
        shadowOf(app).clearStartedServices()

        goOffAt("2026-10-23T09:30")

        assertNotNull("full-screen", reminder("Arzt anrufen").fullScreenIntent)
        assertRings()
    }

    @Test
    fun `on arriving at one of its places, it rings, also without a time`() {
        val connectivity = shadowOf(app.getSystemService(ConnectivityManager::class.java))
        val home = app.place("Zuhause", "FritzBox")
        app.todo("Pakete", Due.On(today), places = setOf(home), fullScreen = true)
        app.wifi.ssid = "Firma"
        idle()

        app.clock.now = berlin("2026-10-23T18:00")
        app.wifi.ssid = "FritzBox"
        connectivity.networkCallbackPendingIntents.single().send()
        idle()
        goOffAt("2026-10-23T18:00")

        assertNotNull("full-screen", reminder("Pakete").fullScreenIntent)
        assertRings()
    }

    @Test
    fun `unlocked, the alarm shows the to-do with only done and later, and on the lock screen only that one is due`() {
        app.todo("Arzt anrufen", Due.At(today, LocalTime.of(9, 0)), fullScreen = true)
        goOffAt("2026-10-23T09:00")
        keyguard.setKeyguardLocked(true)

        openAlarm("Arzt anrufen").use {
            compose.onNodeWithText("Erinnerung fällig").assertIsDisplayed()
            compose.onNodeWithText("Arzt anrufen").assertDoesNotExist()
            listOf("Entsperren", "Schließen").forEach { compose.onNodeWithText(it).assertIsDisplayed() }

            keyguard.setKeyguardLocked(false)
            app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
            idle()

            compose.onNodeWithText("Arzt anrufen").assertIsDisplayed()
            compose.onNodeWithText("Fällig: heute, 09:00").assertIsDisplayed()
            listOf("Erledigt", "Später").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
            listOf("Überspringen", "Heute nicht").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        }
    }

    @Test
    fun `Erledigt in the alarm ticks the to-do off, silences it and takes the reminder away`() {
        val id = app.todo("Arzt anrufen", Due.At(today, LocalTime.of(9, 0)), fullScreen = true)
        goOffAt("2026-10-23T09:00")

        openAlarm("Arzt anrufen").use { scenario ->
            assertTrue("ringing", app.ringer.ringing)
            compose.onNodeWithText("Erledigt").performClick()
            idle()

            scenario.onActivity { assertTrue("closed", it.isFinishing) }
        }
        assertFalse(app.ringer.ringing)
        assertNotNull("done", runBlocking { app.todos.get(id) }?.doneAt)
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun `a routine and a to-do take turns in the same alarm`() {
        app.routine("Tabletten", "09:00", fullScreen = true)
        app.todo("Arzt anrufen", Due.At(today, LocalTime.of(9, 5)), fullScreen = true)
        goOffAt("2026-10-23T09:00")

        openAlarm("Tabletten").use { scenario ->
            goOffAt("2026-10-23T09:05")
            scenario.onActivity { getInstrumentation().callActivityOnNewIntent(it, alarmIntent("Arzt anrufen")) }

            compose.onNodeWithText("Arzt anrufen").assertIsDisplayed()
            compose.onNodeWithText("Erledigt").performClick()
            idle()

            compose.onNodeWithText("Tabletten").assertIsDisplayed()
            compose.onNodeWithText("Überspringen").assertIsDisplayed()
            scenario.onActivity { assertFalse("still open", it.isFinishing) }
        }
    }

    @Test
    fun `the ringing in the background stops once the to-do is done somewhere else, its reminder staying till then`() {
        val id = app.todo("Arzt anrufen", Due.At(today, LocalTime.of(9, 0)), fullScreen = true)
        goOffAt("2026-10-23T09:00")
        val ringing = Robolectric.buildService(AlarmRingService::class.java, shadowOf(app).nextStartedService).create()
        ringing.startCommand(0, 0)
        assertTrue("ringing", app.ringer.ringing)

        runBlocking { app.todos.markDone(id) }
        idle()

        assertFalse("ringing", app.ringer.ringing)
        assertTrue("stopped", shadowOf(ringing.get()).isStoppedBySelf)
    }

    @Test
    fun `at its wake time it rings as an alarm, and again when it is due`() {
        app.todo(
            "Müll",
            Due.At(today.plusDays(1), LocalTime.of(7, 0)),
            fullScreen = true,
            wake = WakeTime.Before(1, LocalTime.of(18, 0)),
        )

        goOffAt("2026-10-23T18:00")
        assertNotNull("full-screen", reminder("Müll").fullScreenIntent)
        assertRings()
        shadowOf(app).clearStartedServices()

        goOffAt("2026-10-24T07:00")
        assertNotNull("full-screen", reminder("Müll").fullScreenIntent)
        assertRings()
    }

    @Test
    fun `the next of a series has a full-screen alarm too`() {
        val id = app.todo("Blumen", Due.At(today, LocalTime.of(9, 0)), recurrence = Recurrence.Daily, fullScreen = true)

        runBlocking { app.todos.markDone(id) }

        val next = runBlocking { app.todos.open().first() }.single().todo
        assertEquals(today.plusDays(1), next.dueDate)
        assertTrue(next.fullScreen)
    }

    private fun assertRings() {
        assertEquals(AlarmRingService::class.java.name, shadowOf(app).nextStartedService?.component?.className)
    }

    private fun openAlarm(of: String): ActivityScenario<AlarmActivity> = ActivityScenario.launch(alarmIntent(of))

    private fun alarmIntent(of: String): Intent =
        shadowOf(checkNotNull(reminder(of).fullScreenIntent) { "No full-screen alarm" }).savedIntent

    private fun reminder(title: String): Notification = reminders().single { it.extras.getString(Notification.EXTRA_TITLE) == title }

    private fun titles() = reminders().map { it.extras.getString(Notification.EXTRA_TITLE) }

    private fun reminders(): List<Notification> =
        app.getSystemService(NotificationManager::class.java).activeNotifications.map { it.notification }
            .filter { it.channelId == "reminders" }

    /** Moves the clock to [localDateTime] in Berlin and lets the alarm set for the next reminder go off. */
    private fun goOffAt(localDateTime: String) {
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(checkNotNull(alarmManager.peekNextScheduledAlarm()))
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Europe/Berlin")).toInstant()
}
