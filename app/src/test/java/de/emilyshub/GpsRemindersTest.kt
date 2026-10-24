package de.emilyshub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.places.Area
import de.emilyshub.places.Coordinates
import de.emilyshub.places.Position
import de.emilyshub.reminders.SystemAreaWatch
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
 * lies within one's area, wait elsewhere, and pop up on entering it while their time window is open. Android watches
 * the areas only while something waits for them, to save the battery.
 */
@RunWith(AndroidJUnit4::class)
class GpsRemindersTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before
    fun aRoutineBoundToTheGym() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val gym = app.place("Gym", area = GYM)
        app.routine("Training", "18:00", window = TimeWindow(hours = 2), places = setOf(gym))
    }

    @Test
    fun `within the gym's area at its time, it pops up at once, and no area is watched`() {
        app.location.position = Position(GYM.center, accuracy = 15.0)

        goOffAt("2026-10-23T18:00")

        assertEquals(listOf("Training"), reminders())
        assertEquals(emptySet<Area>(), app.areaWatch.watched)
    }

    @Test
    fun `elsewhere at its time, it waits while the gym's area is watched, and pops up on entering it`() {
        app.location.position = ELSEWHERE
        goOffAt("2026-10-23T18:00")
        assertEquals(emptyList<String>(), reminders())
        assertEquals(setOf(GYM), app.areaWatch.watched)

        app.clock.now = berlin("2026-10-23T18:40")
        app.location.position = Position(GYM.center, accuracy = 15.0)
        enterOrLeaveAnArea()

        assertEquals(listOf("Training"), reminders())
        assertEquals(emptySet<Area>(), app.areaWatch.watched)
    }

    @Test
    fun `told of the area while not within it, as on leaving it again, it waits on`() {
        app.location.position = ELSEWHERE
        goOffAt("2026-10-23T18:00")

        app.clock.now = berlin("2026-10-23T18:40")
        app.location.position = Position(Coordinates(52.5225, 13.404954), accuracy = 15.0)
        enterOrLeaveAnArea()

        assertEquals(emptyList<String>(), reminders())
        assertEquals(setOf(GYM), app.areaWatch.watched)
    }

    @Test
    fun `with no position to be had at its time, it pops up rather than being held back`() {
        app.location.position = null

        goOffAt("2026-10-23T18:00")

        assertEquals(listOf("Training"), reminders())
    }

    @Test
    fun `once its time window has ended, it is missed and the area no longer watched`() {
        app.location.position = ELSEWHERE
        goOffAt("2026-10-23T18:00")

        goOffAt("2026-10-23T20:01")

        assertEquals(emptyList<String>(), reminders())
        assertEquals(emptySet<Area>(), app.areaWatch.watched)
        val statuses = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }
        assertEquals(listOf(OccurrenceStatus.Missed), statuses)
    }

    @Test
    fun `waiting for a place known only by its WLAN, no area is watched`() {
        val home = app.place("Zuhause", "FritzBox")
        app.routine("Tabletten", "08:00", places = setOf(home))
        app.wifi.ssid = null

        goOffAt("2026-10-23T08:00")

        assertEquals(emptyList<String>(), reminders())
        assertEquals(emptySet<Area>(), app.areaWatch.watched)
    }

    /** Android tells the app it entered or left a watched area, as the app may be closed; the alarm set then goes off. */
    private fun enterOrLeaveAnArea() {
        SystemAreaWatch.operation(app).send()
        idle()
        alarmManager.peekNextScheduledAlarm()?.let { alarmManager.fireAlarm(it) }
        idle()
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
        val GYM = Area(latitude = 52.520008, longitude = 13.404954, radius = 150)

        /** Some 9 km north of it. */
        val ELSEWHERE = Position(Coordinates(52.6, 13.4), accuracy = 20.0)
    }
}
