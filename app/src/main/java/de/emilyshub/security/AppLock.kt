package de.emilyshub.security

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * When the app has to be unlocked first (SPEC.md §9): on a cold start and after at least one minute
 * in the background. [canAuthenticate] tells whether the phone has anything to unlock with.
 *
 * [now] is a monotonic clock that keeps running in deep sleep, so neither changing the phone's
 * clock nor a sleeping phone can shorten the minute.
 */
class AppLock(
    private val canAuthenticate: () -> Boolean,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    /** Compose state: the UI shows no content while this is true. */
    var isLocked by mutableStateOf(true)
        private set

    private var enabled = true
    private var backgroundSince: Long? = null

    fun unlock() {
        isLocked = false
        // A late unlock, completed while the app was already in the background, starts no break.
        backgroundSince = null
    }

    /** The "App-Sperre" setting. Turning it on never locks right away: whoever turns it on is already in. */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) isLocked = false
    }

    /** The app has left the screen. */
    fun onBackground() {
        backgroundSince = now()
    }

    /** The app is about to show again, before anything is drawn. */
    fun onForeground() {
        val since = backgroundSince
        backgroundSince = null
        when {
            // Without a fingerprint or screen lock, the prompt has nothing to ask for. Nothing is lost
            // by staying open: removing the screen lock takes its PIN, which would unlock the app too.
            !canAuthenticate() -> isLocked = false
            enabled && since != null && now() - since >= TIMEOUT_MILLIS -> isLocked = true
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 60_000L
    }
}
