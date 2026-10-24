package de.emilyshub.status

/** What the reminders may need from Android (SPEC.md §11), in the order the status page lists it. */
enum class Need {
    /** Without them, no reminder shows. */
    Notifications,

    /** Without them, a reminder may come minutes late. */
    ExactAlarms,

    /** The location allowed always, for the places while the app is closed (SPEC.md §4). */
    LocationAlways,

    /** Without them, a full-screen alarm comes as a normal notification. */
    FullScreenAlarms,

    /** Kept out of Android's battery optimisation, which may hold reminders back while the phone rests. */
    NoBatteryLimits,
}

/** What Android allows the app now, of what the reminders may need, and whether the location is turned on. */
data class Readiness(val allowed: Set<Need>, val locationOn: Boolean) {

    /** Whether Android allows [need] now; the location always only counts while the location is turned on. */
    fun has(need: Need): Boolean = need in allowed && (need != Need.LocationAlways || locationOn)

    /** What the routines in [use] call for and Android does not allow, in the order of [Need]. */
    fun lacking(use: Use): List<Need> = Need.entries.filter { it in use.calledFor() && !has(it) }
}

/**
 * What the active routines use: any at all, any bound to a place, any with a full-screen alarm. Battery limits are
 * never called for: reminders still come with them, if maybe late.
 */
data class Use(val routines: Boolean, val places: Boolean, val fullScreen: Boolean) {
    fun calledFor(): Set<Need> = buildSet {
        if (routines) addAll(listOf(Need.Notifications, Need.ExactAlarms))
        if (places) add(Need.LocationAlways)
        if (fullScreen) add(Need.FullScreenAlarms)
    }
}
