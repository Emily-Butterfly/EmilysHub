package de.emilyshub.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

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
