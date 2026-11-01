package de.emilyshub.reminders

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.emilyshub.EmilysHubApplication
import de.emilyshub.R
import de.emilyshub.data.InputText
import de.emilyshub.security.OutsideAppLock
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.reminders.AlarmChoice
import de.emilyshub.ui.reminders.AlarmScreen
import de.emilyshub.ui.reminders.AlarmShown
import de.emilyshub.ui.reminders.LockScreenShown
import de.emilyshub.ui.theme.EmilysHubTheme
import de.emilyshub.ui.theme.isDark
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * The full-screen alarm of a reminder (SPEC.md §3, §5), over the lock screen too. While the phone is locked it
 * only says "Erinnerung fällig", or the name and time of one that allows them there; once it is unlocked, it
 * shows the routine or to-do with its actions. A newer alarm comes first; once a reminder is answered, here or anywhere else, the alarm shows the next one still due,
 * or goes away. It shows no more than the reminder does, so it is not behind the app lock, and its time on
 * the screen is not time in the app. Routines and to-dos take turns in it alike. Not exported: only the reminder opens
 * it, with its full-screen intent or when it is tapped.
 *
 * Like an alarm clock, it rings with the tone of the one it shows, the newest ([Sounds.alarmOf]), and keeps the screen
 * on when an alarm comes, for at most [AlarmRinger.LIMIT]; opened
 * by a tap on the reminder, it only rings on if the alarm rings already in the background ([AlarmRingService]).
 * Answering the reminder, here or anywhere else, closing the alarm or leaving it for longer than
 * [LEAVE_GRACE], say with the power button or for a call, silences it until the next alarm.
 */
class AlarmActivity : ComponentActivity(), OutsideAppLock {
    private val app get() = application as EmilysHubApplication
    private val keyguard get() = getSystemService(KeyguardManager::class.java)

    /** The occurrences and to-dos whose alarms came, the newest first; the alarm shows the first. */
    private var queue = listOf<Alarmed>()
    private var showing by mutableStateOf<Showing?>(null)
    private var locked by mutableStateOf(true)
    private var watching: Job? = null

    /** Silences the alarm at [ringUntil]; none while it is silent. */
    private var ringLimit: Job? = null

    /** Starts the ringing with the tone of what is first, once it is read; none once it rings or is silent. */
    private var ringStart: Job? = null

    /** When the ringing ends, on [SystemClock.elapsedRealtime]; 0 while it is silent. Kept across a rotation. */
    private var ringUntil = 0L

    /** Silences the alarm once it has been off the screen for [LEAVE_GRACE]. */
    private var leaving: Job? = null

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
        queue = savedInstanceState?.getStringArray(QUEUE)?.mapNotNull(Alarmed::of) ?: listOfNotNull(intent.alarmed())
        showFirst()
        when {
            queue.isEmpty() -> Unit
            savedInstanceState == null -> if (intent.rings()) ring()
            // Turned while it rang: it rings on, for as long as it had left.
            savedInstanceState.getLong(RING_UNTIL) > 0 -> ring(until = savedInstanceState.getLong(RING_UNTIL))
        }

        setContent {
            LaunchedEffect(Unit) { if (app.settings.current == null) app.settings.load() }
            val design = app.settings.current?.design ?: return@setContent
            EmilysHubTheme(darkTheme = isDark(design)) {
                val shown = showing
                AlarmScreen(
                    shown = shown?.screen,
                    putOffChoices = shown?.putOffChoices?.invoke().orEmpty(),
                    locked = locked,
                    onUnlock = ::unlock,
                    onDone = { answer(ofOccurrence = app.routines::markDone, ofTodo = app.todos::markDone) },
                    // One of a joint alarm: that one done skips the others (SPEC.md §2).
                    onDoneOf = { id -> answer(ofOccurrence = { app.routines.markDone(id) }) },
                    onPutOff = { by: Duration ->
                        answer(ofOccurrence = { app.reminders.putOff(it, by) }, ofTodo = { app.reminders.putOffTodo(it, by) })
                    },
                    // For all a joint alarm rings for (SPEC.md §3).
                    onSkip = { answer(ofOccurrence = app.routines::skipTogether) },
                    onDrop = { answer(ofOccurrence = app.routines::dropTogether) },
                    onClose = {
                        silence()
                        finish()
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val alarmed = intent.alarmed() ?: return
        queue = listOf(alarmed) + (queue - alarmed)
        showFirst()
        if (intent.rings()) ring()
    }

    override fun onStart() {
        super.onStart()
        leaving?.cancel()
    }

    override fun onResume() {
        super.onResume()
        checkLocked()
    }

    override fun onStop() {
        super.onStop()
        when {
            // Turning the phone only recreates the alarm, which rings on.
            isChangingConfigurations -> Unit
            // Gone back from, or gone because its reminders are.
            isFinishing -> silence()
            // Some phones stop it for a moment while the screen turns on, which must not silence it.
            else -> leaving = lifecycleScope.launch {
                delay(LEAVE_GRACE.toMillis())
                silence()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArray(QUEUE, queue.map { it.key }.toTypedArray())
        outState.putLong(RING_UNTIL, ringUntil)
    }

    override fun onDestroy() {
        unregisterReceiver(userPresent)
        super.onDestroy()
    }

    /** Rings and keeps the screen on [until] then, a time on [now]; not at all once that is past. */
    private fun ring(until: Long = now() + AlarmRinger.LIMIT.toMillis()) {
        ringLimit?.cancel()
        if (until <= now()) return silence()
        ringUntil = until
        val first = queue.firstOrNull()
        ringStart?.cancel()
        ringStart = lifecycleScope.launch { app.ringer.start(first?.let { app.sounds.alarmOf(it) } ?: Sound.PhoneDefault) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ringLimit = lifecycleScope.launch {
            delay(until - now())
            silence()
        }
    }

    private fun silence() {
        ringStart?.cancel()
        ringStart = null
        ringLimit?.cancel()
        ringLimit = null
        ringUntil = 0
        app.ringer.stop()
        // Heard here, the alarm rings no more in the background either.
        AlarmRingService.silence(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun now() = SystemClock.elapsedRealtime()

    /** Shows the first in the [queue] while it is due, then the next; with none left, the alarm closes. */
    private fun showFirst() {
        watching?.cancel()
        showing = null
        val alarmed = queue.firstOrNull() ?: return finish()
        watching = lifecycleScope.launch {
            showingOf(alarmed).collect { due ->
                if (due != null) {
                    showing = due
                } else {
                    // Answered elsewhere, or gone: the alarm has been heard.
                    silence()
                    drop(alarmed)
                }
            }
        }
    }

    /** What the alarm shows of the occurrence or to-do while it is due, as it changes; null once it is not. */
    private fun showingOf(alarmed: Alarmed): Flow<Showing?> = when (alarmed) {
        is Alarmed.Occurrence -> app.routines.reminder(alarmed.id).map { reminder ->
            reminder?.let {
                val dueAt = getString(R.string.reminder_due_at, it.dueAt.clockTime())
                val onLockScreen = LockScreenShown(it.title, dueAt).takeIf { _ -> it.titleOnLockScreen }
                val each = if (it.together.isEmpty()) {
                    emptyList()
                } else {
                    it.occurrences.sortedWith(compareBy(InputText.byName) { joint -> joint.name }).map { joint ->
                        AlarmChoice(joint.occurrenceId, joint.name)
                    }
                }
                val screen = AlarmShown(alarmed.key, it.title, dueAt, skippable = true, onLockScreen, each)
                Showing(alarmed, screen) { app.reminders.putOffChoices(it) }
            }
        }
        is Alarmed.Todo -> app.todos.reminder(alarmed.id).map { reminder ->
            reminder?.let {
                val today = app.todos.today()
                val onLockScreen = LockScreenShown(it.title, resources.lockScreenText(it, today)).takeIf { _ -> it.nameOnLockScreen }
                val screen = AlarmShown(alarmed.key, it.title, resources.reminderText(it, today), skippable = false, onLockScreen)
                // Any of them, as a to-do has no time window.
                Showing(alarmed, screen) { Reminders.PUT_OFF_CHOICES }
            }
        }
    }

    /** Takes it out of the [queue], however often it is answered or gone, and shows what is first then. */
    private fun drop(alarmed: Alarmed) {
        queue = queue - alarmed
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
     * Goes on to the next in the queue and does the work on the one shown, [ofOccurrence] or [ofTodo], to the end even
     * if the alarm closes. In that order, so that the work being done cannot take the next one out of the queue too.
     * Nothing for one without such work, as a to-do is never skipped.
     */
    private fun answer(ofOccurrence: suspend (Long) -> Unit, ofTodo: (suspend (Long) -> Unit)? = null) {
        val alarmed = showing?.alarmed ?: return
        val work = when (alarmed) {
            is Alarmed.Occurrence -> ofOccurrence
            is Alarmed.Todo -> ofTodo ?: return
        }
        silence()
        drop(alarmed)
        app.background.launch { work(alarmed.id) }
    }

    /** What the alarm shows of [alarmed]: its [screen], and what "Später" offers now. */
    private class Showing(val alarmed: Alarmed, val screen: AlarmShown, val putOffChoices: () -> List<Duration>)

    private fun Intent.rings() = getBooleanExtra(EXTRA_RING, true)

    private companion object {
        const val QUEUE = "queue"
        const val RING_UNTIL = "ring-until"

        /** Long enough for a phone to turn its screen on, short enough to silence the alarm with the power button. */
        val LEAVE_GRACE: Duration = Duration.ofSeconds(3)
    }
}

/**
 * Opens the full-screen alarm of the occurrence or to-do: [ring]ing, as when it pops up, or only showing it, as when its
 * reminder is tapped, where it rings on only if it already does.
 */
internal fun alarmIntent(context: Context, alarmed: Alarmed, ring: Boolean): Intent =
    alarmed.putInto(Intent(context, AlarmActivity::class.java))
        // Tells them apart, so that each keeps its own extras.
        .setIdentifier(if (ring) alarmed.key else "${alarmed.key}:open")
        .putExtra(EXTRA_RING, ring)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private const val EXTRA_RING = "ring"
