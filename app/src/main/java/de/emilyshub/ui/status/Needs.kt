package de.emilyshub.ui.status

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import de.emilyshub.R
import de.emilyshub.reminders.REMINDER_CHANNEL
import de.emilyshub.reminders.exactAlarmsAllowed
import de.emilyshub.reminders.fullScreenAlarmsAllowed
import de.emilyshub.reminders.notificationsAllowed
import de.emilyshub.reminders.remindersPopUp
import de.emilyshub.routines.RoutineStatus
import de.emilyshub.routines.Routines
import de.emilyshub.status.Need
import de.emilyshub.status.Readiness
import de.emilyshub.status.Standing
import de.emilyshub.status.Use
import de.emilyshub.ui.SystemSettings
import de.emilyshub.ui.isGranted
import de.emilyshub.ui.places.locationOn
import kotlinx.coroutines.flow.combine

/** How Android stands now on each of what the reminders may need, and whether it is a Samsung. */
fun Context.readiness() = Readiness(
    standings = Need.entries.associateWith { standingOf(it) },
    putsAppsToSleep = Build.MANUFACTURER.equals("samsung", ignoreCase = true),
)

private fun Context.standingOf(need: Need): Standing = when (need) {
    Need.Notifications -> standing(allowed = notificationsAllowed(), on = remindersPopUp())
    Need.ExactAlarms -> standing(allowed = exactAlarmsAllowed())
    Need.LocationAlways -> standing(
        allowed = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) && isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        on = locationOn(),
    )
    Need.FullScreenAlarms -> standing(allowed = fullScreenAlarmsAllowed())
    Need.NoBatteryLimits -> standing(allowed = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName))
}

private fun standing(allowed: Boolean, on: Boolean = true) = when {
    !allowed -> Standing.NotAllowed
    !on -> Standing.Off
    else -> Standing.Allowed
}

/** What the active routines use, as it changes; none until it is known. */
@Composable
fun rememberUse(routines: Routines): Use? {
    val use = remember(routines) {
        combine(routines.inUse(), routines.boundToPlaces()) { all, boundToPlaces ->
            val active = all.filter { it.status == RoutineStatus.Active }
            Use(anyRoutine = active.isNotEmpty(), boundToPlaces = boundToPlaces, fullScreen = active.any { it.fullScreen })
        }
    }
    return use.collectAsState(initial = null).value
}

/**
 * What the status page and Heute say of a need: its [title], what it is for ([purpose]), and the button to allow it
 * ([allow]); for one turned off elsewhere, what is off ([off]), the button to turn it on ([turnOn]), and how Heute
 * names it then ([offTitle]).
 */
internal class NeedTexts(
    @StringRes val title: Int,
    @StringRes val purpose: Int,
    @StringRes val allow: Int,
    @StringRes val off: Int = 0,
    @StringRes val turnOn: Int = 0,
    @StringRes val offTitle: Int = 0,
)

internal val Need.texts: NeedTexts
    get() = when (this) {
        Need.Notifications -> NeedTexts(
            R.string.need_notifications, R.string.need_notifications_purpose, R.string.need_notifications_allow,
            off = R.string.need_notifications_off, turnOn = R.string.need_notifications_turn_on,
            offTitle = R.string.need_notifications_off_title,
        )
        Need.ExactAlarms ->
            NeedTexts(R.string.need_exact_alarms, R.string.need_exact_alarms_purpose, R.string.need_exact_alarms_allow)
        Need.LocationAlways -> NeedTexts(
            R.string.need_location_always, R.string.need_location_always_purpose, R.string.need_location_always_allow,
            off = R.string.need_location_off, turnOn = R.string.location_off_open, offTitle = R.string.need_location_off_title,
        )
        Need.FullScreenAlarms ->
            NeedTexts(R.string.need_full_screen, R.string.need_full_screen_purpose, R.string.need_full_screen_allow)
        Need.NoBatteryLimits -> NeedTexts(
            R.string.need_no_battery_limits, R.string.need_no_battery_limits_purpose, R.string.need_no_battery_limits_allow,
        )
    }

/** Where in Android's settings the need is allowed, turned on again ([Standing.Off]), or seen to while allowed. */
internal fun Need.settings(context: Context, standing: Standing): Intent = when (this) {
    Need.Notifications -> when (standing) {
        Standing.Off -> SystemSettings.notificationChannel(context, REMINDER_CHANNEL)
        else -> SystemSettings.notifications(context)
    }
    Need.ExactAlarms -> SystemSettings.exactAlarms(context)
    Need.LocationAlways -> when (standing) {
        Standing.Off -> SystemSettings.location()
        // Android allows the location always only on the app's page, under its permissions.
        else -> SystemSettings.app(context)
    }
    Need.FullScreenAlarms -> SystemSettings.fullScreenAlarms(context)
    Need.NoBatteryLimits -> when (standing) {
        // Asking again would change nothing: the list of apps without limits is where it is undone.
        Standing.Allowed -> SystemSettings.batteryOptimisation()
        else -> SystemSettings.noBatteryLimits(context)
    }
}
