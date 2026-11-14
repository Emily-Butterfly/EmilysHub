package de.emilyshub.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.emilyshub.MainActivity
import de.emilyshub.R
import de.emilyshub.routines.RoutineRuns
import de.emilyshub.routines.RunView
import de.emilyshub.routines.StepTime
import kotlinx.coroutines.flow.first
import java.time.Clock

/**
 * The notification of a run (SPEC.md §2, Ablauf-Modus), there while a run is running: quiet, with the step's countdown,
 * or its overtime, or a stopwatch without minutes, and "Fertig" and "Beenden"; tapped, it opens the run behind the app's
 * lock. The routine and the step show only where the routine allows its name on the lock screen, else "Ablauf läuft"
 * and the step's number. No service runs for it: the end of a step's time comes by an exact alarm ([alarm]), after which
 * it alerts once, with the standard tone of notifications. All of it follows the run as [runs] keeps it ([update]).
 */
class RunNotification(
    private val context: Context,
    private val runs: RoutineRuns,
    private val clock: Clock,
    private val alarm: ReminderAlarm = ReminderAlarm(context, ReminderReceiver.ACTION_RUN_STEP_END),
) {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    /**
     * Shows the run as it stands, with the alarm set for the end of its step's time, alerting once that is over; or takes
     * both away once no run is running, its routine done or its occurrence ended otherwise. Without notifications
     * allowed, there is nothing to show, and the run goes on in the app alone.
     */
    suspend fun update() {
        val run = runs.current()?.takeIf { it.finishedAt == null }
        val step = run?.let { runs.view(it.occurrenceId, it.routineId).first() } as? RunView.Step
        if (run == null || step == null || !context.notificationsAllowed()) {
            alarm.cancel()
            manager.cancel(TAG, ID)
            return
        }
        val now = clock.instant()
        val end = step.endsAt
        val alert = end != null && !now.isBefore(end) && !run.alerted
        if (end != null && now.isBefore(end) && !run.alerted) alarm.set(end) else alarm.cancel()
        createChannels()
        manager.notify(TAG, ID, notification(run.occurrenceId, run.routineId, step, alert))
        if (alert) runs.alerted(run.occurrenceId, step.item.itemId, step.startedAt)
    }

    private fun notification(occurrenceId: Long, routineId: Long, step: RunView.Step, alert: Boolean): Notification {
        val face = RunFace.of(step)
        val time = step.time(clock.instant())
        return Notification.Builder(context, if (alert) ALERT_CHANNEL else CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(face.title ?: context.getString(R.string.run_notification_title))
            .setContentText(face.text ?: context.getString(R.string.run_step, step.number, step.count))
            .setSubText(if (time is StepTime.Overtime) context.getString(R.string.run_overtime) else null)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            // It says no more than the lock screen may show (RunFace), on the phone unlocked too: a run is short, and
            // one notification for both keeps it from changing as the phone is unlocked.
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .apply {
                when (time) {
                    is StepTime.Countdown -> setWhen(checkNotNull(step.endsAt).toEpochMilli()).setChronometerCountDown(true)
                    is StepTime.Overtime -> setWhen(checkNotNull(step.endsAt).toEpochMilli())
                    is StepTime.Stopwatch -> setWhen(step.runStartedAt.toEpochMilli())
                }
            }
            .setContentIntent(open(occurrenceId, routineId))
            .addAction(action(R.string.run_done, ReminderReceiver.ACTION_RUN_DONE, occurrenceId, step.item.itemId))
            .addAction(action(R.string.run_stop, ReminderReceiver.ACTION_RUN_STOP, occurrenceId, step.item.itemId))
            .build()
    }

    /** The quiet channel it stands in, and the one it alerts from once a step's time is over. */
    private fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.run_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        // With the standard tone of notifications, Android's own when none is set.
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, context.getString(R.string.run_alert_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setShowBadge(false)
            },
        )
    }

    /** To [ReminderReceiver], about the step [itemId] of the occurrence's run; only once the phone is unlocked. */
    private fun action(title: Int, action: String, occurrenceId: Long, itemId: Long): Notification.Action {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(action)
            // Tells the intents of different runs and steps apart, so that each keeps its own extras.
            .setIdentifier("$TAG:$occurrenceId:$itemId")
            .putExtra(EXTRA_OCCURRENCE, occurrenceId)
            .putExtra(EXTRA_ITEM, itemId)
        val operation = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(null, context.getString(title), operation)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAuthenticationRequired(true) }
            .build()
    }

    /** The run, behind the app's lock. */
    private fun open(occurrenceId: Long, routineId: Long): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_OPEN, MainActivity.openRun(context, occurrenceId, routineId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL = "run"
        const val ALERT_CHANNEL = "run-alert"
        const val TAG = "run"
        const val ID = 1

        /** The step an action of the notification is about. */
        const val EXTRA_ITEM = "item"

        /** Not the request code of the reminders' taps (0), nor that of "Gedanke festhalten" (1). */
        private const val REQUEST_OPEN = 2
    }
}

/**
 * What a run's notification names (SPEC.md §2, Ablauf-Modus): the routine as its [title] and the step as its [text],
 * where the routine allows its name on the lock screen; else neither, and it says "Ablauf läuft" and the step's number.
 */
data class RunFace(val title: String?, val text: String?) {
    companion object {
        fun of(step: RunView.Step): RunFace =
            if (step.nameOnLockScreen) RunFace(step.name, step.item.text) else RunFace(title = null, text = null)
    }
}
