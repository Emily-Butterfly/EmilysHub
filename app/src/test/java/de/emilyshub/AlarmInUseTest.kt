package de.emilyshub

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.AlarmActivity
import de.emilyshub.reminders.AlarmRingService
import de.emilyshub.routines.OccurrenceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A full-screen alarm while the phone or tablet is in use: Android then shows only its reminder (SPEC.md §3),
 * which rings like an alarm clock all the same and opens the alarm when tapped.
 */
@RunWith(AndroidJUnit4::class)
class AlarmInUseTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Before
    fun aFullScreenRoutineGoesOff() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.fullScreenAlarmsAllowed = true
        app.routine("Arzttermin", "07:30", fullScreen = true)
        val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
        app.clock.now = LocalDateTime.parse("2026-10-23T07:30").atZone(ZoneId.of("Europe/Berlin")).toInstant()
        alarmManager.fireAlarm(checkNotNull(alarmManager.peekNextScheduledAlarm()))
        idle()
    }

    @Test
    fun `a full-screen alarm rings like an alarm clock in the background, also where Android shows only the reminder`() {
        val ringing = ringing()

        assertTrue("ringing", app.ringer.ringing)
        val notification = checkNotNull(shadowOf(ringing.get()).lastForegroundNotification) { "Not in the foreground" }
        assertEquals("Alarm klingelt", notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `it stops ringing once the reminder is answered, say with Erledigt on the reminder`() {
        val ringing = ringing()

        reminder().actions.single { it.title == "Erledigt" }.actionIntent.send()
        idle()

        assertFalse("ringing", app.ringer.ringing)
        assertTrue("stopped", shadowOf(ringing.get()).isStoppedBySelf)
        assertEquals(listOf(OccurrenceStatus.Done), statuses())
    }

    @Test
    fun `Stumm silences it, and the reminder stays`() {
        val ringing = ringing()

        val notification = checkNotNull(shadowOf(ringing.get()).lastForegroundNotification)
        notification.actions.single { it.title == "Stumm" }.actionIntent.send()
        idle()

        assertEquals(AlarmRingService::class.java.name, shadowOf(app).nextStoppedService?.component?.className)
        ringing.destroy()
        assertFalse("ringing", app.ringer.ringing)
        assertEquals(listOf("Arzttermin"), reminders().map { it.extras.getString(Notification.EXTRA_TITLE) })
    }

    @Test
    fun `after ten minutes it stops ringing by itself, and the reminder stays`() {
        val ringing = ringing()

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))

        assertFalse("ringing", app.ringer.ringing)
        assertTrue("stopped", shadowOf(ringing.get()).isStoppedBySelf)
        assertEquals(listOf("Arzttermin"), reminders().map { it.extras.getString(Notification.EXTRA_TITLE) })
    }

    @Test
    fun `tapping the reminder opens the alarm with the routine and its actions, without ringing anew`() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)

        ActivityScenario.launch<AlarmActivity>(shadowOf(reminder().contentIntent).savedIntent).use {
            compose.onNodeWithText("Arzttermin").assertIsDisplayed()
            assertFalse("ringing", app.ringer.ringing)

            compose.onNodeWithText("Erledigt").performClick()
            idle()
        }
        assertEquals(listOf(OccurrenceStatus.Done), statuses())
    }

    @Test
    fun `silencing the full-screen alarm silences the ringing in the background too`() {
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        val alarm = Intent(shadowOf(checkNotNull(reminder().fullScreenIntent)).savedIntent)

        ActivityScenario.launch<AlarmActivity>(alarm).use {
            compose.onNodeWithText("Schließen").performClick()
            idle()
        }

        assertEquals(AlarmRingService::class.java.name, shadowOf(app).nextStoppedService?.component?.className)
    }

    /** Starts the ringing the way Android does, with the intent the alarm started it with. */
    private fun ringing(): ServiceController<AlarmRingService> {
        val started = checkNotNull(shadowOf(app).nextStartedService) { "Nothing rings" }
        assertEquals(AlarmRingService::class.java.name, started.component?.className)
        return Robolectric.buildService(AlarmRingService::class.java, started).create().startCommand(0, 1).also { idle() }
    }

    private fun reminder() = reminders().single()

    private fun reminders(): List<Notification> =
        app.getSystemService(NotificationManager::class.java).activeNotifications
            .map { it.notification }
            .filter { it.channelId == "reminders" }

    private fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
