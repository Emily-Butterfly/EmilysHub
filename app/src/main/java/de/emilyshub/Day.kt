package de.emilyshub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The time of day for all that shows it: the time now and the day it is, in the time zone the phone is in now, by
 * [clock]. As flows, anew each minute and at once when the phone's time or time zone is changed, by hand or by the
 * network ([clockChanges]); a day changes at midnight in that zone (SPEC.md §2), so the day turns with the minute.
 * The modules keep their own [Clock] for what they write; this one is for what is shown.
 */
class Day(private val clock: Clock, private val clockChanges: Flow<Unit> = emptyFlow()) {

    fun now(): Instant = clock.instant()

    fun zone(): ZoneId = clock.zone

    /** The local date now. */
    fun today(): LocalDate = dayOf(now())

    /** The local date at [moment], in the time zone the phone is in now. */
    fun dayOf(moment: Instant): LocalDate = moment.atZone(clock.zone).toLocalDate() // LocalDate.ofInstant needs API 34

    /**
     * The time now, at once, then just after each full minute, and at once after the clock changed: what changes as
     * time goes by, such as a to-do turning overdue. The waits stand still while the phone sleeps, so a screen
     * collects it only while it shows, and so starts anew on return.
     */
    fun minutes(): Flow<Instant> = channelFlow {
        val changed = Channel<Unit>(Channel.CONFLATED)
        launch { clockChanges.collect { changed.send(Unit) } }
        while (true) {
            val now = now()
            send(now)
            // Measured anew each time, so that after a change it is on the new clock.
            val untilNextMinute = MINUTE_MILLIS - Math.floorMod(now.toEpochMilli(), MINUTE_MILLIS) + MARGIN_MILLIS
            withTimeoutOrNull(untilNextMinute) { changed.receive() }
        }
    }

    /** The day it is, at once, then each time it changes: at midnight, or as the clock or time zone changes. */
    fun dates(): Flow<LocalDate> = minutes().map(::dayOf).distinctUntilChanged()

    private companion object {
        const val MINUTE_MILLIS = 60_000L

        /** The wait may end a little early, so it waits a little longer to be sure the minute has turned. */
        const val MARGIN_MILLIS = 1_000L
    }
}

/**
 * Each time the phone's time, time zone or date is changed by hand or by the network, after a flight for example, for
 * as long as it is collected.
 */
fun clockChanges(context: Context): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(Unit)
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_DATE_CHANGED)
    }
    // Not exported: the system still delivers its own broadcasts, other apps cannot send any (SPEC.md §9).
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    awaitClose { context.unregisterReceiver(receiver) }
}
