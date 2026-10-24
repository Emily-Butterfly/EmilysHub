package de.emilyshub.ui.places

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R
import de.emilyshub.ui.Hint

/** What Android allows the app, as it is now, read again by [check]. */
class Checked<T>(private val read: () -> T) {
    var current by mutableStateOf(read())
        private set

    fun check() {
        current = read()
    }
}

/** Reads what Android allows with [read], and again on every return to the app, say from Android's settings. */
@Composable
fun <T> rememberChecked(read: Context.() -> T): Checked<T> {
    val context = LocalContext.current
    val state = remember(context) { Checked { context.read() } }
    LifecycleResumeEffect(state) {
        state.check()
        onPauseOrDispose {}
    }
    return state
}

fun Context.isGranted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/**
 * Why the app needs [permissions], the first of which matters, and the way to allow them (SPEC.md §11: asked only when
 * needed, with a German explanation): Android's own question, [allow], or, once it was turned down for good, the
 * app's settings. [onAnswer] reads again what Android allows.
 */
@Composable
fun PermissionNotice(text: String, allow: String, permissions: Array<String>, onAnswer: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var turnedDown by rememberSaveable { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        onAnswer()
        val permission = permissions.first()
        // Android asks no more once turned down for good, and says so by no longer wanting the reason told.
        turnedDown = granted[permission] != true && activity?.shouldShowRequestPermissionRationale(permission) == false
    }
    Column {
        Hint(text)
        if (turnedDown) {
            TextButton(onClick = { context.startActivity(appSettings(context)) }) {
                Text(stringResource(R.string.wifi_access_settings))
            }
        } else {
            TextButton(onClick = { request.launch(permissions) }) { Text(allow) }
        }
    }
}

private fun appSettings(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
