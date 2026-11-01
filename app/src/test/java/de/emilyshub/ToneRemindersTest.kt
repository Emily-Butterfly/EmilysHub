package de.emilyshub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.AlarmActivity
import de.emilyshub.reminders.AlarmRingService
import de.emilyshub.reminders.Sound
import de.emilyshub.reminders.Tone
import de.emilyshub.routines.FollowUps
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.todos.Due
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
 * Tones of their own (SPEC.md §3): a notification plays its tone once, on a channel of that tone, and a full-screen
 * alarm plays it over and over; the default in the settings stands in for none, and for one gone.
 */
@RunWith(AndroidJUnit4::class)
class ToneRemindersTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val today = LocalDate.parse("2026-10-23")
    private val morning = app.tones.add("content://media/internal/audio/media/12", "Morgentau")
    private val bell = app.tones.add("content://media/internal/audio/media/34", "Glocke")

    @Before
    fun allowExactAlarms() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        runBlocking { app.settings.load() }
    }

    @Test
    fun `without a tone of its own, a reminder comes on the usual channel, with the phone's notification tone`() {
        app.routine("Vitamine", "07:30")

        goOffAt("2026-10-23T07:30")

        assertEquals("reminders", reminder("Vitamine").channelId)
    }

    @Test
    fun `with a tone of its own, a reminder comes on a channel of that tone, which plays it once`() {
        app.routine("Vitamine", "07:30", tone = morning)
        app.todo("Arzt anrufen", Due.At(today, LocalTime.of(7, 30)), tone = morning)
        app.routine("Lüften", "07:30", tone = bell)

        goOffAt("2026-10-23T07:30")

        val vitamins = channelOf(reminder("Vitamine"))
        assertEquals(Uri.parse(morning.uri), vitamins.sound)
        assertEquals("Erinnerungen: Morgentau", vitamins.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, vitamins.importance)
        assertEquals("one channel per tone", vitamins.id, reminder("Arzt anrufen").channelId)
        assertEquals(Uri.parse(bell.uri), channelOf(reminder("Lüften")).sound)
        assertNotEquals(vitamins.id, reminder("Lüften").channelId)
    }

    @Test
    fun `silent, a reminder comes on a channel without a sound`() {
        app.routine("Vitamine", "07:30", tone = Tone.Silent)

        goOffAt("2026-10-23T07:30")

        assertNull(channelOf(reminder("Vitamine")).sound)
    }

    @Test
    fun `the default notification tone in the settings stands in for none of its own`() {
        runBlocking { app.settings.setNotificationTone(bell) }
        app.routine("Vitamine", "07:30")
        app.routine("Lüften", "07:30", tone = morning)

        goOffAt("2026-10-23T07:30")

        assertEquals(Uri.parse(bell.uri), channelOf(reminder("Vitamine")).sound)
        assertEquals(Uri.parse(morning.uri), channelOf(reminder("Lüften")).sound)
    }

    @Test
    fun `a tone gone from the phone falls back to the default`() {
        runBlocking { app.settings.setNotificationTone(bell) }
        app.routine("Vitamine", "07:30", tone = morning)
        app.tones.remove(morning.uri)

        goOffAt("2026-10-23T07:30")

        assertEquals(Uri.parse(bell.uri), channelOf(reminder("Vitamine")).sound)
    }

    @Test
    fun `a changed tone moves the reminder up to its channel, quietly, and the channel no one uses any more goes away`() {
        val vitamins = app.routine("Vitamine", "07:30", tone = morning)
        app.routine("Lüften", "07:30", tone = Tone.Silent)
        goOffAt("2026-10-23T07:30")
        val morningChannel = reminder("Vitamine").channelId
        val silentChannel = reminder("Lüften").channelId

        runBlocking { app.routines.update(vitamins, draft("Vitamine", "07:30", tone = bell)) }

        assertEquals(Uri.parse(bell.uri), channelOf(reminder("Vitamine")).sound)
        assertTrue("shown again without a sound", reminder("Vitamine").flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertFalse("gone", morningChannel in channelIds())
        assertTrue("still used by Lüften", silentChannel in channelIds())
    }

    @Test
    fun `a full-screen alarm plays its own tone, the default alarm tone without one, and only vibrates when silent`() {
        app.fullScreenAlarmsAllowed = true
        runBlocking { app.settings.setAlarmTone(bell) }
        // Without follow-ups, so that each alarm goes off for one only.
        app.routine("Arzttermin", "07:30", followUps = FollowUps(count = 0), fullScreen = true, tone = morning)
        app.routine("Zahnarzt", "08:10", followUps = FollowUps(count = 0), fullScreen = true)
        app.routine("Tabletten", "08:50", followUps = FollowUps(count = 0), fullScreen = true, tone = Tone.Silent)

        goOffAt("2026-10-23T07:30")
        openAlarm("Arzttermin").use { assertEquals(Sound.Of(morning.uri), app.ringer.sound) }

        goOffAt("2026-10-23T08:10")
        openAlarm("Zahnarzt").use { assertEquals(Sound.Of(bell.uri), app.ringer.sound) }

        goOffAt("2026-10-23T08:50")
        openAlarm("Tabletten").use { assertEquals(Sound.Silent, app.ringer.sound) }
    }

    @Test
    fun `ringing in the background, the alarm plays its tone too, and a newer one rings with its own`() {
        app.fullScreenAlarmsAllowed = true
        app.routine("Arzttermin", "07:30", followUps = FollowUps(count = 0), fullScreen = true, tone = morning)
        app.routine("Zahnarzt", "07:40", followUps = FollowUps(count = 0), fullScreen = true, tone = bell)
        goOffAt("2026-10-23T07:30")
        val service = Robolectric.buildService(AlarmRingService::class.java, startedService()).create().startCommand(0, 1)
        idle()
        assertEquals(Sound.Of(morning.uri), app.ringer.sound)

        goOffAt("2026-10-23T07:40")
        service.withIntent(startedService()).startCommand(0, 2)
        idle()

        assertTrue(app.ringer.ringing)
        assertEquals(Sound.Of(bell.uri), app.ringer.sound)
    }

    @Test
    fun `a full-screen alarm whose tone is gone plays the phone's own alarm tone, as the default is at first`() {
        app.fullScreenAlarmsAllowed = true
        app.todo("Arzt anrufen", Due.At(today, LocalTime.of(9, 0)), fullScreen = true, tone = morning)
        app.tones.remove(morning.uri)

        goOffAt("2026-10-23T09:00")

        openAlarm("Arzt anrufen").use { assertEquals(Sound.PhoneDefault, app.ringer.sound) }
    }

    @Test
    fun `a ringing full-screen alarm comes on the quiet channel, as it plays its tone itself`() {
        app.fullScreenAlarmsAllowed = true
        app.routine("Arzttermin", "07:30", fullScreen = true, tone = morning)

        goOffAt("2026-10-23T07:30")

        assertNull(channelOf(reminder("Arzttermin")).sound)
    }

    private fun draft(name: String, time: String, tone: Tone) =
        RoutineDraft(name, description = "", listOf(LocalTime.parse(time)), Repetition.Daily, tone = tone)

    /** Opens the alarm the way Android does, with the reminder's own full-screen intent. */
    private fun openAlarm(of: String): ActivityScenario<AlarmActivity> {
        val intent = shadowOf(checkNotNull(reminder(of).fullScreenIntent) { "No full-screen alarm" }).savedIntent
        return ActivityScenario.launch(intent)
    }

    /** What the reminders started last to ring in the background. */
    private fun startedService(): Intent = checkNotNull(shadowOf(app).nextStartedService) { "Not ringing" }

    private fun channelOf(reminder: Notification) = notificationManager.getNotificationChannel(reminder.channelId)

    private fun channelIds() = notificationManager.notificationChannels.map { it.id }

    private fun reminder(title: String) = reminders().single { it.extras.getString(Notification.EXTRA_TITLE) == title }

    private fun reminders(): List<Notification> =
        notificationManager.activeNotifications.map { it.notification }.filter { it.channelId != "alarm-ringing" }

    /** Moves the clock to [localDateTime] in Berlin and lets the alarm set for the next reminder go off. */
    private fun goOffAt(localDateTime: String) {
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(checkNotNull(alarmManager.peekNextScheduledAlarm()))
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Europe/Berlin")).toInstant()
}
