package de.emilyshub

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.reminders.AlarmActivity
import de.emilyshub.routines.FollowUps
import de.emilyshub.routines.OccurrenceStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** "Starten" on a routine's reminder and its full-screen alarm leads into its run (SPEC.md §2, §3; #103). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h915dp") // the Galaxy A52
class StartFromReminderTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val alarmManager = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val keyguard = shadowOf(app.getSystemService(KeyguardManager::class.java))

    @Before
    fun allowReminders() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.settingsDao.values["reminders-asked"] = "yes"
    }

    @Test
    fun `a routine with a checklist is reminded of with Starten, Erledigt and Spaeter, Starten opening the app once unlocked`() {
        app.routine("Morgens", "07:30", checklist = listOf("Wasser", "Zähne"))

        goOffAt("2026-10-23T07:30")

        val reminder = reminders().single()
        assertEquals(listOf("Starten", "Erledigt", "Später"), reminder.actions.map { it.title.toString() })
        assertTrue(reminder.actions.all { it.isAuthenticationRequired })
        val start = shadowOf(reminder.actions[0].actionIntent)
        assertTrue("immutable", start.isImmutable)
        assertTrue("opens an activity", start.isActivity)
        assertEquals(MainActivity::class.java.name, start.savedIntent.component?.className)
        assertTrue(reminder.publicVersion.actions.isNullOrEmpty())

        tap("Später")
        assertEquals(listOf("10 Min.", "30 Min.", "Heute nicht"), reminders().single().actions.map { it.title.toString() })
    }

    @Test
    fun `a routine without a checklist keeps Erledigt, Spaeter and Ueberspringen`() {
        app.routine("Vitamine", "07:30")

        goOffAt("2026-10-23T07:30")

        assertEquals(listOf("Erledigt", "Später", "Überspringen"), reminders().single().actions.map { it.title.toString() })
    }

    @Test
    fun `Starten opens the run behind the app's lock, and puts the reminder off by the follow-up time`() {
        app.authenticator.deviceSecure = true
        app.routine("Morgens", "07:30", checklist = listOf("Wasser", "Zähne"), followUps = FollowUps(count = 3, everyMinutes = 20))
        goOffAt("2026-10-23T07:30")
        app.clock.now = berlin("2026-10-23T07:31")

        ActivityScenario.launch<MainActivity>(startIntent()).use {
            compose.onNodeWithText("EmilysHub ist gesperrt").assertIsDisplayed()
            compose.onNodeWithText("Wasser").assertDoesNotExist()
            assertEquals("not answered before the lock", 1, reminders().size)

            compose.unlockWithFingerprint(app.authenticator)

            compose.onNodeWithText("Wasser").assertIsDisplayed()
            compose.onNodeWithText("Fertig").assertIsDisplayed()
        }
        assertEquals(emptyList<Notification>(), reminders())
        assertEquals(listOf(OccurrenceStatus.Open), statuses())
        assertEquals("comes back at its follow-up time", berlin("2026-10-23T07:51"), nextAlarmAt())
        assertEquals("Morgens", runBlocking { app.routines.get(checkNotNull(app.runs.current()).routineId)?.name })

        goOffAt("2026-10-23T07:51")
        assertEquals("still open, it reminds again", 1, reminders().size)
    }

    @Test
    fun `Starten on a reminder answered meanwhile opens Heute and takes the reminder away`() {
        app.routine("Morgens", "07:30", checklist = listOf("Wasser"))
        goOffAt("2026-10-23T07:30")
        val intent = startIntent()
        val stillUp = notificationManager.activeNotifications.single { it.tag?.startsWith("occurrence:") == true }
        runBlocking { app.routines.skip(occurrence()) }
        // As it was before, should Android not have taken it down yet.
        notificationManager.notify(stillUp.tag, stillUp.id, stillUp.notification)

        ActivityScenario.launch<MainActivity>(intent).use {
            compose.waitForIdle()
            compose.onNodeWithText("Fertig").assertDoesNotExist()
            compose.onNode(isSelectable() and hasText("Heute")).assertIsSelected()
        }
        assertEquals(emptyList<Notification>(), reminders())
        assertNull(runBlocking { app.runs.current() })
    }

    @Test
    fun `Starten with another run running asks first, as on Heute`() {
        app.routine("Abends", "07:00", checklist = listOf("Lesen"))
        app.routine("Morgens", "07:30", checklist = listOf("Wasser"))
        val evening = runBlocking { app.routines.dueOn(app.routines.today()).first().single { it.name == "Abends" } }
        runBlocking { app.runs.start(evening.occurrenceId, evening.routineId) }
        goOffAt("2026-10-23T07:30")

        ActivityScenario.launch<MainActivity>(startIntent(of = "Morgens")).use {
            compose.onNodeWithText("„Abends“ läuft noch. Beenden und „Morgens“ starten?", substring = true).assertIsDisplayed()
            compose.onNodeWithText("Beenden und starten").performClick()

            compose.onNodeWithText("Wasser").assertIsDisplayed()
        }
    }

    @Test
    fun `the full-screen alarm offers Starten once unlocked, which closes it and opens the app at the run`() {
        app.fullScreenAlarmsAllowed = true
        app.routine("Morgens", "07:30", checklist = listOf("Wasser"), fullScreen = true)
        goOffAt("2026-10-23T07:30")
        keyguard.setKeyguardLocked(true)

        openAlarm().use { scenario ->
            compose.onNodeWithText("Starten").assertDoesNotExist()

            keyguard.setKeyguardLocked(false)
            app.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
            idle()
            listOf("Starten", "Erledigt", "Später", "Überspringen", "Heute nicht").forEach { compose.onNodeWithText(it).assertIsDisplayed() }

            compose.onNodeWithText("Starten").performClick()
            idle()

            scenario.onActivity { assertTrue("closed", it.isFinishing) }
            assertTrue("silent", !app.ringer.ringing)
            val opened = checkNotNull(shadowOf(app).nextStartedActivity)
            assertEquals(MainActivity::class.java.name, opened.component?.className)
            val asFromNotification = MainActivity.startFromReminder(app, occurrence())
            assertEquals(asFromNotification.extras?.keySet(), opened.extras?.keySet())
            assertTrue(asFromNotification.extras?.keySet().orEmpty().all { opened.extras?.get(it) == asFromNotification.extras?.get(it) })
        }
    }

    @Test
    fun `the joint reminder of coupled routines and its alarm have no Starten`() {
        app.fullScreenAlarmsAllowed = true
        val one = app.routine("Laufen", "07:30", checklist = listOf("Schuhe"), followUps = FollowUps(count = 0), fullScreen = true)
        app.routine("Yoga", "07:30", checklist = listOf("Matte"), followUps = FollowUps(count = 0), fullScreen = true, coupledWith = setOf(one))
        goOffAt("2026-10-23T07:30")
        keyguard.setKeyguardLocked(false)

        assertEquals(listOf("Erledigt", "Später", "Überspringen"), reminders().single().actions.map { it.title.toString() })
        openAlarm().use {
            compose.onNodeWithText("Laufen · Yoga").assertIsDisplayed()
            compose.onNodeWithText("Starten").assertDoesNotExist()
        }
    }

    /** What "Starten" on the reminder of [of] opens, as Android sends it. */
    private fun startIntent(of: String? = null): Intent {
        val reminder = reminders().single { of == null || it.extras.getString(Notification.EXTRA_TITLE) == of }
        return shadowOf(reminder.actions.single { it.title == "Starten" }.actionIntent).savedIntent
    }

    private fun openAlarm(): ActivityScenario<AlarmActivity> {
        val reminder = reminders().single()
        return ActivityScenario.launch(shadowOf(checkNotNull(reminder.fullScreenIntent) { "No full-screen alarm" }).savedIntent)
    }

    private fun tap(action: String) {
        reminders().single().actions.single { it.title == action }.actionIntent.send()
        idle()
    }

    /** The reminders on the screen, without the ringing's and the run's own notifications. */
    private fun reminders(): List<Notification> =
        notificationManager.activeNotifications.filter { it.tag?.startsWith("occurrence:") == true }.map { it.notification }

    private fun occurrence() = runBlocking { app.routines.dueOn(app.routines.today()).first().single().occurrenceId }

    private fun statuses() = runBlocking { app.routines.dueOn(app.routines.today()).first().map { it.status } }

    private fun nextAlarmAt(): Instant = Instant.ofEpochMilli(checkNotNull(alarmManager.peekNextScheduledAlarm()).triggerAtMs)

    private fun goOffAt(localDateTime: String) {
        app.clock.now = berlin(localDateTime)
        alarmManager.fireAlarm(checkNotNull(alarmManager.peekNextScheduledAlarm()))
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant()

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
