package de.emilyshub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.net.ConnectivityManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.places.Area
import de.emilyshub.places.Coordinates
import de.emilyshub.places.Position
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.TimeWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Routines bound to GPS places (SPEC.md §4), with the app closed: they pop up at their time where the device's position
 * lies within one's area, and wait elsewhere. Without proximity alerts, which would need a PendingIntent Android may
 * change (SPEC.md §9), arriving is noticed at the follow-ups and on joining a WLAN, while the time window is open.
 */
@RunWith(AndroidJUnit4::class)
class GpsRemindersTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val connectivity = shadowOf(app.getSystemService(ConnectivityManager::class.java))

    @Before
    fun aRoutineBoundToTheGym() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val gym = app.place("Gym", area = GYM)
        app.routine("Training", "18:00", window = TimeWindow(hours = 2), places = setOf(gym))
    }

    @Test
    fun `within the gym's area at its time, it pops up at once`() {
        app.location.position = AT_THE_GYM

        goOffAt("2026-10-23T18:00")

        assertEquals(listOf("Training"), reminders())
    }

    @Test
    fun `elsewhere at its time, it waits, and pops up at a follow-up once within the area`() {
        app.location.position = ELSEWHERE
        goOffAt("2026-10-23T18:00")
        assertEquals(emptyList<String>(), reminders())

        app.location.position = AT_THE_GYM
        goOffAt("2026-10-23T18:30")

        assertEquals(listOf("Training"), reminders())
    }

    @Test
    fun `waiting, it pops up on joining a WLAN within the area, as the position is looked at then too`() {
        app.location.position = ELSEWHERE
        goOffAt("2026-10-23T18:00")

        app.clock.now = berlin("2026-10-23T18:10")
        app.location.position = AT_THE_GYM
        app.wifi.ssid = "FitX Gast"
        connectivity.networkCallbackPendingIntents.single().send()
        idle()
        goOffAt("2026-10-23T18:10")

        assertEquals(listOf("Training"), reminders())
    }

    @Test
    fun `with no position to be had at its time, it pops up rather than wait`() {
        app.location.position = null
        goOffAt("2026-10-23T18:00")
        assertEquals(listOf("Training"), reminders())
    }

    @Test
    fun `with a position less precise than the area is large, it pops up rather than wait`() {
        app.location.position = Position(Coordinates(52.53, 13.404954), accuracy = 2_000.0)

        goOffAt("2026-10-23T18:00")

        assertEquals(listOf("Training"), reminders())
    }

    @Test
    fun `still elsewhere once its time window has ended, it is missed`() {
        app.location.position = ELSEWHERE
        goOffAt("2026-10-23T18:00")

        goOffAt("2026-10-23T20:01")

        assertEquals(emptyList<String>(), reminders())
        val statuses = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }
        assertEquals(listOf(OccurrenceStatus.Missed), statuses)
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

    private companion object {
        /** A gym in Berlin Mitte, 150 m around. */
        val GYM = Area(Coordinates(52.520008, 13.404954), radius = 150)

        val AT_THE_GYM = Position(GYM.center, accuracy = 15.0)

        /** Some 9 km north of it. */
        val ELSEWHERE = Position(Coordinates(52.6, 13.4), accuracy = 20.0)
    }
}
