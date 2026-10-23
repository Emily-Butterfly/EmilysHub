package de.emilyshub.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.emilyshub.EmilysHubApplication
import kotlinx.coroutines.launch

/**
 * The reminder alarm and the actions on a reminder. Not exported, so only the app and the system can
 * send here, and anything without one of the actions below, without an occurrence for them, or with a
 * time to put off by that "Später" does not offer, is ignored (SPEC.md §9).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as EmilysHubApplication
        val occurrence = intent.occurrenceId()
        val minutes = intent.getLongExtra(EXTRA_MINUTES, 0)
        val putOffBy = Reminders.PUT_OFF_CHOICES.firstOrNull { it.toMinutes() == minutes }
        val work: (suspend () -> Unit)? = when (intent.action) {
            ACTION_REMIND -> suspend { app.reminders.remind() }
            ACTION_DONE -> occurrence?.let { suspend { app.routines.markDone(it) } }
            ACTION_OFFER_PUT_OFF -> occurrence?.let { suspend { app.reminders.offerPutOff(it) } }
            ACTION_PUT_OFF -> putOffBy?.let { by -> occurrence?.let { suspend { app.reminders.putOff(it, by) } } }
            ACTION_SKIP -> occurrence?.let { suspend { app.routines.skip(it) } }
            else -> null
        }
        if (work != null) finishInBackground(app, work)
    }

    companion object {
        const val ACTION_REMIND = "de.emilyshub.action.REMIND"
        const val ACTION_DONE = "de.emilyshub.action.DONE"

        /** "Später": asks when to come back. */
        const val ACTION_OFFER_PUT_OFF = "de.emilyshub.action.OFFER_PUT_OFF"

        /** One of the choices "Später" offers, in [EXTRA_MINUTES]. */
        const val ACTION_PUT_OFF = "de.emilyshub.action.PUT_OFF"
        const val ACTION_SKIP = "de.emilyshub.action.SKIP"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** The occurrence that the intents of a reminder, its actions and its alarm, are about. */
const val EXTRA_OCCURRENCE = "occurrence"

/** The occurrence an intent is about; none without a valid one. */
internal fun Intent.occurrenceId(): Long? = getLongExtra(EXTRA_OCCURRENCE, 0).takeIf { it > 0 }

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
