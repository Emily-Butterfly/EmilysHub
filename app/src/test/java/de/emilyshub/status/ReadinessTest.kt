package de.emilyshub.status

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which of what the reminders need from Android is lacking, as far as the routines in use need it (SPEC.md §11). */
class ReadinessTest {

    private val nothingAllowed = Readiness(emptyMap())
    private val reminders = mapOf(Need.Notifications to Standing.Allowed, Need.ExactAlarms to Standing.Allowed)

    @Test
    fun `without routines, nothing is lacking`() {
        assertEquals(emptyList<Need>(), nothingAllowed.lacking(Use(anyRoutine = false, boundToPlaces = false, fullScreen = false)))
    }

    @Test
    fun `routines need notifications and exact alarms`() {
        val use = Use(anyRoutine = true, boundToPlaces = false, fullScreen = false)

        assertEquals(listOf(Need.Notifications, Need.ExactAlarms), nothingAllowed.lacking(use))
        assertEquals(listOf(Need.ExactAlarms), Readiness(mapOf(Need.Notifications to Standing.Allowed)).lacking(use))
    }

    @Test
    fun `allowed but turned off or down elsewhere, as the location or the reminders popping up, it is lacking too`() {
        val use = Use(anyRoutine = true, boundToPlaces = true, fullScreen = false)
        val off = Readiness(reminders + (Need.LocationAlways to Standing.Off))

        assertEquals(listOf(Need.LocationAlways), off.lacking(use))
        assertEquals(Standing.Off, off.standing(Need.LocationAlways))
        assertEquals(false, off.has(Need.LocationAlways))
        val quiet = Readiness(reminders + (Need.Notifications to Standing.Off))
        assertEquals(listOf(Need.Notifications), quiet.lacking(use.copy(boundToPlaces = false)))
    }

    @Test
    fun `routines bound to places need the location always, and those with a full-screen alarm full-screen alarms`() {
        val boundToPlaces = Use(anyRoutine = true, boundToPlaces = true, fullScreen = false)
        val fullScreen = Use(anyRoutine = true, boundToPlaces = false, fullScreen = true)

        assertEquals(listOf(Need.LocationAlways), Readiness(reminders).lacking(boundToPlaces))
        assertEquals(listOf(Need.FullScreenAlarms), Readiness(reminders).lacking(fullScreen))
    }

    @Test
    fun `no battery limits are needed only where the device puts apps to sleep, and are recommended elsewhere`() {
        val use = Use(anyRoutine = true, boundToPlaces = false, fullScreen = false)

        assertEquals(emptyList<Need>(), Readiness(reminders).lacking(use))
        assertEquals(listOf(Need.NoBatteryLimits), Readiness(reminders, putsAppsToSleep = true).lacking(use))
        assertEquals(true, Need.NoBatteryLimits.recommended)
        assertEquals(listOf(Need.NoBatteryLimits), Need.entries.filter { it.recommended })
    }
}
