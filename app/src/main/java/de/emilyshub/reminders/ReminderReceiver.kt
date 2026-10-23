package de.emilyshub.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.emilyshub.EmilysHubApplication
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * The reminder alarm and the actions on a reminder. Not exported, so only the app and the system can
 * send here, and anything without one of the actions below, without an occurrence for them, or with a
 * time to put off by that "Später" does not offer, is ignored (SPEC.md §9).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as EmilysHubApplication
        val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE, 0).takeIf { it > 0 }
        val putOffBy = Duration.ofMinutes(intent.getLongExtra(EXTRA_MINUTES, 0)).takeIf { it in Reminders.PUT_OFF_CHOICES }
        val work: (suspend () -> Unit)? = when (intent.action) {
            ACTION_REMIND -> suspend { app.reminders.remind() }
            ACTION_DONE -> occurrence?.let { suspend { app.routines.markDone(it) } }
            ACTION_LATER -> occurrence?.let { suspend { app.reminders.offerPutOff(it) } }
            ACTION_PUT_OFF -> if (occurrence != null && putOffBy != null) suspend { app.reminders.putOff(occurrence, putOffBy) } else null
            ACTION_SKIP -> occurrence?.let { suspend { app.routines.skip(it) } }
            else -> null
        }
        if (work != null) finishInBackground(app, work)
    }

    companion object {
        const val ACTION_REMIND = "de.emilyshub.action.REMIND"
        const val ACTION_DONE = "de.emilyshub.action.DONE"

        /** "Später": asks when to come back. */
        const val ACTION_LATER = "de.emilyshub.action.LATER"

        /** One of the choices "Später" offers, in [EXTRA_MINUTES]. */
        const val ACTION_PUT_OFF = "de.emilyshub.action.PUT_OFF"
        const val ACTION_SKIP = "de.emilyshub.action.SKIP"
        const val EXTRA_OCCURRENCE = "occurrence"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** Does [work] after onReceive has returned, in the app's background scope, and tells Android once it is done. */
internal fun BroadcastReceiver.finishInBackground(app: EmilysHubApplication, work: suspend () -> Unit) {
    val pending = goAsync()
    app.background.launch {
        try {
            work()
        } finally {
            pending.finish()
        }
    }
}
