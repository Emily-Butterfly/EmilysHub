package de.emilyshub.status

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which of what the reminders need from Android is lacking, as far as the routines in use call for it (SPEC.md §11). */
class ReadinessTest {

    private val nothingAllowed = Readiness(allowed = emptySet(), locationOn = false)

    @Test
    fun `without routines, nothing is lacking`() {
        assertEquals(emptyList<Need>(), nothingAllowed.lacking(Use(routines = false, places = false, fullScreen = false)))
    }

    @Test
    fun `routines need notifications and exact alarms`() {
        val use = Use(routines = true, places = false, fullScreen = false)

        assertEquals(listOf(Need.Notifications, Need.ExactAlarms), nothingAllowed.lacking(use))
        assertEquals(listOf(Need.ExactAlarms), Readiness(setOf(Need.Notifications), locationOn = false).lacking(use))
    }

    @Test
    fun `routines bound to places need the location allowed always, and turned on`() {
        val use = Use(routines = true, places = true, fullScreen = false)
        val reminders = setOf(Need.Notifications, Need.ExactAlarms)

        assertEquals(listOf(Need.LocationAlways), Readiness(reminders, locationOn = true).lacking(use))
        assertEquals(listOf(Need.LocationAlways), Readiness(reminders + Need.LocationAlways, locationOn = false).lacking(use))
        assertEquals(emptyList<Need>(), Readiness(reminders + Need.LocationAlways, locationOn = true).lacking(use))
    }

    @Test
    fun `routines with a full-screen alarm need full-screen alarms allowed`() {
        val reminders = Readiness(setOf(Need.Notifications, Need.ExactAlarms), locationOn = false)

        assertEquals(listOf(Need.FullScreenAlarms), reminders.lacking(Use(routines = true, places = false, fullScreen = true)))
    }

    @Test
    fun `battery limits never count as lacking, as reminders still come, if maybe late`() {
        val everythingElse = Readiness(Need.entries.toSet() - Need.NoBatteryLimits, locationOn = true)

        assertEquals(emptyList<Need>(), everythingElse.lacking(Use(routines = true, places = true, fullScreen = true)))
        assertEquals(false, everythingElse.has(Need.NoBatteryLimits))
    }

    @Test
    fun `whether each one is there, the location always only while it is turned on`() {
        assertEquals(true, Readiness(setOf(Need.LocationAlways), locationOn = true).has(Need.LocationAlways))
        assertEquals(false, Readiness(setOf(Need.LocationAlways), locationOn = false).has(Need.LocationAlways))
        assertEquals(true, Readiness(setOf(Need.Notifications), locationOn = false).has(Need.Notifications))
    }
}
