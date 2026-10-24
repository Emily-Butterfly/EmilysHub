package de.emilyshub.status

/** What the reminders may need from Android (SPEC.md §11), in the order the status page lists it. */
enum class Need(
    /** Worth allowing even where the routines in use do not need it: reminders come sooner with it. */
    val recommended: Boolean = false,
) {
    /** Without them, no reminder shows; turned down, reminders no longer pop up. */
    Notifications,

    /** Without them, a reminder may come minutes late. */
    ExactAlarms,

    /** The location allowed always, for the places while the app is closed (SPEC.md §4); of no use while it is off. */
    LocationAlways,

    /** Without them, a full-screen alarm comes as a normal notification. */
    FullScreenAlarms,

    /** Kept out of Android's battery optimisation, which may hold reminders back while the phone rests. */
    NoBatteryLimits(recommended = true),
}

/** How Android stands on a [Need] now. */
enum class Standing {
    Allowed,
    NotAllowed,

    /** Allowed, but turned off or down elsewhere in Android's settings: the location, or the reminders popping up. */
    Off,
}

/**
 * How Android stands on each of what the reminders may need, none known counting as not allowed; and whether the
 * device puts apps not opened for a while to sleep, as Samsung's do.
 */
data class Readiness(val standings: Map<Need, Standing>, val putsAppsToSleep: Boolean = false) {

    fun standing(need: Need): Standing = standings[need] ?: Standing.NotAllowed

    fun has(need: Need): Boolean = standing(need) == Standing.Allowed

    /**
     * Whether the reminders in [use] need [need]: any of them notifications and exact alarms, those bound to places the
     * location always, those with a full-screen alarm full-screen alarms. No battery limits only where the device puts
     * apps to sleep, as the reminders stop altogether then; elsewhere they still come, if maybe late.
     */
    fun needs(use: Use, need: Need): Boolean = when (need) {
        Need.Notifications, Need.ExactAlarms -> use.reminds
        Need.LocationAlways -> use.boundToPlaces
        Need.FullScreenAlarms -> use.fullScreen
        Need.NoBatteryLimits -> use.reminds && putsAppsToSleep
    }

    /** What the reminders in [use] need and Android does not give them now, in the order of [Need]. */
    fun lacking(use: Use): List<Need> = Need.entries.filter { needs(use, it) && !has(it) }
}

/**
 * What the reminders use: whether anything [reminds], an active routine or an open to-do at its time or places; whether
 * any is bound to a place, a routine, or a to-do waiting for one; and whether any routine has a full-screen alarm.
 */
data class Use(val reminds: Boolean, val boundToPlaces: Boolean, val fullScreen: Boolean)
