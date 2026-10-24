package de.emilyshub.ui.places

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.emilyshub.R
import de.emilyshub.ui.isGranted
import de.emilyshub.ui.rememberChecked

/**
 * For a routine bound to a place (SPEC.md §4, §11): first the precise location, as for the WLAN name at all, then the
 * location in the background, so that Android tells the WLAN name with the app closed and a reminder pops up on
 * arrival; without it, the routine reminds everywhere at its time. Asked for only here, each with why. From Android 11
 * on, Android asks this in its own settings, where "Immer erlauben" is one of the choices.
 */
@Composable
fun BackgroundLocationNotice() {
    val access = rememberLocationAccess()
    if (access.current != LocationAccess.Granted) return LocationAccessNotice(access)
    val background = rememberChecked { isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
    if (background.current) return
    PermissionNotice(
        text = stringResource(R.string.background_location_text),
        allow = stringResource(R.string.background_location_allow),
        permissions = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        onAnswer = background::check,
    )
}
