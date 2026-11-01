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
import de.emilyshub.routines.FollowUps
import de.emilyshub.routines.OccurrenceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * Coupled routines due at the same time (SPEC.md §3): one reminder for all of them. Its notification asks after
 * "Erledigt" which one it was; its full-screen alarm has a "done" for each; "Später", "Überspringen" and "Heute nicht"
 * are for all of them together.
 */
@RunWith(AndroidJUnit4::class)
class JointReminderTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))

    @Before
    fun allowExactAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `coupled routines due at the same time come as one reminder, with done, later and skip`() {
        coupled("Laufen", "Yoga")

        goOffAt("2026-10-23T07:30")

        val reminder = reminders().single()
        assertEquals("Laufen · Yoga", reminder.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(listOf("Erledigt", "Später", "Überspringen"), reminder.actions.map { it.title.toString() })
    }

    @Test
    fun `routines due at the same time but not coupled each come on their own`() {
        app.routine("Laufen", "07:30")
        app.routine("Yoga", "07:30")

        goOffAt("2026-10-23T07:30")

        assertEquals(setOf("Laufen", "Yoga"), reminders().map { it.extras.getString(Notification.EXTRA_TITLE) }.toSet())
    }

    @Test
    fun `done asks which one it was, and that one done skips the other, the reminder going`() {
        coupled("Laufen", "Yoga")
        goOffAt("2026-10-23T07:30")

        tap("Erledigt")
        val choice = reminders().single()
        assertEquals("Welche war es?", choice.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(listOf("Laufen", "Yoga"), choice.actions.map { it.title.toString() })
        assertTrue(choice.actions.all { it.isAuthenticationRequired })

        tap("Yoga")

        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(mapOf("Laufen" to OccurrenceStatus.Skipped, "Yoga" to OccurrenceStatus.Done), statuses())
    }

    @Test
    fun `skip is for all of them`() {
        coupled("Laufen", "Yoga")
        goOffAt("2026-10-23T07:30")

        tap("Überspringen")

        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(mapOf("Laufen" to OccurrenceStatus.Skipped, "Yoga" to OccurrenceStatus.Skipped), statuses())
    }

    @Test
    fun `later is for all of them, which come back together`() {
        coupled("Laufen", "Yoga")
        goOffAt("2026-10-23T07:30")

        tap("Später")
        tap("10 Min.")
        assertEquals(emptyList<Notification>(), reminders())

        goOffAt("2026-10-23T07:40")
        assertEquals(listOf("Laufen · Yoga"), reminders().map { it.extras.getString(Notification.EXTRA_TITLE) })
    }

    @Test
    fun `on the lock screen the names show only if each of them allows it`() {
        val running = app.routine("Laufen", "07:30", nameOnLockScreen = true)
        app.routine("Yoga", "07:30", coupledWith = setOf(running))
        goOffAt("2026-10-23T07:30")
        assertEquals("Erinnerung fällig", reminders().single().publicVersion.extras.getString(Notification.EXTRA_TITLE))

        runBlocking { app.routines.markDone(app.routines.dueOn(app.routines.today()).first().first().occurrenceId) }
        val swim = app.routine("Schwimmen", "18:00", nameOnLockScreen = true)
        app.routine("Rad", "18:00", nameOnLockScreen = true, coupledWith = setOf(swim))
        goOffAt("2026-10-23T18:00")
        assertEquals("Rad · Schwimmen", reminders().single().publicVersion.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `the full-screen alarm has a done for each, and later, skip and Heute nicht for all`() {
        app.fullScreenAlarmsAllowed = true
        coupled("Laufen", "Yoga", fullScreen = true)
        goOffAt("2026-10-23T07:30")
        keyguard.setKeyguardLocked(false)

        openAlarm().use {
            compose.onNodeWithText("Laufen · Yoga").assertIsDisplayed()
            listOf("Laufen erledigt", "Yoga erledigt", "Später", "Überspringen", "Heute nicht").forEach {
                compose.onNodeWithText(it).assertIsDisplayed()
            }
            compose.onNodeWithText("Erledigt").assertDoesNotExist()

            compose.onNodeWithText("Laufen erledigt").performClick()
            idle()
        }
        assertEquals(mapOf("Laufen" to OccurrenceStatus.Done, "Yoga" to OccurrenceStatus.Skipped), statuses())
    }

    @Test
    fun `Heute nicht in the alarm drops all of them for the day`() {
        app.fullScreenAlarmsAllowed = true
        coupled("Laufen", "Yoga", fullScreen = true)
        goOffAt("2026-10-23T07:30")
        keyguard.setKeyguardLocked(false)

        openAlarm().use {
            compose.onNodeWithText("Heute nicht").performClick()
            idle()
        }
        assertEquals(mapOf("Laufen" to OccurrenceStatus.Dropped, "Yoga" to OccurrenceStatus.Dropped), statuses())
    }

    /** Two routines at 07:30 coupled with each other, without follow-ups, so that each alarm goes off for one only. */
    private fun coupled(first: String, second: String, fullScreen: Boolean = false) {
        val one = app.routine(first, "07:30", followUps = FollowUps(count = 0), fullScreen = fullScreen)
        app.routine(second, "07:30", followUps = FollowUps(count = 0), fullScreen = fullScreen, coupledWith = setOf(one))
    }

    private fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first() }.associate { it.name to it.status }

    private fun openAlarm(): ActivityScenario<AlarmActivity> {
        val reminder = reminders().single()
        return ActivityScenario.launch(shadowOf(checkNotNull(reminder.fullScreenIntent) { "No full-screen alarm" }).savedIntent)
    }

    /** Taps [action] on the reminder on the screen. */
    private fun tap(action: String) {
        reminders().single().actions.single { it.title == action }.actionIntent.send()
        idle()
    }

    private fun reminders(): List<Notification> =
        notificationManager.activeNotifications.map { it.notification }.filter { it.channelId != "alarm-ringing" }

    private fun goOffAt(localDateTime: String) {
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(checkNotNull(alarmManager.peekNextScheduledAlarm()))
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Europe/Berlin")).toInstant()
}
