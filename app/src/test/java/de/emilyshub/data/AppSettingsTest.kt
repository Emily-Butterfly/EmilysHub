package de.emilyshub.data

import de.emilyshub.reminders.DefaultTones
import de.emilyshub.reminders.Tone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSettingsTest {

    private val dao = InMemorySettingsDao()
    private val defaults = Settings(design = Design.System, appLockEnabled = true, remindersAsked = false, lookAroundEnabled = true)

    @Test
    fun `a new installation follows the system design and has the app lock on`() {
        val settings = load()

        assertEquals(defaults, settings)
    }

    @Test
    fun `the chosen design is still there after a restart`() {
        for (design in Design.entries) {
            runBlocking { AppSettings({ dao }).apply { load() }.setDesign(design) }

            assertEquals(design, load().design)
        }
    }

    @Test
    fun `that the permissions for reminders were asked for is still known after a restart`() {
        assertEquals(false, load().remindersAsked)

        runBlocking { AppSettings({ dao }).apply { load() }.setRemindersAsked() }

        assertEquals(true, load().remindersAsked)
    }

    @Test
    fun `turning the app lock off and on again is still there after a restart`() {
        runBlocking { AppSettings({ dao }).apply { load() }.setAppLockEnabled(false) }
        assertEquals(false, load().appLockEnabled)

        runBlocking { AppSettings({ dao }).apply { load() }.setAppLockEnabled(true) }
        assertEquals(true, load().appLockEnabled)
    }

    @Test
    fun `looking around for to-dos waiting for a place is on until turned off, which is still there after a restart`() {
        assertEquals(true, load().lookAroundEnabled)

        runBlocking { AppSettings({ dao }).apply { load() }.setLookAroundEnabled(false) }

        assertEquals(false, load().lookAroundEnabled)
        assertEquals(false, runBlocking { AppSettings({ dao }).mayLookAround() })
    }

    @Test
    fun `the default tones are the phone's own at first, and a choice of each is still there after a restart`() {
        assertEquals(DefaultTones(), load().tones)
        val morning = Tone.Of("content://media/internal/audio/media/12")

        runBlocking { AppSettings({ dao }).apply { load() }.setAlarmTone(morning) }
        runBlocking { AppSettings({ dao }).apply { load() }.setNotificationTone(Tone.Silent) }

        assertEquals(DefaultTones(alarm = morning, notification = Tone.Silent), load().tones)
        assertEquals(DefaultTones(alarm = morning, notification = Tone.Silent), runBlocking { AppSettings({ dao }).defaultTones() })
    }

    @Test
    fun `unknown stored values fall back to the defaults, keeping the app lock on`() {
        dao.values["design"] = "Dark"
        dao.values["app-lock"] = "false"
        dao.values["look-around"] = "no"
        dao.values["alarm-tone"] = "file:///sdcard/tone.mp3"

        assertEquals(defaults, load())
    }

    @Test
    fun `the UI sees the settings once loaded and every change right away`() {
        val settings = AppSettings({ dao })
        assertNull(settings.current)

        runBlocking { settings.load() }
        assertEquals(defaults, settings.current)

        runBlocking { settings.setDesign(Design.Dark) }
        runBlocking { settings.setAppLockEnabled(false) }
        assertEquals(defaults.copy(design = Design.Dark, appLockEnabled = false), settings.current)
    }

    private fun load() = runBlocking { AppSettings({ dao }).load() }
}
