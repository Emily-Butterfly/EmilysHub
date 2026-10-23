package de.emilyshub.reminders

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import de.emilyshub.EmilysHubApplication
import de.emilyshub.routines.Reminder
import de.emilyshub.security.OutsideAppLock
import de.emilyshub.ui.reminders.AlarmScreen
import de.emilyshub.ui.theme.EmilysHubTheme
import de.emilyshub.ui.theme.isDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * The full-screen alarm of a reminder (SPEC.md §3), over the lock screen too. While the phone is locked it
 * only says "Erinnerung fällig"; once it is unlocked, it shows the routine with its actions. It goes away
 * once its reminder is answered, here or anywhere else. A newer alarm takes its place, and the older
 * reminder stays a notification. It shows no more than the reminder does, so it is not behind the app lock,
 * and its time on the screen is not time in the app. Not exported: only the reminder's full-screen intent
 * opens it.
 */
class AlarmActivity : ComponentActivity(), OutsideAppLock {
    private val app get() = application as EmilysHubApplication
    private val keyguard get() = getSystemService(KeyguardManager::class.java)

    private var reminder by mutableStateOf<Reminder?>(null)
    private var locked by mutableStateOf(true)
    private var watching: Job? = null

    /** Unlocking on the lock screen itself, say with a fingerprint, rather than from the alarm. */
    private val unlocked = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) locked = keyguard.isKeyguardLocked
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        locked = keyguard.isKeyguardLocked
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        // Not exported: the system still delivers its own broadcasts, other apps cannot send any (SPEC.md §9).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlocked, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlocked, filter)
        }
        if (!show(intent)) return

        setContent {
            LaunchedEffect(Unit) { if (app.settings.current == null) app.settings.load() }
            val design = app.settings.current?.design ?: return@setContent
            EmilysHubTheme(darkTheme = isDark(design)) {
                AlarmScreen(
                    reminder = reminder,
                    locked = locked,
                    onUnlock = ::unlock,
                    onDone = { answer { app.routines.markDone(it) } },
                    onPutOff = { by: Duration -> answer { app.reminders.putOff(it, by) } },
                    onSkip = { answer { app.routines.skip(it) } },
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    override fun onResume() {
        super.onResume()
        locked = keyguard.isKeyguardLocked
    }

    override fun onDestroy() {
        unregisterReceiver(unlocked)
        super.onDestroy()
    }

    /** Shows the occurrence in [intent] while it is due; false, and the alarm closes, without one. */
    private fun show(intent: Intent): Boolean {
        val occurrenceId = intent.getLongExtra(EXTRA_OCCURRENCE, 0).takeIf { it > 0 }
        if (occurrenceId == null) {
            finish()
            return false
        }
        watching?.cancel()
        watching = lifecycleScope.launch {
            app.routines.reminder(occurrenceId).collect { due -> if (due == null) finish() else reminder = due }
        }
        return true
    }

    private fun unlock() = keyguard.requestDismissKeyguard(
        this,
        object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                locked = keyguard.isKeyguardLocked
            }
        },
    )

    /** Does [work] on the occurrence shown, to the end even though the alarm closes at once. */
    private fun answer(work: suspend (occurrenceId: Long) -> Unit) {
        val occurrenceId = reminder?.occurrenceId ?: return
        app.background.launch { work(occurrenceId) }
        finish()
    }

    companion object {
        const val EXTRA_OCCURRENCE = "occurrence"
    }
}
