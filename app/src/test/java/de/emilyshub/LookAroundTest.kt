package de.emilyshub

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.places.Area
import de.emilyshub.places.Coordinates
import de.emilyshub.places.Position
import de.emilyshub.reminders.ReminderReceiver
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
 * Looking around every 30 minutes (SPEC.md §4), also with the app closed: while a to-do waits for a place, the app looks
 * where the device is, so that one waiting for a place without a WLAN still pops up on arriving there. It can be turned
 * off in the settings.
 */
@RunWith(AndroidJUnit4::class)
class LookAroundTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private var bakery = 0L

    @Before
    fun aBakeryWithoutWlan() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        bakery = app.place("Bäckerei", area = BAKERY)
    }

    @Test
    fun `while a to-do waits for a place, the app looks around every 30 minutes, and stops once none waits`() {
        app.location.position = ELSEWHERE
        val bread = app.todo("Brot", places = setOf(bakery))
        assertEquals(listOf(berlin("2026-10-23T07:30")), looks())

        lookAt("2026-10-23T07:30")
        assertEquals(listOf(berlin("2026-10-23T08:00")), looks())

        runBlocking { app.todos.markDone(bread) }
        assertEquals(emptyList<Instant>(), looks())
    }

    @Test
    fun `a to-do pops up on arriving at a place without a WLAN, once the app has seen the device away from it`() {
        app.location.position = ELSEWHERE
        app.todo("Brot", places = setOf(bakery))
        lookAt("2026-10-23T07:30")
        assertEquals(emptyList<String>(), reminders())

        app.location.position = AT_THE_BAKERY
        lookAt("2026-10-23T08:00")

        assertEquals(listOf("Brot"), reminders())
    }

    @Test
    fun `after a restart of the phone, the look around comes as it was to`() {
        app.location.position = ELSEWHERE
        app.todo("Brot", places = setOf(bakery))
        app.clock.now = berlin("2026-10-23T07:10")
        val android = app.getSystemService(AlarmManager::class.java)
        alarmManager.scheduledAlarms.toList().forEach { android.cancel(checkNotNull(it.operation)) }

        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        idle()

        assertEquals(listOf(berlin("2026-10-23T07:30")), looks())
    }

    @Test
    fun `turned off in the settings, the app does not look around`() {
        runBlocking {
            app.settings.load()
            app.settings.setLookAround(false)
        }

        app.todo("Brot", places = setOf(bakery))

        assertEquals(emptyList<Instant>(), looks())
    }

    /** When the app is to look around next. */
    private fun looks() = alarms(ReminderReceiver.ACTION_LOOK_AROUND).map { Instant.ofEpochMilli(it.triggerAtMs) }

    /** Moves the clock to [localDateTime] in Berlin, lets the app look around, and then any reminder that brings pop up. */
    private fun lookAt(localDateTime: String) {
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(alarms(ReminderReceiver.ACTION_LOOK_AROUND).single())
        idle()
        alarms(ReminderReceiver.ACTION_REMIND).forEach(alarmManager::fireAlarm)
        idle()
    }

    private fun alarms(action: String) = alarmManager.scheduledAlarms.filter { shadowOf(it.operation).savedIntent.action == action }

    private fun reminders() = app.getSystemService(NotificationManager::class.java).activeNotifications
        .map { it.notification }
        .filter { it.channelId == "reminders" }
        .map { it.extras.getString(Notification.EXTRA_TITLE) }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun berlin(localDateTime: String): Instant =
        LocalDateTime.parse(localDateTime).atZone(ZoneId.of("Europe/Berlin")).toInstant()

    private companion object {
        /** A bakery in Berlin Mitte, 150 m around, without a WLAN. */
        val BAKERY = Area(Coordinates(52.520008, 13.404954), radius = 150)

        val AT_THE_BAKERY = Position(BAKERY.center, accuracy = 15.0)

        /** Some 9 km north of it. */
        val ELSEWHERE = Position(Coordinates(52.6, 13.4), accuracy = 20.0)
    }
}
