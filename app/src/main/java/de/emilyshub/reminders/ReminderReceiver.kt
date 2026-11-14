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
        val work: (suspend () -> Unit)? = when (intent.action) {
            // Marking missed may end the occurrence a run is running through.
            ACTION_REMIND -> suspend {
                try {
                    app.reminders.remind()
                } finally {
                    app.updateRun()
                }
            }
            ACTION_RUN_STEP_END -> suspend { app.updateRun() }
            ACTION_RUN_DONE -> intent.runStep()?.let { (occurrence, item) -> suspend { app.runs.done(occurrence, item) } }
            ACTION_RUN_STOP -> suspend { app.runs.stop() }
            ACTION_SILENCE -> suspend { AlarmRingService.silence(context) }
            ACTION_ARRIVE -> suspend { app.reminders.arrived() }
            ACTION_LOOK_AROUND -> suspend { app.reminders.lookAround() }
            ACTION_PROBE -> suspend { app.reminders.showProbe() }
            else -> intent.answer()?.let { (about, answer) -> suspend { app.reminders.answer(about, answer) } }
        }
        if (work != null) finishInBackground(app, work)
    }

    companion object {
        const val ACTION_REMIND = "de.emilyshub.action.REMIND"
        const val ACTION_DONE = "de.emilyshub.action.DONE"

        /** "Erledigt" on a joint reminder: asks which one it was. */
        const val ACTION_OFFER_DONE = "de.emilyshub.action.OFFER_DONE"

        /** "Später": asks when to come back. */
        const val ACTION_OFFER_PUT_OFF = "de.emilyshub.action.OFFER_PUT_OFF"

        /** One of the choices "Später" offers, by its key in [EXTRA_PUT_OFF]. */
        const val ACTION_PUT_OFF = "de.emilyshub.action.PUT_OFF"
        const val ACTION_SKIP = "de.emilyshub.action.SKIP"

        /** "Heute nicht": the routine is dropped for the rest of the day. */
        const val ACTION_DROP = "de.emilyshub.action.DROP"

        /** "Läuft weiter" on an Abo's reminder (#80). */
        const val ACTION_RUN_ON = "de.emilyshub.action.RUN_ON"

        /** "Stumm" while an alarm rings: it stops ringing, its reminders stay. */
        const val ACTION_SILENCE = "de.emilyshub.action.SILENCE"

        /** The device joined a WLAN, as Android tells for [ArrivalWatch]. */
        const val ACTION_ARRIVE = "de.emilyshub.action.ARRIVE"

        /** The app is to look around, as a to-do waits for a place. */
        const val ACTION_LOOK_AROUND = "de.emilyshub.action.LOOK_AROUND"

        /** The reminder to try out is due. */
        const val ACTION_PROBE = "de.emilyshub.action.PROBE"

        /** The time of a run's step is over (SPEC.md §2, Ablauf-Modus). */
        const val ACTION_RUN_STEP_END = "de.emilyshub.action.RUN_STEP_END"

        /** "Fertig" in a run's notification: the step it shows is ticked off. */
        const val ACTION_RUN_DONE = "de.emilyshub.action.RUN_DONE"

        /** "Beenden" in a run's notification: the run ends, its ticks staying as they are. */
        const val ACTION_RUN_STOP = "de.emilyshub.action.RUN_STOP"

        /** The key of a choice "Später" offers ([PutOffChoice.key]). */
        const val EXTRA_PUT_OFF = "put-off"
    }
}

/**
 * The answer to a reminder an action of its notification gives, and what it is about, as its full-screen alarm gives
 * them too; none without one of those actions, without what it is about, or with a time to put off by that "Später"
 * does not offer.
 */
private fun Intent.answer(): Pair<Alarmed, Answer>? {
    val about = alarmed() ?: return null
    val answer = when (action) {
        ReminderReceiver.ACTION_DONE -> Answer.Done
        ReminderReceiver.ACTION_OFFER_PUT_OFF -> Answer.OfferPutOff
        ReminderReceiver.ACTION_OFFER_DONE -> Answer.OfferDone
        ReminderReceiver.ACTION_PUT_OFF -> getStringExtra(ReminderReceiver.EXTRA_PUT_OFF)?.let(PutOffChoice::of)?.let(Answer::PutOff)
        ReminderReceiver.ACTION_SKIP -> Answer.Skip
        ReminderReceiver.ACTION_DROP -> Answer.Drop
        ReminderReceiver.ACTION_RUN_ON -> Answer.RunOn
        else -> null
    }
    return answer?.let { about to it }
}

/** The occurrence and the step of its run that an action of a run's notification is about; none without valid ones. */
private fun Intent.runStep(): Pair<Long, Long>? {
    val occurrence = occurrenceId() ?: return null
    val item = getLongExtra(RunNotification.EXTRA_ITEM, 0).takeIf { it > 0 } ?: return null
    return occurrence to item
}

/** The occurrence that the intents of a reminder, its actions and its alarm, are about. */
const val EXTRA_OCCURRENCE = "occurrence"

/** The occurrence an intent is about; none without a valid one. */
internal fun Intent.occurrenceId(): Long? = getLongExtra(EXTRA_OCCURRENCE, 0).takeIf { it > 0 }

/** The to-do that the actions of its reminder are about. */
const val EXTRA_TODO = "todo"

/** The to-do an intent is about; none without a valid one. */
internal fun Intent.todoId(): Long? = getLongExtra(EXTRA_TODO, 0).takeIf { it > 0 }

/** The Abo that the intents of its reminder are about (#80). */
const val EXTRA_SUBSCRIPTION = "subscription"

/** The Abo an intent is about; none without a valid one. */
internal fun Intent.subscriptionId(): Long? = getLongExtra(EXTRA_SUBSCRIPTION, 0).takeIf { it > 0 }

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
