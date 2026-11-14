package de.emilyshub

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.ReminderReceiver
import de.emilyshub.reminders.RescheduleReceiver
import de.emilyshub.reminders.RunNotification
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration

/** The notification of a run running, and the alarm for the end of its step (SPEC.md §2, Ablauf-Modus; #102). */
@RunWith(AndroidJUnit4::class)
class RunNotificationTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before
    fun allowNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `a run running stands quietly, with the step's countdown, Fertig and Beenden`() {
        val occurrence = startRun(nameOnLockScreen = true)

        val notification = checkNotNull(shown())
        assertEquals("Morgens", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Wasser", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue("stays", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(RunNotification.CHANNEL, notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(RunNotification.CHANNEL).importance)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals((app.clock.now + Duration.ofMinutes(5)).toEpochMilli(), notification.`when`)
        assertEquals(listOf("Fertig", "Beenden"), notification.actions.map { it.title.toString() })
        assertTrue(occurrence > 0)
    }

    @Test
    fun `without the routine's name on the lock screen, it says only Ablauf laeuft and the step`() {
        startRun(nameOnLockScreen = false)

        val notification = checkNotNull(shown())
        assertEquals("Ablauf läuft", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Schritt 1 von 2", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
    }

    @Test
    fun `an exact alarm comes at the end of the step, after which it alerts once, as overtime`() {
        startRun()
        val alarm = alarms.alarmsFor(ReminderReceiver.ACTION_RUN_STEP_END).single()
        assertEquals((app.clock.now + Duration.ofMinutes(5)).toEpochMilli(), alarm.triggerAtTime)

        app.clock.now = app.clock.now + Duration.ofMinutes(5)
        alarms.fireAlarm(alarm)
        idle()

        val notification = checkNotNull(shown())
        assertEquals(RunNotification.ALERT_CHANNEL, notification.channelId)
        assertEquals("Überzeit", notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(alarms.alarmsFor(ReminderReceiver.ACTION_RUN_STEP_END).isEmpty())

        // Updated again, it stays quiet: no following up.
        runBlocking { app.updateRun() }
        assertTrue(checkNotNull(shown()).flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
    }

    @Test
    fun `Fertig there ticks the step off and shows the next, the last one taking it away`() {
        val occurrence = startRun()
        val item = runBlocking { app.routines.dueOn(app.routines.today()).first().single().checklist.first().itemId }

        send(ReminderReceiver.ACTION_RUN_DONE, occurrence, item)

        assertEquals("Zähne", checkNotNull(shown()).extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        // Without minutes, the run's stopwatch counts.
        assertFalse(checkNotNull(shown()).extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(alarms.alarmsFor(ReminderReceiver.ACTION_RUN_STEP_END).isEmpty())

        val next = runBlocking { app.routines.dueOn(app.routines.today()).first().single().checklist.last().itemId }
        send(ReminderReceiver.ACTION_RUN_DONE, occurrence, next)

        assertNull(shown())
        assertEquals(true, runBlocking { app.routines.dueOn(app.routines.today()).first().single().checklist.all { it.ticked } })
    }

    @Test
    fun `Beenden takes it away and leaves the ticks`() {
        val occurrence = startRun()

        send(ReminderReceiver.ACTION_RUN_STOP, occurrence, item = 1)

        assertNull(shown())
        assertNull(runBlocking { app.runs.current() })
        assertTrue(alarms.alarmsFor(ReminderReceiver.ACTION_RUN_STEP_END).isEmpty())
    }

    @Test
    fun `after a restart of the phone it stands again, with its time as it was`() {
        startRun()
        manager.cancelAll()
        // A restart drops every alarm.
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        alarms.scheduledAlarms.toList().mapNotNull { it.operation }.forEach(alarmManager::cancel)
        app.clock.now = app.clock.now + Duration.ofMinutes(2)

        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, RescheduleReceiver::class.java))
        idle()

        val notification = checkNotNull(shown())
        assertEquals((app.clock.now + Duration.ofMinutes(3)).toEpochMilli(), notification.`when`)
        assertEquals(1, alarms.alarmsFor(ReminderReceiver.ACTION_RUN_STEP_END).size)
    }

    @Test
    fun `once the occurrence ends otherwise, it goes`() {
        val occurrence = startRun()

        runBlocking { app.routines.skip(occurrence) }

        assertNull(shown())
        assertTrue(alarms.alarmsFor(ReminderReceiver.ACTION_RUN_STEP_END).isEmpty())
    }

    @Test
    fun `without notifications allowed the run goes on in the app alone`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val occurrence = startRun()

        assertNull(shown())
        assertEquals(occurrence, runBlocking { app.runs.current() }?.occurrenceId)
    }

    /** A routine with two steps, the first of five minutes, its run started; its occurrence. */
    private fun startRun(nameOnLockScreen: Boolean = true): Long {
        val routine = app.routine(
            "Morgens", "08:00", checklist = listOf("Wasser", "Zähne"), minutes = listOf(5, null), nameOnLockScreen = nameOnLockScreen,
        )
        return runBlocking {
            val occurrence = app.routines.dueOn(app.routines.today()).first().single().occurrenceId
            app.runs.start(occurrence, routine)
            occurrence
        }
    }

    private fun send(action: String, occurrence: Long, item: Long) {
        val intent = Intent(action).setClass(app, ReminderReceiver::class.java)
            .putExtra("occurrence", occurrence)
            .putExtra(RunNotification.EXTRA_ITEM, item)
        app.sendBroadcast(intent)
        idle()
    }

    private fun shown(): Notification? = manager.activeNotifications.find { it.tag == RunNotification.TAG }?.notification

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
