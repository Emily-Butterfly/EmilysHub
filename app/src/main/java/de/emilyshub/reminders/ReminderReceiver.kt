package de.emilyshub.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.emilyshub.EmilysHubApplication
import java.time.Instant

/**
 * The reminder alarm and the actions on a reminder. Not exported, so only the app and the system can
 * send here, and anything without one of the actions below is ignored (SPEC.md §9).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as EmilysHubApplication
        val work: suspend () -> Unit = when (intent.action) {
            ACTION_REMIND -> {
                val at = intent.getLongExtra(EXTRA_AT, -1).takeIf { it >= 0 } ?: return
                { app.reminders.remind(Instant.ofEpochMilli(at)) }
            }
            ACTION_DONE, ACTION_PUT_OFF, ACTION_SKIP -> {
                val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE, -1).takeIf { it > 0 } ?: return
                when (intent.action) {
                    ACTION_DONE -> suspend { app.routines.markDone(occurrence) }
                    ACTION_SKIP -> suspend { app.routines.skip(occurrence) }
                    else -> suspend { app.reminders.putOff(occurrence) }
                }
            }
            else -> return
        }
        app.finishInBackground(goAsync(), work)
    }

    companion object {
        const val ACTION_REMIND = "de.emilyshub.action.REMIND"
        const val ACTION_DONE = "de.emilyshub.action.DONE"
        const val ACTION_PUT_OFF = "de.emilyshub.action.PUT_OFF"
        const val ACTION_SKIP = "de.emilyshub.action.SKIP"

        /** When the alarm was set for, in milliseconds since 1970. */
        const val EXTRA_AT = "at"
        const val EXTRA_OCCURRENCE = "occurrence"
    }
}
