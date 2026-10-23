package de.emilyshub.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppSettingsTest {

    private val dao = InMemorySettingsDao()

    @Test
    fun `a new installation follows the system design and has the app lock on`() {
        val settings = load()

        assertEquals(Settings(design = Design.System, appLockEnabled = true, remindersAsked = false), settings)
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
    fun `unknown stored values fall back to the defaults, keeping the app lock on`() {
        dao.values["design"] = "Dark"
        dao.values["app-lock"] = "false"

        assertEquals(Settings(design = Design.System, appLockEnabled = true, remindersAsked = false), load())
    }

    @Test
    fun `the UI sees the settings once loaded and every change right away`() {
        val settings = AppSettings({ dao })
        assertNull(settings.current)

        runBlocking { settings.load() }
        assertEquals(Settings(design = Design.System, appLockEnabled = true, remindersAsked = false), settings.current)

        runBlocking { settings.setDesign(Design.Dark) }
        runBlocking { settings.setAppLockEnabled(false) }
        assertEquals(Settings(design = Design.Dark, appLockEnabled = false, remindersAsked = false), settings.current)
    }

    private fun load() = runBlocking { AppSettings({ dao }).load() }
}
