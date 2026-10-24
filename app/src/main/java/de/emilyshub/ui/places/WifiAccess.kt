package de.emilyshub.ui.places

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R

/** Whether Android tells the app the name of the WLAN: only with the precise location allowed, and only while it is on. */
enum class WifiAccess {
    Granted,
    LocationOff,
    NotAllowed,
}

private fun Context.wifiAccess(): WifiAccess = when {
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> WifiAccess.NotAllowed
    !LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java)) -> WifiAccess.LocationOff
    else -> WifiAccess.Granted
}

/** The [WifiAccess] now, checked again on every return to the app, say from Android's settings. */
@Composable
fun rememberWifiAccess(): WifiAccessState {
    val context = LocalContext.current
    val state = remember(context) { WifiAccessState { context.wifiAccess() } }
    LifecycleResumeEffect(state) {
        state.check()
        onPauseOrDispose {}
    }
    return state
}

class WifiAccessState(private val read: () -> WifiAccess) {
    var current by mutableStateOf(read())
        private set

    fun check() {
        current = read()
    }
}

/**
 * Why the app needs the location for the WLAN name, and the way to allow it (SPEC.md §11: asked only when needed,
 * with a German explanation): first Android's own question, then, once that was turned down, the app's settings.
 */
@Composable
fun WifiAccessNotice(state: WifiAccessState) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        state.check()
    }
    Column {
        when (state.current) {
            WifiAccess.Granted -> Unit
            WifiAccess.NotAllowed -> {
                Notice(stringResource(R.string.wifi_access_text))
                if (asked) {
                    TextButton(onClick = { context.startActivity(appSettings(context)) }) {
                        Text(stringResource(R.string.wifi_access_settings))
                    }
                } else {
                    TextButton(
                        onClick = {
                            request.launch(LOCATION)
                        },
                    ) { Text(stringResource(R.string.wifi_access_allow)) }
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

/** Precise, as the WLAN name needs, and approximate, which Android wants asked for along with it. */
private val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun appSettings(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
