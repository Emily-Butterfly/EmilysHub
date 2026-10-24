package de.emilyshub.ui.status

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.core.location.LocationManagerCompat
import de.emilyshub.R
import de.emilyshub.reminders.exactAlarmsAllowed
import de.emilyshub.reminders.fullScreenAlarmsAllowed
import de.emilyshub.reminders.notificationsAllowed
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.routines.Routines
import de.emilyshub.status.Need
import de.emilyshub.status.Readiness
import de.emilyshub.status.Use
import de.emilyshub.ui.SystemSettings
import de.emilyshub.ui.isGranted
import kotlinx.coroutines.flow.combine

/** What Android allows the app now, of what the reminders may need. */
fun Context.readiness() = Readiness(
    allowed = Need.entries.filter { allows(it) }.toSet(),
    locationOn = LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java)),
)

private fun Context.allows(need: Need): Boolean = when (need) {
    Need.Notifications -> notificationsAllowed()
    Need.ExactAlarms -> exactAlarmsAllowed()
    Need.LocationAlways ->
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) && isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    Need.FullScreenAlarms -> fullScreenAlarmsAllowed()
    Need.NoBatteryLimits -> getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
}

/** What the active routines use, as it changes; none until it is known. */
@Composable
fun rememberUse(routines: Routines): Use? {
    val use = remember(routines) {
        combine(routines.inUse(), routines.boundToPlaces()) { all, places ->
            val active = all.filter { it.status == RoutineStatus.Active }
            Use(routines = active.isNotEmpty(), places = places, fullScreen = active.any { it.fullScreen })
        }
    }
    return use.collectAsState(initial = null).value
}

/** How the status page names [Need]s, says what each is for, and where in Android's settings it is allowed. */
internal val Need.title: Int
    @StringRes get() = when (this) {
        Need.Notifications -> R.string.need_notifications
        Need.ExactAlarms -> R.string.need_exact_alarms
        Need.LocationAlways -> R.string.need_location_always
        Need.FullScreenAlarms -> R.string.need_full_screen
        Need.NoBatteryLimits -> R.string.need_no_battery_limits
    }

internal val Need.purpose: Int
    @StringRes get() = when (this) {
        Need.Notifications -> R.string.need_notifications_purpose
        Need.ExactAlarms -> R.string.need_exact_alarms_purpose
        Need.LocationAlways -> R.string.need_location_always_purpose
        Need.FullScreenAlarms -> R.string.need_full_screen_purpose
        Need.NoBatteryLimits -> R.string.need_no_battery_limits_purpose
    }

internal val Need.allow: Int
    @StringRes get() = when (this) {
        Need.Notifications -> R.string.need_notifications_allow
        Need.ExactAlarms -> R.string.need_exact_alarms_allow
        Need.LocationAlways -> R.string.need_location_always_allow
        Need.FullScreenAlarms -> R.string.need_full_screen_allow
        Need.NoBatteryLimits -> R.string.need_no_battery_limits_allow
    }

internal fun Need.settings(context: Context): Intent = when (this) {
    Need.Notifications -> SystemSettings.notifications(context)
    Need.ExactAlarms -> SystemSettings.exactAlarms(context)
    // Android allows the location always only on the app's page, under its permissions.
    Need.LocationAlways -> SystemSettings.app(context)
    Need.FullScreenAlarms -> SystemSettings.fullScreenAlarms(context)
    Need.NoBatteryLimits -> SystemSettings.noBatteryLimits(context)
}
