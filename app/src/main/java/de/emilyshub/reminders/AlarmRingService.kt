package de.emilyshub.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.emilyshub.EmilysHubApplication
import de.emilyshub.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Rings like an alarm clock for a full-screen alarm as it pops up (SPEC.md §3), also where Android shows only its
 * reminder instead, as it does while the phone or tablet is in use. A foreground service, so that Android lets it
 * ring on in the background, with the same [AlarmRinger] as the alarm itself. It rings for at most [AlarmRinger.LIMIT],
 * until every reminder it rings for is answered or gone, "Stumm" is tapped, or the alarm is silenced ([silence]).
 */
class AlarmRingService : Service() {
    private val app get() = application as EmilysHubApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The occurrences it rings for, each watched until its reminder is answered or gone. */
    private val ringingFor = mutableMapOf<Long, Job>()
    private var limit: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val occurrenceId = intent?.occurrenceId()
        // First of all, as Android expects of a service started in the foreground.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(occurrenceId), type)
        if (occurrenceId == null) {
            if (ringingFor.isEmpty()) stop()
            return START_NOT_STICKY
        }
        app.ringer.start()
        if (occurrenceId !in ringingFor) {
            ringingFor[occurrenceId] = scope.launch {
                app.routines.reminder(occurrenceId).first { it == null }
                ringingFor.remove(occurrenceId)
                if (ringingFor.isEmpty()) stop()
            }
        }
        // A newer alarm rings for as long again.
        limit?.cancel()
        limit = scope.launch {
            delay(AlarmRinger.LIMIT.toMillis())
            stop()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        app.ringer.stop()
        super.onDestroy()
    }

    private fun stop() {
        app.ringer.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Says that an alarm rings, without what for, as it shows on the lock screen too; tapped, it opens the alarm. */
    private fun notification(occurrenceId: Long?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.alarm_ringing_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val silence = Intent(this, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_SILENCE)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.alarm_ringing))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .apply {
                if (occurrenceId != null) {
                    val open = alarmIntent(this@AlarmRingService, occurrenceId, ring = false)
                    setContentIntent(PendingIntent.getActivity(this@AlarmRingService, 0, open, flags))
                }
            }
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.alarm_silence),
                    PendingIntent.getBroadcast(this, 0, silence, flags),
                ).build(),
            )
            .build()
    }

    companion object {
        private const val CHANNEL = "alarm-ringing"
        private const val NOTIFICATION_ID = 2
        private const val TAG = "EmilysHub"

        /** Rings for the full-screen alarm of the occurrence, as it pops up; Android allows that only then. */
        fun ring(context: Context, occurrenceId: Long) {
            val intent = Intent(context, AlarmRingService::class.java).putExtra(EXTRA_OCCURRENCE, occurrenceId)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: IllegalStateException) {
                // Not allowed from the background just now: the full-screen alarm still rings while it is up.
                Log.w(TAG, "Could not ring in the background", e)
            }
        }

        /** Stops the ringing, whatever it rang for; the reminders stay. */
        fun silence(context: Context) {
            context.stopService(Intent(context, AlarmRingService::class.java))
        }
    }
}
