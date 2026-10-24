package de.emilyshub.ui.places

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.Wifi

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

private fun Context.isGranted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/** The [WifiAccess] as it is now, read again by [check]. */
class WifiAccessState(private val read: () -> WifiAccess) {
    var current by mutableStateOf(read())
        private set

    fun check() {
        current = read()
    }
}

/**
 * The [WifiAccess], checked again on every return to the app, say from Android's settings, and whenever the location
 * is turned on or off, say in the quick settings, which leave the app as it is.
 */
@Composable
fun rememberWifiAccess(): WifiAccessState {
    val context = LocalContext.current
    val state = remember(context) { WifiAccessState { context.wifiAccess() } }
    LifecycleResumeEffect(state) {
        state.check()
        onPauseOrDispose {}
    }
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
fun rememberConnectedWifi(wifi: CurrentWifi, access: WifiAccessState): Wifi? {
    if (access.current != WifiAccess.Granted) return null
    // Asked anew once allowed: before, Android left the name out.
    val connected by remember(wifi, access.current) { wifi.current() }.collectAsState(initial = null)
    return connected
}

/**
 * Why the app needs the location for the WLAN name, and the way to allow it (SPEC.md §11: asked only when needed,
 * with a German explanation): Android's own question, or, once that was turned down for good, the app's settings.
 */
@Composable
fun WifiAccessNotice(state: WifiAccessState) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var turnedDown by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        state.check()
        // Android asks no more once turned down for good, and says so by no longer wanting the reason told.
        turnedDown = granted[Manifest.permission.ACCESS_FINE_LOCATION] != true && activity?.mayAskAgain() == false
    }
    Column {
        when (state.current) {
            WifiAccess.Granted -> Unit
            WifiAccess.NotAllowed, WifiAccess.ApproximateOnly -> {
                val approximate = state.current == WifiAccess.ApproximateOnly
                Notice(stringResource(if (approximate) R.string.wifi_access_approximate_text else R.string.wifi_access_text))
                if (turnedDown) {
                    TextButton(onClick = { context.startActivity(appSettings(context)) }) {
                        Text(stringResource(R.string.wifi_access_settings))
                    }
                } else {
                    TextButton(onClick = { request.launch(LOCATION) }) {
                        Text(stringResource(if (approximate) R.string.wifi_access_allow_precise else R.string.wifi_access_allow))
                    }
                }
            }
            WifiAccess.LocationOff -> {
                Notice(stringResource(R.string.wifi_location_off_text))
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text(stringResource(R.string.wifi_location_off_open))
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Whether Android would still ask for the precise location, after it was turned down once. */
private fun Activity.mayAskAgain() = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

/** Precise, as the WLAN name needs, and approximate, which Android wants asked for along with it. */
private val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun appSettings(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
