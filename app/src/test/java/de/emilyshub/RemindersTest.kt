package de.emilyshub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.ReminderReceiver
import de.emilyshub.reminders.RescheduleReceiver
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class RemindersTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val notificationManager = app.getSystemService(NotificationManager::class.java)

    @Before
    fun allowExactAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `a new routine sets an exact alarm for its time, which only the app can trigger`() {
        app.routine("Vitamine", "07:30")

        val alarm = nextAlarm()
        assertEquals(berlin("2026-10-23T07:30"), Instant.ofEpochMilli(alarm.triggerAtMs))
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
        assertTrue("goes off in Doze too", alarm.isAllowWhileIdle)
        assertSentOnlyTo(ReminderReceiver::class.java.name, checkNotNull(alarm.operation))
    }

    @Test
    fun `at its time a reminder pops up with its three actions, and the alarm moves on to the next one`() {
        app.routine("Vitamine", "07:30")

        goOffAt("2026-10-23T07:30")

        val reminder = reminders().single()
        assertEquals("Vitamine", reminder.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Fällig um 07:30", reminder.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(Notification.CATEGORY_REMINDER, reminder.category)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, notificationManager.getNotificationChannel(reminder.channelId).importance)
        assertEquals(listOf("Erledigt", "Später", "Überspringen"), reminder.actions.map { it.title.toString() })
        reminder.actions.forEach { assertSentOnlyTo(ReminderReceiver::class.java.name, it.actionIntent) }
        assertSentOnlyTo(MainActivity::class.java.name, reminder.contentIntent)
        assertEquals(berlin("2026-10-24T07:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `on the lock screen a reminder only says that one is due, and its actions need the phone unlocked`() {
        app.routine("Arzttermin", "07:30")

        goOffAt("2026-10-23T07:30")

        val reminder = reminders().single()
        assertEquals(Notification.VISIBILITY_PRIVATE, reminder.visibility)
        assertEquals("Erinnerung fällig", reminder.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertNull(reminder.publicVersion.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(reminder.publicVersion.actions.isNullOrEmpty())
        assertTrue(reminder.actions.all { it.isAuthenticationRequired })
    }

    @Test
    fun `routines due at the same time each get a reminder of their own`() {
        app.routine("Vitamine", "07:30")
        app.routine("Lüften", "07:30")

        goOffAt("2026-10-23T07:30")

        assertEquals(setOf("Vitamine", "Lüften"), reminders().map { it.extras.getString(Notification.EXTRA_TITLE) }.toSet())
    }

    @Test
    fun `done in the reminder ticks the routine off and takes the reminder away`() {
        app.routine("Vitamine", "07:30")
        goOffAt("2026-10-23T07:30")

        tap("Erledigt")

        assertEquals(listOf(OccurrenceStatus.Done), statuses())
        assertEquals(emptyList<Notification>(), reminders())
    }

    @Test
    fun `skipping in the reminder skips the routine and takes the reminder away`() {
        app.routine("Vitamine", "07:30")
        goOffAt("2026-10-23T07:30")

        tap("Überspringen")

        assertEquals(listOf(OccurrenceStatus.Skipped), statuses())
        assertEquals(emptyList<Notification>(), reminders())
    }

    @Test
    fun `putting the reminder off brings it back in ten minutes`() {
        app.routine("Vitamine", "07:30")
        app.routine("Tagebuch", "20:00")
        goOffAt("2026-10-23T07:30")
        app.clock.now = berlin("2026-10-23T07:32")

        tap("Später")

        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(listOf(OccurrenceStatus.Open, OccurrenceStatus.Open), statuses())
        assertEquals(berlin("2026-10-23T07:42"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))

        goOffAt("2026-10-23T07:42")
        assertEquals(listOf("Vitamine"), reminders().map { it.extras.getString(Notification.EXTRA_TITLE) })
    }

    @Test
    fun `a reminder goes away when its routine is ticked off or deleted in the app`() = runBlocking {
        app.routine("Vitamine", "07:30")
        val walk = app.routine("Spaziergang", "07:30")
        goOffAt("2026-10-23T07:30")

        app.routines.markDone(app.routines.dueOn(app.routines.today()).first().first { it.name == "Vitamine" }.occurrenceId)
        assertEquals(listOf("Spaziergang"), reminders().map { it.extras.getString(Notification.EXTRA_TITLE) })

        app.routines.delete(walk)
        assertEquals(emptyList<Notification>(), reminders())
    }

    @Test
    fun `after a restart or an app update the alarm is set again`() {
        app.routine("Vitamine", "07:30")

        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            alarmManager.cancel(checkNotNull(nextAlarm().operation))
            app.sendBroadcast(Intent(action))
            idle()

            assertEquals(action, berlin("2026-10-23T07:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        }
    }

    @Test
    fun `after a change of time zone the alarm goes off at the routine's time there`() {
        app.routine("Vitamine", "07:30")

        app.clock.timeZone = ZoneId.of("Asia/Tokyo") // 14:00 there, so 07:30 has passed
        app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
        idle()

        assertEquals(Instant.parse("2026-10-23T22:30:00Z"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `the receivers do nothing for anything but their own actions`() = runBlocking {
        app.routine("Vitamine", "07:30")
        val occurrence = app.routines.dueOn(app.routines.today()).first().single().occurrenceId
        alarmManager.cancel(checkNotNull(nextAlarm().operation))

        app.sendBroadcast(Intent(app, ReminderReceiver::class.java).setAction(OTHER_ACTION).putExtra("occurrence", occurrence))
        app.sendBroadcast(Intent(app, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_DONE))
        app.sendBroadcast(Intent(app, RescheduleReceiver::class.java).setAction(OTHER_ACTION))
        idle()

        assertEquals(listOf(OccurrenceStatus.Open), statuses())
        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), shadowOf(alarmManager).scheduledAlarms)
    }

    @Test
    fun `without the permission for exact alarms the reminder still comes, if not to the minute`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        app.routine("Vitamine", "07:30")

        val alarm = nextAlarm()
        assertEquals(berlin("2026-10-23T07:30"), Instant.ofEpochMilli(alarm.triggerAtMs))
        assertNotEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
    }

    @Test
    fun `an alarm that only goes off long after its time leaves what was due then on Heute`() {
        app.routine("Vitamine", "07:30")

        goOffAt("2026-10-23T09:00") // after the clock was put forward, say

        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(listOf(OccurrenceStatus.Open), statuses())
    }

    @Test
    fun `a reminder that came due just as the alarm was set anew still comes`() {
        app.routine("Vitamine", "07:30")
        app.clock.now = berlin("2026-10-23T07:30:20")

        app.routine("Tagebuch", "20:00") // a change before the alarm for 07:30 could go off

        assertEquals(berlin("2026-10-23T07:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-10-23T07:30:30")
        assertEquals(listOf("Vitamine"), titles())
    }

    @Test
    fun `after a restart, a reminder put off to a time while the phone was off still comes`() {
        app.routine("Vitamine", "07:30")
        goOffAt("2026-10-23T07:30")
        app.clock.now = berlin("2026-10-23T07:32")
        tap("Später")

        // The phone restarts at 07:40 and is unlocked at 07:50; the restart took the alarm with it.
        alarmManager.cancel(checkNotNull(nextAlarm().operation))
        app.clock.now = berlin("2026-10-23T07:50")
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        idle()

        goOffAt("2026-10-23T07:50")
        assertEquals(listOf("Vitamine"), titles())
    }

    @Test
    fun `a reminder on the screen follows a new name of its routine`() = runBlocking {
        val pills = app.routine("Tabletten", "07:30")
        goOffAt("2026-10-23T07:30")

        app.routines.update(pills, RoutineDraft("Vitamin D", "", listOf(LocalTime.of(7, 30)), Repetition.Daily))

        assertEquals(listOf("Vitamin D"), titles())
    }

    @Test
    fun `a reminder put off days after it came due comes back`() {
        app.routine("Arzttermin", "07:30", repetition = Repetition.Once(LocalDate.parse("2026-10-23")))
        goOffAt("2026-10-23T07:30")
        app.clock.now = berlin("2026-10-25T08:00")

        tap("Später")

        assertEquals(berlin("2026-10-25T08:10"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-10-25T08:10")
        assertEquals(listOf("Arzttermin"), titles())
    }

    private fun nextAlarm() = checkNotNull(shadowOf(alarmManager).peekNextScheduledAlarm()) { "No alarm set" }

    /** Moves the clock to [localDateTime] in Berlin and lets the alarm set for the next reminder go off. */
    private fun goOffAt(localDateTime: String) {
        val alarm = nextAlarm()
        app.clock.now = berlin(localDateTime)
        shadowOf(alarmManager).fireAlarm(alarm)
        idle()
    }

    private fun tap(action: String) {
        reminders().single().actions.single { it.title == action }.actionIntent.send()
        idle()
    }

    private fun reminders(): List<Notification> = notificationManager.activeNotifications.map { it.notification }

    private fun titles() = reminders().map { it.extras.getString(Notification.EXTRA_TITLE) }

    private fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun assertSentOnlyTo(className: String, pendingIntent: PendingIntent) {
        val shadow = shadowOf(pendingIntent)
        assertTrue("immutable", shadow.isImmutable)
        assertEquals(className, shadow.savedIntent.component?.className)
    }

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant()

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
        const val OTHER_ACTION = "de.emilyshub.action.OTHER"
    }
}
