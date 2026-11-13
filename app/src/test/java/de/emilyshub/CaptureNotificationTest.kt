package de.emilyshub

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.RemoteInput
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.inbox.CaptureNotification
import de.emilyshub.inbox.CaptureReceiver
import de.emilyshub.reminders.RescheduleReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * "Gedanke festhalten" in the notifications (SPEC.md §16, #95): off at first, a quiet notification that stays once on,
 * whose field writes into the inbox and which shows nothing of it.
 */
@RunWith(AndroidJUnit4::class)
class CaptureNotificationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Test
    fun `off at first, turned on it stands quietly with Schreiben, turned off it and its channel are gone`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        launchApp().use {
            openSettings()
            captureSwitch().performScrollTo().assertIsOff()
            assertNull(shown())

            captureSwitch().performClick().assertIsOn()

            val notification = checkNotNull(shown())
            assertEquals("Gedanke festhalten", notification.extras.getString(Notification.EXTRA_TITLE))
            assertNull(notification.extras.getCharSequence(Notification.EXTRA_TEXT))
            assertTrue("stays", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(Notification.VISIBILITY_PUBLIC, notification.visibility)
            val channel = checkNotNull(manager.getNotificationChannel(CaptureNotification.CHANNEL))
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
            assertNull(channel.sound)
            val write = notification.actions.single()
            assertEquals("Schreiben", write.title)
            assertEquals(CaptureNotification.KEY_TEXT, write.remoteInputs.single().resultKey)

            captureSwitch().performClick().assertIsOff()

            assertNull(shown())
            assertNull(manager.getNotificationChannel(CaptureNotification.CHANNEL))
        }
    }

    @Test
    fun `without notifications allowed the switch asks, and stays off when declined`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        launchApp().use { scenario ->
            openSettings()
            captureSwitch().performScrollTo().performClick()
            scenario.onActivity { activity ->
                val request = checkNotNull(shadowOf(activity).lastRequestedPermission)
                assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request.requestedPermissions.toList())
                activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED))
            }

            captureSwitch().assertIsOff()
            compose.onNodeWithText("Dafür braucht die App die Erlaubnis", substring = true).assertExists()

            // Asked once already, the next tap leads to Android's settings.
            captureSwitch().performClick()
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, shadowOf(app).nextStartedActivity?.action)
        }
        assertNull(shown())
    }

    @Test
    fun `notifications forbidden later, the switch shows off and says why`() {
        turnOn()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        launchApp().use {
            openSettings()
            captureSwitch().performScrollTo().assertIsOff()
            compose.onNodeWithText("Dafür braucht die App die Erlaubnis", substring = true).assertExists()
        }
        assertNull(shown())
    }

    @Test
    fun `writing takes the phone unlocked and stays on it`() {
        turnOn()
        val notification = checkNotNull(shown())

        assertTrue(notification.actions.single().isAuthenticationRequired)
        assertTrue("local only", notification.flags and Notification.FLAG_LOCAL_ONLY != 0)
    }

    @Test
    fun `what is written goes into the inbox, and the notification stands empty again without it`() {
        turnOn()

        reply("Zahnarzt anrufen")

        assertEquals(listOf("Zahnarzt anrufen"), texts())
        val notification = checkNotNull(shown())
        assertNull(notification.extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY))
        assertFalse(notification.extras.toString().contains("Zahnarzt"))
        assertEquals(null, notification.extras.getCharSequence(Notification.EXTRA_TEXT))
    }

    @Test
    fun `the same checks as in the app, nothing to see and too long are not kept`() {
        turnOn()

        reply("   ")
        reply("a".repeat(1001))

        assertEquals(emptyList<String>(), texts())
    }

    @Test
    fun `intents not from the field are ignored`() {
        turnOn()

        send(Intent("de.emilyshub.action.OTHER").also { RemoteInput.addResultsToIntent(field(), it, results("Fremd")) })
        send(Intent(CaptureReceiver.ACTION_CAPTURE).putExtra(CaptureNotification.KEY_TEXT, "Fremd"))
        idle()

        assertEquals(emptyList<String>(), texts())
    }

    @Test
    fun `after a restart of the phone or an update it stands again, and not while off`() {
        turnOn()
        manager.cancelAll()

        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, RescheduleReceiver::class.java))
        idle()
        assertTrue(shown() != null)

        runBlocking { app.settings.setCaptureInNotifications(false) }
        app.sendBroadcast(Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setClass(app, RescheduleReceiver::class.java))
        idle()
        assertNull(shown())
    }

    @Test
    fun `swiped away, it is back on the next opening of the app`() {
        turnOn()
        manager.cancelAll()

        launchApp().use { assertTrue(shown() != null) }
    }

    @Test
    fun `its field sends to the app's own receiver, mutable, and tapped it opens the line for a thought`() {
        turnOn()
        val notification = checkNotNull(shown())

        val reply = shadowOf(notification.actions.single().actionIntent)
        assertFalse("the field needs a mutable one", reply.isImmutable)
        assertEquals(CaptureReceiver::class.java.name, reply.savedIntent.component?.className)
        val open = shadowOf(notification.contentIntent)
        assertTrue("immutable", open.isImmutable)
        assertEquals(MainActivity::class.java.name, open.savedIntent.component?.className)
        assertTrue(open.savedIntent.getBooleanExtra("de.emilyshub.extra.CAPTURE", false))
    }

    @Test
    fun `exactly one PendingIntent of the app is mutable, the one of this field (SPEC md 9)`() {
        val mutable = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> file.readLines().filter { "FLAG_MUTABLE" in it }.map { file.name } }
            .toList()

        assertEquals(listOf("CaptureNotification.kt"), mutable)
    }

    private fun turnOn() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking {
            app.settings.load()
            app.settings.setCaptureInNotifications(true)
        }
        idle()
    }

    /** Android sends what was typed in the field, as its PendingIntent fills it in. */
    private fun reply(text: String) {
        val intent = Intent(CaptureReceiver.ACTION_CAPTURE)
        RemoteInput.addResultsToIntent(field(), intent, results(text))
        send(intent)
        idle()
    }

    /** To the app's receiver, as the field's PendingIntent or another sender would. */
    private fun send(intent: Intent) = app.sendBroadcast(intent.setClass(app, CaptureReceiver::class.java))

    private fun field() = arrayOf(RemoteInput.Builder(CaptureNotification.KEY_TEXT).build())

    private fun results(text: String) = Bundle().apply { putCharSequence(CaptureNotification.KEY_TEXT, text) }

    private fun shown(): Notification? =
        manager.activeNotifications.find { it.tag == CaptureNotification.NOTIFICATION_TAG }?.notification

    private fun openSettings() = compose.onNodeWithContentDescription("Einstellungen").performClick()

    private fun captureSwitch() = compose.onNode(isToggleable() and hasText("Gedanke festhalten in den Benachrichtigungen"))

    private fun texts() = runBlocking { app.inbox.entries().first().map { it.text } }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
