package de.emilyshub

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.AlarmActivity
import de.emilyshub.routines.OccurrenceStatus
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
            listOf("Erledigt", "Später", "Überspringen").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
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

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant()

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
