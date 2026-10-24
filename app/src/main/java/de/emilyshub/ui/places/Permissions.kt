package de.emilyshub.ui.places

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.ui.Hint
import de.emilyshub.ui.SystemSettings
import de.emilyshub.ui.openSettings

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
            TextButton(onClick = { context.openSettings(SystemSettings.app(context)) }) {
                Text(stringResource(R.string.location_access_settings))
            }
        } else {
            TextButton(onClick = { request.launch(permissions) }) { Text(allow) }
        }
    }
}
