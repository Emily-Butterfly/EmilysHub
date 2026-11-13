package de.emilyshub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.EXTRA_TODO
import de.emilyshub.reminders.ReminderReceiver
import de.emilyshub.todos.Due
import de.emilyshub.todos.TodoDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

/**
 * To-dos remind as routines do (SPEC.md §3, §4, §5), with the app closed: at their time with "Erledigt · Später", and
 * bound to places there, or on arrival, also without a time. One left open waits until it is done; it is never missed.
 */
@RunWith(AndroidJUnit4::class)
class TodoRemindersTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val connectivity = shadowOf(app.getSystemService(ConnectivityManager::class.java))
    private val today = LocalDate.parse("2026-10-23")

    @Before
    fun allowExactAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `a to-do with a time pops up at it with done and later, which only the app can receive`() {
        app.todo("Arzt anrufen", due = Due.At(today, LocalTime.of(9, 0)))
        assertEquals(berlin("2026-10-23T09:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))

        goOffAt("2026-10-23T09:00")

        val reminder = reminders().single()
        assertEquals("Arzt anrufen", reminder.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Fällig: heute, 09:00", reminder.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(listOf("Erledigt", "Später"), reminder.actions.map { it.title.toString() })
        reminder.actions.forEach { assertSentOnlyTo(ReminderReceiver::class.java.name, it.actionIntent) }
        assertTrue(reminder.actions.all { it.isAuthenticationRequired })
        assertEquals("Erinnerung fällig", reminder.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertSentOnlyTo(MainActivity::class.java.name, reminder.contentIntent)
        assertEquals("tapped, it stays up until done", 0, reminder.flags and Notification.FLAG_AUTO_CANCEL)
    }

    @Test
    fun `done in the reminder ticks the to-do off into the archive and takes the reminder away`() {
        app.todo("Arzt anrufen", due = Due.At(today, LocalTime.of(9, 0)))
        goOffAt("2026-10-23T09:00")

        tap("Erledigt")

        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(listOf("Arzt anrufen"), runBlocking { app.todos.archived().first().map { it.title } })
    }

    @Test
    fun `before 19 00 later offers 30 min, tonight and tomorrow morning, and tonight comes back at 20 00`() {
        val call = app.todo("Arzt anrufen", due = Due.At(today, LocalTime.of(9, 0)))
        goOffAt("2026-10-23T09:00")

        tap("Später")
        val choice = reminders().single()
        assertEquals("Wann erneut erinnern?", choice.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(listOf("30 Min.", "Heute Abend", "Morgen früh"), choice.actions.map { it.title.toString() })
        tap("Heute Abend")
        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(berlin("2026-10-23T20:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))

        goOffAt("2026-10-23T20:00")
        assertEquals(listOf("Arzt anrufen"), titles())
        assertEquals("only the reminder moved", Due.At(today, LocalTime.of(9, 0)), runBlocking { app.todos.get(call) }?.due)
    }

    @Test
    fun `from 19 00 on later offers 1 h instead of tonight, and the reminder comes back after the time chosen`() {
        app.todo("Arzt anrufen", due = Due.At(today, LocalTime.of(23, 0)))
        goOffAt("2026-10-23T23:00")

        tap("Später")
        assertEquals(listOf("30 Min.", "1 Std.", "Morgen früh"), reminders().single().actions.map { it.title.toString() })
        tap("1 Std.")
        assertEquals(emptyList<Notification>(), reminders())

        goOffAt("2026-10-24T00:00")
        assertEquals(listOf("Arzt anrufen"), titles())
        assertEquals("Fällig: gestern, 23:00", reminders().single().extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `tomorrow morning comes back at 08 00 the next day, and still does after a restart`() {
        app.todo("Arzt anrufen", due = Due.At(today, LocalTime.of(18, 0)))
        goOffAt("2026-10-23T18:00")

        tap("Später")
        tap("Morgen früh")
        assertEquals(emptyList<Notification>(), reminders())
        // The phone restarts overnight; the restart took the alarm with it.
        app.getSystemService(AlarmManager::class.java).cancel(checkNotNull(nextAlarm().operation))
        app.clock.now = berlin("2026-10-24T06:00")
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        idle()

        assertEquals(berlin("2026-10-24T08:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-10-24T08:00")
        assertEquals(listOf("Arzt anrufen"), titles())
    }

    @Test
    fun `tonight from a notification still up after 19 00 leaves the reminder as it is`() {
        app.todo("Arzt anrufen", due = Due.At(today, LocalTime.of(18, 30)))
        goOffAt("2026-10-23T18:30")
        tap("Später")

        app.clock.now = berlin("2026-10-23T19:05")
        tap("Heute Abend")

        assertEquals(listOf("Arzt anrufen"), titles())
        assertEquals(listOf("30 Min.", "1 Std.", "Morgen früh"), reminders().single().actions.map { it.title.toString() })
    }

    @Test
    fun `a to-do left open is never missed, its reminder stays up until it is done`() {
        app.todo("Arzt anrufen", due = Due.At(today, LocalTime.of(9, 0)))
        goOffAt("2026-10-23T09:00")

        app.clock.now = berlin("2026-10-25T09:00")
        runBlocking { app.reminders.update() }

        assertEquals(listOf("Arzt anrufen"), titles())
        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarmManager.scheduledAlarms)
    }

    @Test
    fun `allowed, the lock screen shows the to-do's title and when it is due, but never more of it, nor actions`() {
        app.todo(
            "Arzt anrufen", due = Due.At(today, LocalTime.of(9, 0)), nameOnLockScreen = true,
            description = "Wegen der Überweisung", checklist = listOf("Karte bereitlegen"),
        )

        goOffAt("2026-10-23T09:00")

        val reminder = reminders().single()
        assertEquals("Arzt anrufen", reminder.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Fällig: heute, 09:00", reminder.publicVersion.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(reminder.publicVersion.actions.isNullOrEmpty())
        assertTrue(reminder.actions.all { it.isAuthenticationRequired })

        tap("Später")
        val choice = reminders().single()
        assertEquals("Arzt anrufen", choice.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(choice.publicVersion.actions.isNullOrEmpty())
        assertTrue(choice.actions.all { it.isAuthenticationRequired })
    }

    @Test
    fun `allowed for a to-do bound to a place without a time, the lock screen shows its title but not the place`() {
        val home = app.place("Zuhause", "FritzBox")
        app.todo("Post", places = setOf(home), nameOnLockScreen = true)

        app.clock.now = berlin("2026-10-23T18:00")
        app.wifi.ssid = "FritzBox"
        arrive()

        val reminder = reminders().single()
        assertEquals("Post", reminder.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(null, reminder.publicVersion.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test
    fun `bound to a place, it pops up at its time there, and elsewhere on arriving there`() {
        val home = app.place("Zuhause", "FritzBox")
        app.todo("Pakete", due = Due.At(today, LocalTime.of(9, 0)), places = setOf(home))
        app.todo("Blumen", due = Due.At(today, LocalTime.of(9, 0)), places = setOf(home))
        app.wifi.ssid = "Firma"
        goOffAt("2026-10-23T09:00")
        assertEquals(emptyList<String>(), titles())

        app.clock.now = berlin("2026-10-25T18:00")
        app.wifi.ssid = "FritzBox"
        arrive()

        assertEquals(listOf("Blumen", "Pakete"), titles().sortedBy { it })
    }

    @Test
    fun `bound to a place without a time, it pops up on arriving there, as Android wakes the app on joining a WLAN`() {
        val home = app.place("Zuhause", "FritzBox")
        val post = app.todo("Post", places = setOf(home))
        assertEquals(1, connectivity.networkCallbackPendingIntents.size)

        app.clock.now = berlin("2026-10-23T18:00")
        app.wifi.ssid = "FritzBox"
        arrive()

        assertEquals("Ort: Zuhause", reminders().single().extras.getString(Notification.EXTRA_TEXT))
        assertEquals("waits no more", 0, connectivity.networkCallbackPendingIntents.size)
        runBlocking { app.todos.markDone(post) }
        assertEquals(emptyList<Notification>(), reminders())
    }

    @Test
    fun `a reminder follows a new title of its to-do, and goes away when the to-do is deleted or due later`() {
        val call = app.todo("Arzt", due = Due.At(today, LocalTime.of(9, 0)))
        val bins = app.todo("Müll", due = Due.At(today, LocalTime.of(9, 0)))
        goOffAt("2026-10-23T09:00")

        runBlocking {
            app.todos.update(call, TodoDraft("Arzt anrufen", due = Due.At(today, LocalTime.of(9, 0))))
            app.todos.update(bins, TodoDraft("Müll", due = Due.At(today, LocalTime.of(18, 0))))
        }
        assertEquals(listOf("Arzt anrufen"), titles())

        runBlocking { app.todos.delete(call) }
        assertEquals(emptyList<String>(), titles())
    }

    @Test
    fun `the receiver does nothing for a to-do's action without a valid to-do or with a time later does not offer`() =
        runBlocking {
            val id = app.todo("Arzt", due = Due.At(today, LocalTime.of(9, 0)))
            goOffAt("2026-10-23T09:00")

            listOf(0L, -1L, id + 1).forEach { other -> app.sendBroadcast(receiverIntent(ReminderReceiver.ACTION_DONE, other)) }
            app.sendBroadcast(receiverIntent(ReminderReceiver.ACTION_PUT_OFF, id).putExtra(ReminderReceiver.EXTRA_PUT_OFF, "5"))
            app.sendBroadcast(receiverIntent(ReminderReceiver.ACTION_PUT_OFF, id).putExtra(ReminderReceiver.EXTRA_PUT_OFF, "never"))
            idle()

            assertEquals(listOf("Arzt"), titles())
            assertEquals(emptyList<String>(), app.todos.archived().first().map { it.title })
            assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarmManager.scheduledAlarms)
        }

    private fun receiverIntent(action: String, todoId: Long) =
        Intent(app, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_TODO, todoId)

    /** The alarm for the next reminder, not the one for looking around. */
    private fun nextAlarm() = alarmManager.alarmsFor(ReminderReceiver.ACTION_REMIND).single()

    /** Moves the clock to [localDateTime] in Berlin and lets the alarm set for the next reminder go off. */
    private fun goOffAt(localDateTime: String) {
        val alarm = nextAlarm()
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(alarm)
        idle()
    }

    /** Joins a WLAN: Android sends what the app asked it to, as the app may be closed, and the alarm set then goes off. */
    private fun arrive() {
        connectivity.networkCallbackPendingIntents.single().send()
        idle()
        alarmManager.fireAlarm(nextAlarm())
        idle()
    }

    /** Taps [action] on the reminder on the screen. */
    private fun tap(action: String) {
        reminders().single().actions.single { it.title == action }.actionIntent.send()
        idle()
    }

    private fun reminders(): List<Notification> = notificationManager.activeNotifications.map { it.notification }

    private fun titles() = reminders().map { it.extras.getString(Notification.EXTRA_TITLE) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun assertSentOnlyTo(className: String, pendingIntent: PendingIntent) {
        val shadow = shadowOf(pendingIntent)
        assertTrue("immutable", shadow.isImmutable)
        assertEquals(className, shadow.savedIntent.component?.className)
    }

    private fun berlin(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Europe/Berlin")).toInstant()
}
