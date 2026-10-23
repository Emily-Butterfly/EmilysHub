package de.emilyshub.reminders

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
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
 * only says "Erinnerung fällig"; once it is unlocked, it shows the routine with its actions. A newer alarm
 * comes first; once a reminder is answered, here or anywhere else, the alarm shows the next one still due,
 * or goes away. It shows no more than the reminder does, so it is not behind the app lock, and its time on
 * the screen is not time in the app. Not exported: only the reminder's full-screen intent opens it.
 */
class AlarmActivity : ComponentActivity(), OutsideAppLock {
    private val app get() = application as EmilysHubApplication
    private val keyguard get() = getSystemService(KeyguardManager::class.java)

    /** The occurrences whose alarms came, the newest first; the alarm shows the first. */
    private var queue = listOf<Long>()
    private var reminder by mutableStateOf<Reminder?>(null)
    private var locked by mutableStateOf(true)
    private var watching: Job? = null

    /** Unlocking on the lock screen itself, say with a fingerprint, rather than from the alarm. */
    private val userPresent = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) checkLocked()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        checkLocked()
        // Not exported: the system still delivers its own broadcasts, other apps cannot send any (SPEC.md §9).
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(this, userPresent, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        queue = savedInstanceState?.getLongArray(QUEUE)?.toList() ?: listOfNotNull(intent.occurrenceId())
        showFirst()

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
        val occurrenceId = intent.occurrenceId() ?: return
        queue = listOf(occurrenceId) + (queue - occurrenceId)
        showFirst()
    }

    override fun onResume() {
        super.onResume()
        checkLocked()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLongArray(QUEUE, queue.toLongArray())
    }

    override fun onDestroy() {
        unregisterReceiver(userPresent)
        super.onDestroy()
    }

    /** Shows the first occurrence in the [queue] while it is due, then the next; with none left, the alarm closes. */
    private fun showFirst() {
        watching?.cancel()
        reminder = null
        val occurrenceId = queue.firstOrNull() ?: return finish()
        watching = lifecycleScope.launch {
            app.routines.reminder(occurrenceId).collect { due -> if (due == null) drop(occurrenceId) else reminder = due }
        }
    }

    /** Takes the occurrence out of the [queue], however often it is answered or gone, and shows what is first then. */
    private fun drop(occurrenceId: Long) {
        queue = queue - occurrenceId
        showFirst()
    }

    private fun checkLocked() {
        locked = keyguard.isKeyguardLocked
    }

    private fun unlock() = keyguard.requestDismissKeyguard(
        this,
        object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = checkLocked()
        },
    )

    /**
     * Goes on to the next occurrence and does [work] on the one shown, to the end even if the alarm closes. In
     * that order, so that the work being done cannot take the next one out of the queue too.
     */
    private fun answer(work: suspend (occurrenceId: Long) -> Unit) {
        val occurrenceId = reminder?.occurrenceId ?: return
        drop(occurrenceId)
        app.background.launch { work(occurrenceId) }
    }

    private companion object {
        const val QUEUE = "queue"
    }
}
