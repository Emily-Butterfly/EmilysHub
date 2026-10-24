package de.emilyshub.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.emilyshub.EmilysHubApplication
import kotlinx.coroutines.launch

/**
 * The reminder alarm and the actions on a reminder, of a routine's occurrence or of a to-do. Not exported, so only the
 * app and the system can send here, and anything without one of the actions below, without an occurrence or a to-do
 * for them, or with a time to put off by that "Später" does not offer, is ignored (SPEC.md §9).
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as EmilysHubApplication
        val occurrence = intent.occurrenceId()
        val todo = intent.todoId()
        val minutes = intent.getLongExtra(EXTRA_MINUTES, 0)
        val putOffBy = Reminders.PUT_OFF_CHOICES.firstOrNull { it.toMinutes() == minutes }

        /** The work on the occurrence's reminder, or else on the to-do's; none without either. */
        fun onEither(ofOccurrence: suspend (Long) -> Unit, ofTodo: suspend (Long) -> Unit): (suspend () -> Unit)? =
            occurrence?.let { suspend { ofOccurrence(it) } } ?: todo?.let { suspend { ofTodo(it) } }

        val work: (suspend () -> Unit)? = when (intent.action) {
            ACTION_REMIND -> suspend { app.reminders.remind() }
            ACTION_DONE -> onEither(app.routines::markDone, app.todos::markDone)
            ACTION_OFFER_PUT_OFF -> onEither(app.reminders::offerPutOff, app.reminders::offerTodoPutOff)
            ACTION_PUT_OFF -> putOffBy?.let { by -> onEither({ app.reminders.putOff(it, by) }, { app.reminders.putOffTodo(it, by) }) }
            ACTION_SKIP -> occurrence?.let { suspend { app.routines.skip(it) } }
            ACTION_SILENCE -> suspend { AlarmRingService.silence(context) }
            ACTION_ARRIVE -> suspend { app.reminders.arrived() }
            ACTION_PROBE -> suspend { app.reminders.showProbe() }
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

        /** "Stumm" while an alarm rings: it stops ringing, its reminders stay. */
        const val ACTION_SILENCE = "de.emilyshub.action.SILENCE"

        /** The device joined a WLAN, as Android tells for [ArrivalWatch]. */
        const val ACTION_ARRIVE = "de.emilyshub.action.ARRIVE"

        /** The reminder to try out is due. */
        const val ACTION_PROBE = "de.emilyshub.action.PROBE"
        const val EXTRA_MINUTES = "minutes"
    }
}

/** The occurrence that the intents of a reminder, its actions and its alarm, are about. */
const val EXTRA_OCCURRENCE = "occurrence"

/** The occurrence an intent is about; none without a valid one. */
internal fun Intent.occurrenceId(): Long? = getLongExtra(EXTRA_OCCURRENCE, 0).takeIf { it > 0 }

/** The to-do that the actions of its reminder are about. */
const val EXTRA_TODO = "todo"

/** The to-do an intent is about; none without a valid one. */
internal fun Intent.todoId(): Long? = getLongExtra(EXTRA_TODO, 0).takeIf { it > 0 }

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
