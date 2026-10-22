package de.emilyshub.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    private var clock = 0L
    private var deviceSecure = true
    private val lock = AppLock(canAuthenticate = { deviceSecure }, now = { clock })

    @Test
    fun `a cold start is locked`() {
        lock.onForeground()

        assertTrue(lock.isLocked)
    }

    @Test
    fun `unlocking opens the app`() {
        lock.onForeground()

        lock.unlock()

        assertFalse(lock.isLocked)
    }

    @Test
    fun `a minute in the background locks the app again`() {
        unlocked()

        away(60_000)

        assertTrue(lock.isLocked)
    }

    @Test
    fun `a shorter break keeps the app open`() {
        unlocked()

        away(59_999)

        assertFalse(lock.isLocked)
    }

    @Test
    fun `short breaks do not add up`() {
        unlocked()

        repeat(3) { away(40_000) }

        assertFalse(lock.isLocked)
    }

    @Test
    fun `with the lock turned off the app opens right away and stays open`() {
        lock.onForeground()

        lock.setEnabled(false)
        assertFalse(lock.isLocked)

        away(10 * 60_000)
        assertFalse(lock.isLocked)
    }

    @Test
    fun `turning the lock on does not lock right away, only after the next break`() {
        lock.onForeground()
        lock.setEnabled(false)

        lock.setEnabled(true)
        assertFalse(lock.isLocked)

        away(60_000)
        assertTrue(lock.isLocked)
    }

    @Test
    fun `without a screen lock on the phone there is nothing to unlock with, so the app stays open`() {
        deviceSecure = false

        lock.onForeground()
        assertFalse(lock.isLocked)

        away(60_000)
        assertFalse(lock.isLocked)
    }

    @Test
    fun `removing the phone's screen lock while the app waits locked in the background opens it`() {
        lock.onForeground()
        lock.onBackground()

        deviceSecure = false
        lock.onForeground()

        assertFalse(lock.isLocked)
    }

    @Test
    fun `an unlock that completes while the app is in the background is kept`() {
        lock.onForeground()
        lock.onBackground()

        clock += 60_000
        lock.unlock()
        lock.onForeground()

        assertFalse(lock.isLocked)
    }

    @Test
    fun `a minute away after such a late unlock locks the app again`() {
        lock.onForeground()
        lock.onBackground()

        lock.unlock()
        clock += 60_000
        lock.onForeground()

        assertTrue(lock.isLocked)
    }

    /** The app leaves the screen for [millis] and comes back. */
    private fun away(millis: Int) {
        lock.onBackground()
        clock += millis
        lock.onForeground()
    }

    private fun unlocked() {
        lock.onForeground()
        lock.unlock()
    }
}
