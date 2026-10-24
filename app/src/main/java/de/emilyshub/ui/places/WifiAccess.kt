package de.emilyshub.ui.places

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import de.emilyshub.R
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.Wifi
import de.emilyshub.ui.Hint

/** Whether Android tells the app the name of the WLAN: only with the precise location allowed, and only while it is on. */
enum class WifiAccess {
    Granted,
    LocationOff,

    /** Only the approximate location allowed, which Android offers next to the precise one from Android 12 on. */
    ApproximateOnly,
    NotAllowed,
}

private fun Context.wifiAccess(): WifiAccess = when {
    !isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ->
        if (isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) WifiAccess.ApproximateOnly else WifiAccess.NotAllowed
    !LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java)) -> WifiAccess.LocationOff
    else -> WifiAccess.Granted
}

/**
 * The [WifiAccess], checked again on every return to the app, say from Android's settings, and whenever the location
 * is turned on or off, say in the quick settings, which leave the app as it is.
 */
@Composable
fun rememberWifiAccess(): Checked<WifiAccess> {
    val context = LocalContext.current
    val state = rememberChecked { wifiAccess() }
    DisposableEffect(context, state) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = state.check()
        }
        // Not exported: the system still delivers its own broadcasts, other apps cannot send any (SPEC.md §9).
        val locationTurned = IntentFilter(LocationManager.MODE_CHANGED_ACTION)
        ContextCompat.registerReceiver(context, receiver, locationTurned, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/** The WLAN connected to, while Android tells its name; none before it is known, or while [access] keeps it back. */
@Composable
fun rememberConnectedWifi(wifi: CurrentWifi, access: Checked<WifiAccess>): Wifi? {
    if (access.current != WifiAccess.Granted) return null
    // Asked anew once allowed: before, Android left the name out.
    val connected by remember(wifi, access.current) { wifi.current() }.collectAsState(initial = null)
    return connected
}

/** Why the app needs the location for the WLAN name, and the way to allow it, or to turn the location on. */
@Composable
fun WifiAccessNotice(state: Checked<WifiAccess>) {
    val context = LocalContext.current
    when (state.current) {
        WifiAccess.Granted -> Unit
        WifiAccess.NotAllowed -> PermissionNotice(
            text = stringResource(R.string.wifi_access_text),
            allow = stringResource(R.string.wifi_access_allow),
            permissions = LOCATION,
            onAnswer = state::check,
        )
        WifiAccess.ApproximateOnly -> PermissionNotice(
            text = stringResource(R.string.wifi_access_approximate_text),
            allow = stringResource(R.string.wifi_access_allow_precise),
            permissions = LOCATION,
            onAnswer = state::check,
        )
        WifiAccess.LocationOff -> Column {
            Hint(stringResource(R.string.wifi_location_off_text))
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                Text(stringResource(R.string.wifi_location_off_open))
            }
        }
    }
}

/** Precise, as the WLAN name needs, and approximate, which Android wants asked for along with it. */
private val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
