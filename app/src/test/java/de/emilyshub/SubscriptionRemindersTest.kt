package de.emilyshub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Saving
import de.emilyshub.ledger.NoticeUnit
import de.emilyshub.ledger.SubscriptionDraft
import de.emilyshub.ledger.SubscriptionEditing
import de.emilyshub.reminders.ReminderReceiver
import de.emilyshub.reminders.RescheduleReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Abos remind to cancel them (#80), with the app closed: at 21:00 on the first day of their lead and again on their last
 * day, as a plain notification with "Gekündigt · Läuft weiter", nothing but "Ein Abo ist zu kündigen" on the lock
 * screen; made up for after the phone was off, while the last day is not over; and following every change.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionRemindersTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notificationManager = app.getSystemService(NotificationManager::class.java)

    @Before
    fun allowExactAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `at nine in the evening of the first day of its lead it notifies with its name, its last day and its two buttons`() {
        // The test app's clock: Friday, 23 October 2026, 07:00. 14 days of lead: from today on.
        val streaming = abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-06")))
        assertEquals(berlin("2026-10-23T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))

        goOffAt("2026-10-23T21:00")

        val reminder = reminders().single()
        assertEquals("Streaming", reminder.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Kündigen bis 06.11.2026", reminder.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(listOf("Gekündigt", "Läuft weiter"), reminder.actions.map { it.title.toString() })
        assertEquals(Notification.CATEGORY_REMINDER, reminder.category)
        assertEquals("never on the full screen", null, reminder.fullScreenIntent)
        assertEquals("Ein Abo ist zu kündigen", reminder.publicVersion.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(null, reminder.publicVersion.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(reminder.publicVersion.actions.isNullOrEmpty())
        assertEquals("tapped, it stays up until answered", 0, reminder.flags and Notification.FLAG_AUTO_CANCEL)
        // Tapped, it opens the Abo; "Gekündigt" opens it asking until when; "Läuft weiter" goes to the app only.
        assertOpens(reminder.contentIntent, streaming, cancel = false)
        assertOpens(reminder.actions[0].actionIntent, streaming, cancel = true)
        assertSentOnlyTo(ReminderReceiver::class.java.name, reminder.actions[1].actionIntent)
        assertTrue(reminder.actions.all { it.isAuthenticationRequired })
    }

    @Test
    fun `it comes again on its last day, and goes once that is over`() {
        abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-06")))
        goOffAt("2026-10-23T21:00")
        notificationManager.cancelAll()

        assertEquals(berlin("2026-11-06T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-11-06T21:00")
        assertEquals(listOf("Streaming"), titles())

        assertEquals(berlin("2026-11-07T00:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-11-07T00:00")
        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarmManager.alarmsFor(ReminderReceiver.ACTION_REMIND))
    }

    @Test
    fun `missed while the phone was off, it comes on the next start, but not once its last day is over`() {
        abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-06")))
        abo(SubscriptionDraft("Zeitung", cancelBy = date("2026-10-23"), lead = "0"))

        // Off from the afternoon to the next morning: both moments went by, but only one Abo still has time.
        startAt("2026-10-24T08:00")

        assertEquals(listOf("Streaming"), titles())
    }

    @Test
    fun `made within its lead, it does not notify for the time gone by, however long ago the last reminder came`() {
        // Nothing reminded for days: the app last reminded on the 23rd.
        runBlocking { app.reminders.update() }
        app.clock.now = berlin("2026-10-28T10:00")

        // Its lead began on the 26th.
        abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-09")))

        assertEquals(berlin("2026-11-09T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-10-28T10:01")
        assertEquals(emptyList<Notification>(), reminders())
    }

    @Test
    fun `a lead made longer reaches back only from the change on`() {
        val streaming = abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-20")))
        assertEquals(berlin("2026-11-06T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))

        // On the 25th, a lead of 30 days: it would have begun on the 21st.
        app.clock.now = berlin("2026-10-25T09:00")
        save(streaming, SubscriptionDraft("Streaming", cancelBy = date("2026-11-20"), lead = "30"))

        assertEquals(berlin("2026-11-20T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `after a restart of the phone its reminder is set again`() {
        abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-06")))
        // As a restart takes the alarms.
        val alarms = app.getSystemService(AlarmManager::class.java)
        alarmManager.alarmsFor(ReminderReceiver.ACTION_REMIND).forEach { alarms.cancel(checkNotNull(it.operation)) }

        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, RescheduleReceiver::class.java))
        idle()

        assertEquals(berlin("2026-10-23T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `let run on from the notification, it goes, and one by a single date reminds no more`() {
        val streaming = abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-06")))
        goOffAt("2026-10-23T21:00")

        tap("Läuft weiter")

        assertEquals(emptyList<Notification>(), reminders())
        assertTrue(runBlocking { app.subscriptions.overview().first() }.running.single().subscription.runsOn)
        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarmManager.alarmsFor(ReminderReceiver.ACTION_REMIND))
        assertEquals(streaming, runBlocking { app.subscriptions.overview().first() }.running.single().subscription.id)
    }

    @Test
    fun `let run on and reckoned, it reminds of the next term in its own time`() {
        // Term to the end of November, a month of notice: last day the end of October, lead from the 17th.
        abo(
            SubscriptionDraft(
                "Fitness", reckoned = true, termEnd = date("2026-11-30"), notice = "1", noticeUnit = NoticeUnit.Months, renewal = "12",
            ),
        )
        // Made within its lead, it notifies first on its last day.
        assertEquals(berlin("2026-10-31T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
        goOffAt("2026-10-31T21:00")
        assertEquals(listOf("Fitness"), titles())

        tap("Läuft weiter")

        assertEquals(emptyList<Notification>(), reminders())
        // The next last day: 31 October 2027, lead from the 17th.
        assertEquals(berlin("2027-10-17T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))
    }

    @Test
    fun `cancelled, its reminder goes and no more comes`() {
        val streaming = abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-06")))
        goOffAt("2026-10-23T21:00")

        runBlocking { app.subscriptions.cancel(streaming, date("2026-11-30")) }
        idle()

        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarmManager.alarmsFor(ReminderReceiver.ACTION_REMIND))
    }

    @Test
    fun `a new deadline or lead moves the reminder, and one deleted takes it away`() {
        val streaming = abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-06")))
        goOffAt("2026-10-23T21:00")

        // Seven days of lead now: it is not due before the 30th any more.
        save(streaming, SubscriptionDraft("Streaming", cancelBy = date("2026-11-06"), lead = "7"))
        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(berlin("2026-10-30T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))

        save(streaming, SubscriptionDraft("Streaming", cancelBy = date("2026-11-20"), lead = "7"))
        assertEquals(berlin("2026-11-13T21:00"), Instant.ofEpochMilli(nextAlarm().triggerAtMs))

        runBlocking { app.subscriptions.delete(streaming) }
        idle()
        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarmManager.alarmsFor(ReminderReceiver.ACTION_REMIND))
    }

    @Test
    fun `an Abo with a last day to come counts as reminding, one cancelled or let run on not`() {
        assertFalse(anyReminds())
        val streaming = abo(SubscriptionDraft("Streaming", cancelBy = date("2026-11-06")))
        assertTrue(anyReminds())

        runBlocking { app.subscriptions.runOn(streaming) }
        assertFalse(anyReminds())

        val gym = abo(SubscriptionDraft("Fitness", cancelBy = date("2027-01-31")))
        runBlocking { app.subscriptions.cancel(gym, date("2027-02-28")) }
        assertFalse(anyReminds())
    }

    private fun anyReminds() = runBlocking { app.subscriptions.anyReminds().first() }

    private fun abo(draft: SubscriptionDraft): Long = save(null, draft)

    private fun save(id: Long?, draft: SubscriptionDraft): Long {
        val editing = id?.let { runBlocking { app.subscriptions.editing(it) } } ?: SubscriptionEditing.New
        val saved = runBlocking { app.subscriptions.save(editing, draft) }
        idle()
        return (saved as Saving.Saved).id
    }

    private fun date(text: String) = LocalDate.parse(text)

    /** The alarm for the next reminder, not the one for looking around. */
    private fun nextAlarm() = alarmManager.alarmsFor(ReminderReceiver.ACTION_REMIND).single()

    /** Moves the clock to [localDateTime] in Berlin and lets the alarm set for the next reminder go off. */
    private fun goOffAt(localDateTime: String) {
        val alarm = nextAlarm()
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(alarm)
        idle()
    }

    /** The phone was off until [localDateTime]: the app starts, sets its alarm anew, which went by meanwhile, and so goes off. */
    private fun startAt(localDateTime: String) {
        app.clock.now = berlin(localDateTime)
        runBlocking { app.reminders.update() }
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

    /** [pendingIntent] opens the app at the Abo [id], asking until when it was cancelled if [cancel]. */
    private fun assertOpens(pendingIntent: PendingIntent, id: Long, cancel: Boolean) {
        assertSentOnlyTo(MainActivity::class.java.name, pendingIntent)
        val intent = shadowOf(pendingIntent).savedIntent
        assertEquals(MainActivity.openSubscription(app, id, cancel).extras?.keySet(), intent.extras?.keySet())
        assertEquals(id, intent.getLongExtra("de.emilyshub.extra.OPEN_SUBSCRIPTION", 0))
        assertEquals(cancel, intent.getBooleanExtra("de.emilyshub.extra.CANCEL", !cancel))
    }

    private fun assertSentOnlyTo(className: String, pendingIntent: PendingIntent) {
        val shadow = shadowOf(pendingIntent)
        assertTrue("immutable", shadow.isImmutable)
        assertEquals(className, shadow.savedIntent.component?.className)
    }

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Europe/Berlin")).toInstant()
}
