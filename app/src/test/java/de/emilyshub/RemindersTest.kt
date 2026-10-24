package de.emilyshub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.AlarmActivity
import de.emilyshub.reminders.ReminderReceiver
import de.emilyshub.reminders.RescheduleReceiver
import de.emilyshub.routines.FollowUps
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
        assertEquals("the first follow-up", berlin("2026-10-23T08:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
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
    fun `later offers 10 min, 30 min and 1 h, and the reminder comes back after the time chosen`() {
        app.routine("Vitamine", "07:30")
        app.routine("Tagebuch", "20:00")
        goOffAt("2026-10-23T07:30")
        app.clock.now = berlin("2026-10-23T07:32")

        tap("Später")

        val choice = reminders().single()
        assertEquals("Vitamine", choice.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Erneut erinnern in …", choice.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(listOf("10 Min.", "30 Min.", "1 Std."), choice.actions.map { it.title.toString() })
        choice.actions.forEach { assertSentOnlyTo(ReminderReceiver::class.java.name, it.actionIntent) }
        assertTrue(choice.actions.all { it.isAuthenticationRequired })
        assertTrue("without a sound", alertsOnlyOnce(choice))
        assertEquals("Erinnerung fällig", choice.publicVersion.extras.getString(Notification.EXTRA_TITLE))

        tap("30 Min.")

        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(listOf(OccurrenceStatus.Open, OccurrenceStatus.Open), statuses())
        assertEquals(berlin("2026-10-23T08:02"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-10-23T08:02")
        assertEquals(listOf("Vitamine"), titles())
    }

    @Test
    fun `each choice puts the reminder off by its time, and its follow-ups count from then`() {
        app.routine("Vitamine", "07:30")
        goOffAt("2026-10-23T07:32")

        for ((choice, back) in listOf("1 Std." to "08:32", "30 Min." to "09:02", "10 Min." to "09:12")) {
            putOff(choice)
            assertEquals(choice, berlin("2026-10-23T$back"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
            goOffAt("2026-10-23T$back")
            assertEquals(choice, listOf("Vitamine"), titles())
        }

        assertEquals(berlin("2026-10-23T09:42"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `the choice for later stays while the reminder is shown again quietly`() {
        app.routine("Vitamine", "07:30")
        goOffAt("2026-10-23T07:30")
        tap("Später")

        app.routine("Tagebuch", "20:00")

        assertEquals(listOf("10 Min.", "30 Min.", "1 Std."), reminders().single().actions.map { it.title.toString() })
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
        assertEquals("no follow-ups", berlin("2026-10-24T07:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `only a reminder that has come due can be put off`() = runBlocking {
        app.routine("Tagebuch", "20:00")
        val evening = app.routines.dueOn(app.routines.today()).first().single().occurrenceId

        app.sendBroadcast(
            Intent(app, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_PUT_OFF)
                .putExtra("occurrence", evening)
                .putExtra("minutes", 10L),
        )
        idle()

        assertEquals(berlin("2026-10-23T20:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
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
        val putOffByFive = Intent(app, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_PUT_OFF)
            .putExtra("occurrence", occurrence)
            .putExtra("minutes", 5L)
        app.sendBroadcast(putOffByFive) // none of the choices
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
    fun `an alarm going off late, while the time window is still open, still brings the reminder`() {
        app.routine("Vitamine", "07:30") // open until 11:30

        goOffAt("2026-10-23T10:30") // after the phone was off, say

        assertEquals(listOf("Vitamine"), titles())
        assertEquals(listOf(OccurrenceStatus.Open), statuses())
    }

    @Test
    fun `an alarm going off after the time window has ended marks the routine missed instead of reminding`() {
        app.routine("Vitamine", "07:30") // open until 11:30

        goOffAt("2026-10-23T12:00") // after the clock was put forward, say

        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(listOf(OccurrenceStatus.Missed), statuses())
    }

    @Test
    fun `once its time window ends, an occurrence left open is missed and its reminder taken away`() {
        app.routine("Vitamine", "07:30", followUps = FollowUps(count = 0))
        goOffAt("2026-10-23T07:30")

        assertEquals(berlin("2026-10-23T11:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-10-23T11:30")

        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(listOf(OccurrenceStatus.Missed), statuses())
        assertEquals(berlin("2026-10-24T07:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `after a restart, what ran out while the phone was off is missed`() {
        app.routine("Vitamine", "07:30")
        app.clock.now = berlin("2026-10-25T09:00") // off since Friday 07:00

        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        idle()

        val days = listOf("2026-10-23", "2026-10-24", "2026-10-25").map(LocalDate::parse)
        val (missed, open) = OccurrenceStatus.Missed to OccurrenceStatus.Open
        assertEquals(listOf(missed, missed, open), days.map { statuses(it).single() })
    }

    @Test
    fun `when the app starts, what ran out meanwhile is missed`() = runBlocking {
        app.routine("Vitamine", "07:30")
        app.clock.now = berlin("2026-10-23T12:00")

        app.reminders.update() // as the app does when it starts

        assertEquals(listOf(OccurrenceStatus.Missed), statuses())
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
        putOff("10 Min.")

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
    fun `a reminder put off past midnight comes back while its time window is open`() {
        app.routine("Lesen", "23:30", repetition = Repetition.Once(LocalDate.parse("2026-10-23")))
        goOffAt("2026-10-23T23:30")
        app.clock.now = berlin("2026-10-24T00:20")

        putOff("10 Min.")

        assertEquals(berlin("2026-10-24T00:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-10-24T00:30")
        assertEquals(listOf("Lesen"), titles())
    }

    @Test
    fun `an ignored reminder comes again every 30 minutes, three times, and then stays up without a sound till its window ends`() {
        app.routine("Vitamine", "07:30")
        goOffAt("2026-10-23T07:30")

        for (followUp in listOf("08:00", "08:30", "09:00")) {
            notificationManager.cancelAll() // swiped away, which ignores it too
            assertEquals(berlin("2026-10-23T$followUp"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
            goOffAt("2026-10-23T$followUp")
            assertEquals(followUp, listOf("Vitamine"), titles())
        }

        assertEquals("the end of its window", berlin("2026-10-23T11:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        app.routine("Tagebuch", "20:00") // any change shows it again, quietly
        assertEquals(listOf("Vitamine"), titles())
        assertTrue(alertsOnlyOnce(reminders().single()))
    }

    @Test
    fun `a follow-up pops up a reminder still up with a sound again, while other changes only update it quietly`() {
        app.routine("Vitamine", "07:30")
        goOffAt("2026-10-23T07:30")
        app.clock.now = berlin("2026-10-23T07:45")

        app.routine("Tagebuch", "20:00")
        assertTrue(alertsOnlyOnce(reminders().single()))

        goOffAt("2026-10-23T08:00")
        assertEquals(false, alertsOnlyOnce(reminders().single()))
    }

    @Test
    fun `a routine's own follow-ups come as often and as far apart as it says`() {
        app.routine("Wasser", "07:30", followUps = FollowUps(count = 1, everyMinutes = 5))
        goOffAt("2026-10-23T07:30")

        assertEquals(berlin("2026-10-23T07:35"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-10-23T07:35")
        assertEquals("the end of its window", berlin("2026-10-23T11:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `done or skipped in the reminder takes its follow-ups away`() {
        app.routine("Vitamine", "07:30")
        app.routine("Lüften", "07:30")
        goOffAt("2026-10-23T07:30")

        tap("Erledigt", on = "Vitamine")
        tap("Überspringen", on = "Lüften")

        assertEquals(berlin("2026-10-24T07:30"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `a routine with a full-screen alarm fills the screen with its reminder, which only the app's alarm can open`() {
        app.fullScreenAlarmsAllowed = true
        app.routine("Arzttermin", "07:30", fullScreen = true)
        app.routine("Vitamine", "07:30")

        goOffAt("2026-10-23T07:30")

        assertSentOnlyTo(AlarmActivity::class.java.name, checkNotNull(reminder("Arzttermin").fullScreenIntent))
        assertNull(reminder("Vitamine").fullScreenIntent)
    }

    @Test
    fun `a full-screen alarm counts as an alarm, which do not disturb lets through where it lets alarms through`() {
        app.fullScreenAlarmsAllowed = true
        app.routine("Arzttermin", "07:30", fullScreen = true)
        app.routine("Vitamine", "07:30")

        goOffAt("2026-10-23T07:30")

        assertEquals(Notification.CATEGORY_ALARM, reminder("Arzttermin").category)
        assertEquals(Notification.CATEGORY_REMINDER, reminder("Vitamine").category)
    }

    @Test
    fun `without Android's permission a full-screen alarm comes as a normal reminder`() {
        app.routine("Arzttermin", "07:30", fullScreen = true)

        goOffAt("2026-10-23T07:30")

        val reminder = reminder("Arzttermin")
        assertNull(reminder.fullScreenIntent)
        assertEquals(Notification.CATEGORY_REMINDER, reminder.category)
        assertEquals(listOf("Erledigt", "Später", "Überspringen"), reminder.actions.map { it.title.toString() })
    }

    @Test
    fun `a full-screen alarm fills the screen again at each follow-up, not when it is only shown again quietly`() {
        app.fullScreenAlarmsAllowed = true
        app.routine("Arzttermin", "07:30", fullScreen = true)
        goOffAt("2026-10-23T07:30")

        app.routine("Tagebuch", "20:00")
        assertNull(reminder("Arzttermin").fullScreenIntent)

        goOffAt("2026-10-23T08:00")
        assertNotNull(reminder("Arzttermin").fullScreenIntent)
    }

    private fun reminder(name: String) = reminders().single { it.extras.getString(Notification.EXTRA_TITLE) == name }

    private fun nextAlarm() = checkNotNull(shadowOf(alarmManager).peekNextScheduledAlarm()) { "No alarm set" }

    /** Moves the clock to [localDateTime] in Berlin and lets the alarm set for the next reminder go off. */
    private fun goOffAt(localDateTime: String) {
        val alarm = nextAlarm()
        app.clock.now = berlin(localDateTime)
        shadowOf(alarmManager).fireAlarm(alarm)
        idle()
    }

    /** Taps [action] on the reminder on the screen, or on the one of the routine named [on]. */
    private fun tap(action: String, on: String? = null) {
        val reminder = reminders().single { on == null || it.extras.getString(Notification.EXTRA_TITLE) == on }
        reminder.actions.single { it.title == action }.actionIntent.send()
        idle()
    }

    /** Taps "Später" on the reminder on the screen and then [choice]. */
    private fun putOff(choice: String) {
        tap("Später")
        tap(choice)
    }

    /** Whether showing it again, while it is up, makes no sound. */
    private fun alertsOnlyOnce(reminder: Notification) = reminder.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0

    private fun reminders(): List<Notification> = notificationManager.activeNotifications.map { it.notification }

    private fun titles() = reminders().map { it.extras.getString(Notification.EXTRA_TITLE) }

    private fun statuses(on: LocalDate = app.routines.today()) = runBlocking { app.routines.dueOn(on).first().map { it.status } }

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
