package de.emilyshub

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.AlarmActivity
import de.emilyshub.routines.OccurrenceStatus
import de.emilyshub.routines.TimeWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class AlarmTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))

    @Before
    fun aFullScreenRoutineGoesOff() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.fullScreenAlarmsAllowed = true
        app.routine("Arzttermin", "07:30", fullScreen = true)
        goOffAt("2026-10-23T07:30")
    }

    @Test
    fun `the alarm shows over the lock screen and turns the screen on`() {
        openAlarm().use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(shadowOf(activity).showWhenLocked)
                assertTrue(shadowOf(activity).turnScreenOn)
            }
        }
    }

    @Test
    fun `on the lock screen the alarm only says that a reminder is due`() {
        keyguard.setKeyguardLocked(true)

        openAlarm().use {
            compose.onNodeWithText("Erinnerung fällig").assertIsDisplayed()
            compose.onNodeWithText("Arzttermin").assertDoesNotExist()
            compose.onNodeWithText("Erledigt").assertDoesNotExist()
            compose.onNodeWithText("Überspringen").assertDoesNotExist()
        }
    }

    @Test
    fun `once the phone is unlocked from the alarm, it shows the routine and its actions`() {
        keyguard.setKeyguardLocked(true)

        openAlarm().use {
            compose.onNodeWithText("Entsperren").performClick()
            keyguard.setKeyguardLocked(false) // the PIN was entered

            compose.onNodeWithText("Arzttermin").assertIsDisplayed()
            compose.onNodeWithText("Fällig um 07:30").assertIsDisplayed()
            listOf("Erledigt", "Später", "Überspringen", "Heute nicht").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        }
    }

    @Test
    fun `unlocked another way, say with a fingerprint, the alarm shows the routine too`() {
        keyguard.setKeyguardLocked(true)

        openAlarm().use {
            keyguard.setKeyguardLocked(false)
            app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
            idle()

            compose.onNodeWithText("Arzttermin").assertIsDisplayed()
        }
    }

    @Test
    fun `done in the alarm ticks the routine off and takes the alarm and the reminder away`() {
        openAlarm().use { scenario ->
            compose.onNodeWithText("Erledigt").performClick()
            idle()

            assertClosed(scenario)
        }
        assertEquals(listOf(OccurrenceStatus.Done), statuses())
        assertEquals(emptyList<Notification>(), reminders())
    }

    @Test
    fun `skipping in the alarm skips the routine`() {
        openAlarm().use { scenario ->
            compose.onNodeWithText("Überspringen").performClick()
            idle()

            assertClosed(scenario)
        }
        assertEquals(listOf(OccurrenceStatus.Skipped), statuses())
    }

    @Test
    fun `Heute nicht in the alarm drops the routine for the day`() {
        openAlarm().use { scenario ->
            compose.onNodeWithText("Heute nicht").performClick()
            idle()

            assertClosed(scenario)
        }
        assertEquals(listOf(OccurrenceStatus.Dropped), statuses())
    }

    @Test
    fun `later in the alarm offers 10 min, 30 min and 1 h, and puts the reminder off by the time chosen`() {
        openAlarm().use { scenario ->
            compose.onNodeWithText("Später").performClick()
            compose.onNodeWithText("Erneut erinnern in …").assertIsDisplayed()
            listOf("10 Min.", "1 Std.").forEach { compose.onNodeWithText(it).assertIsDisplayed() }

            compose.onNodeWithText("30 Min.").performClick()
            idle()

            assertClosed(scenario)
        }
        val nextAlarm = checkNotNull(shadowOf(app.getSystemService(AlarmManager::class.java)).peekNextScheduledAlarm())
        assertEquals(berlin("2026-10-23T08:00"), Instant.ofEpochMilli(nextAlarm.triggerAtMs))
        assertEquals(listOf(OccurrenceStatus.Open), statuses())
    }

    @Test
    fun `with too little of the time window left, the alarm offers no later`() {
        app.routine("Lesen", "23:55", fullScreen = true, window = TimeWindow(untilMidnight = true))
        goOffAt("2026-10-23T23:55")

        openAlarm(of = "Lesen").use {
            compose.onNodeWithText("Erledigt").assertIsDisplayed()
            compose.onNodeWithText("Später").assertDoesNotExist()
        }
    }

    @Test
    fun `the alarm goes away once its reminder is answered somewhere else`() = runBlocking {
        openAlarm().use { scenario ->
            compose.waitForIdle()

            app.routines.markDone(app.routines.dueOn(app.routines.today()).first().single().occurrenceId)
            idle()

            assertClosed(scenario)
        }
    }

    @Test
    fun `closing the alarm leaves the reminder, also on the lock screen`() {
        keyguard.setKeyguardLocked(true)

        openAlarm().use { scenario ->
            compose.onNodeWithText("Schließen").performClick()
            idle()

            assertClosed(scenario)
        }
        assertEquals(listOf("Arzttermin"), reminders().map { it.extras.getString(Notification.EXTRA_TITLE) })
        assertEquals(listOf(OccurrenceStatus.Open), statuses())
    }

    @Test
    fun `with two full-screen alarms at once, the alarm shows the newer one and then the other`() {
        openAlarm(of = "Arzttermin").use { scenario ->
            compose.onNodeWithText("Später").performClick() // about to put off Arzttermin
            app.routine("Tabletten", "07:45", fullScreen = true)
            goOffAt("2026-10-23T07:45")
            scenario.onActivity { getInstrumentation().callActivityOnNewIntent(it, alarmIntent(of = "Tabletten")) }

            compose.onNodeWithText("Tabletten").assertIsDisplayed()
            compose.onNodeWithText("Erledigt").performClick()
            idle()

            compose.onNodeWithText("Arzttermin").assertIsDisplayed()
            scenario.onActivity { assertFalse("still open", it.isFinishing) }
        }
        assertEquals(listOf("07:30" to OccurrenceStatus.Open, "07:45" to OccurrenceStatus.Done), timesAndStatuses())
    }

    @Test
    fun `the alarm rings like an alarm clock, and keeps the screen on, while it is up`() {
        openAlarm().use { scenario ->
            assertTrue("ringing", app.ringer.ringing)
            scenario.onActivity { assertTrue("screen kept on", it.keepsScreenOn()) }
        }
    }

    @Test
    fun `answering the alarm silences it`() {
        openAlarm().use {
            compose.onNodeWithText("Erledigt").performClick()
            idle()

            assertFalse(app.ringer.ringing)
        }
    }

    @Test
    fun `closing the alarm on the lock screen silences it`() {
        keyguard.setKeyguardLocked(true)

        openAlarm().use {
            compose.onNodeWithText("Schließen").performClick()
            idle()

            assertFalse(app.ringer.ringing)
        }
    }

    @Test
    fun `going back from the alarm closes and silences it`() {
        val scenario = openAlarm()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        scenario.close()

        assertFalse(app.ringer.ringing)
    }

    @Test
    fun `answered somewhere else, the alarm goes quiet, also with another one still to show`() = runBlocking {
        openAlarm(of = "Arzttermin").use { scenario ->
            app.routine("Tabletten", "07:45", fullScreen = true)
            goOffAt("2026-10-23T07:45")
            scenario.onActivity { getInstrumentation().callActivityOnNewIntent(it, alarmIntent(of = "Tabletten")) }
            idle()

            app.routines.markDone(app.routines.dueOn(app.routines.today()).first().single { it.name == "Tabletten" }.occurrenceId)
            idle()

            compose.onNodeWithText("Arzttermin").assertIsDisplayed()
            assertFalse(app.ringer.ringing)
        }
    }

    @Test
    fun `leaving the alarm, say with the power button, silences it after a moment, until the next alarm`() {
        openAlarm().use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            wait(Duration.ofSeconds(2))
            assertTrue("still ringing for a moment", app.ringer.ringing)

            wait(Duration.ofSeconds(1))
            assertFalse("silenced", app.ringer.ringing)

            scenario.moveToState(Lifecycle.State.RESUMED)
            assertFalse("still silent", app.ringer.ringing)
            scenario.onActivity { assertFalse("screen may turn off", it.keepsScreenOn()) }
        }
    }

    @Test
    fun `off the screen for only a moment, as on some phones while the screen turns on, the alarm rings on`() {
        openAlarm().use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            wait(Duration.ofSeconds(5))

            assertTrue(app.ringer.ringing)
        }
    }

    @Test
    fun `after ten minutes the alarm stops ringing and lets the screen turn off, but stays up`() {
        openAlarm().use { scenario ->
            wait(Duration.ofMinutes(10).minusSeconds(1))
            assertTrue("still ringing", app.ringer.ringing)

            wait(Duration.ofSeconds(1))

            assertFalse("silenced", app.ringer.ringing)
            scenario.onActivity {
                assertFalse("screen may turn off", it.keepsScreenOn())
                assertFalse("still up", it.isFinishing)
            }
        }
    }

    @Test
    fun `a newer alarm rings again, and turning the phone keeps it ringing`() {
        openAlarm(of = "Arzttermin").use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED)
            wait(Duration.ofSeconds(3))
            assertFalse("silenced", app.ringer.ringing)
            app.routine("Tabletten", "07:45", fullScreen = true)
            goOffAt("2026-10-23T07:45")

            scenario.onActivity { getInstrumentation().callActivityOnNewIntent(it, alarmIntent(of = "Tabletten")) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertTrue("ringing again", app.ringer.ringing)

            scenario.recreate()
            assertTrue("ringing after turning", app.ringer.ringing)
        }
    }

    private fun AlarmActivity.keepsScreenOn() = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    /** Robolectric leaves a finished activity as it is, so it only shows that it is finishing. */
    private fun assertClosed(scenario: ActivityScenario<AlarmActivity>) {
        scenario.onActivity { assertTrue("closed", it.isFinishing) }
    }

    /** Opens the alarm the way Android does, with the reminder's own full-screen intent. */
    private fun openAlarm(of: String = "Arzttermin"): ActivityScenario<AlarmActivity> = ActivityScenario.launch(alarmIntent(of))

    private fun alarmIntent(of: String): Intent {
        val reminder = reminders().single { it.extras.getString(Notification.EXTRA_TITLE) == of }
        return shadowOf(checkNotNull(reminder.fullScreenIntent) { "No full-screen alarm" }).savedIntent
    }

    /** Moves the clock to [localDateTime] in Berlin and lets the alarm set for the next reminder go off. */
    private fun goOffAt(localDateTime: String) {
        val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(checkNotNull(alarmManager.peekNextScheduledAlarm()))
        idle()
    }

    private fun reminders(): List<Notification> =
        app.getSystemService(NotificationManager::class.java).activeNotifications.map { it.notification }

    private fun statuses() = timesAndStatuses().map { (_, status) -> status }

    private fun timesAndStatuses() =
        runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.dueAt.toLocalTime().toString() to it.status } }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Lets [duration] go by on the main thread, where the alarm keeps its time. */
    private fun wait(duration: Duration) = shadowOf(Looper.getMainLooper()).idleFor(duration)

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant()

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
