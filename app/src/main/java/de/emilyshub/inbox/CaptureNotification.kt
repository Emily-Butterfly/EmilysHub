package de.emilyshub.inbox

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import de.emilyshub.MainActivity
import de.emilyshub.R
import de.emilyshub.reminders.notificationsAllowed

/**
 * "Gedanke festhalten" in the notifications (SPEC.md §16): a quiet notification that stays, with "Schreiben" and a
 * field to answer in, whose text goes into the inbox ([CaptureReceiver]). It only writes: it never shows anything of
 * the app, neither a text nor how many there are, so all in the inbox stays behind the app's lock. On the lock screen
 * Android asks for the phone to be unlocked before the field takes a text; the app's lock is not asked.
 */
class CaptureNotification(private val context: Context) {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    /** Shows it while [enabled] and notifications are allowed; otherwise it and its channel are gone. */
    fun update(enabled: Boolean) {
        if (enabled && context.notificationsAllowed()) show() else remove()
    }

    /** Shown anew, empty, with nothing of what was written. */
    fun show() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.capture_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
        )
        val write = Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_notification),
            context.getString(R.string.capture_write),
            replyIntent(),
        )
            .addRemoteInput(RemoteInput.Builder(KEY_TEXT).setLabel(context.getString(R.string.inbox_capture)).build())
            .setAllowGeneratedReplies(false)
            .build()
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.capture_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // There is nothing to hide: it says no more than its title.
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openCapture())
            .addAction(write)
            .build()
        manager.notify(TAG, ID, notification)
    }

    /** It and its channel are gone. */
    fun remove() {
        manager.cancel(TAG, ID)
        manager.deleteNotificationChannel(CHANNEL)
    }

    /**
     * Where the field sends its text. The one mutable PendingIntent of the app, the exception SPEC.md §9 makes for it:
     * Android fills in the text typed, which an immutable one cannot take. It stays explicit, to the app's own receiver,
     * which is not exported.
     */
    private fun replyIntent(): PendingIntent {
        val intent = Intent(context, CaptureReceiver::class.java).setAction(CaptureReceiver.ACTION_CAPTURE)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** The app, behind its lock, with the line for a thought (#94) over what was open. */
    private fun openCapture(): PendingIntent =
        PendingIntent.getActivity(context, REQUEST_OPEN, MainActivity.capture(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val CHANNEL = "capture"
        const val TAG = "capture"
        const val ID = 1

        /** Under what the field gives its text. */
        const val KEY_TEXT = "text"

        /** Not the request code of the reply, so that both stay. */
        private const val REQUEST_OPEN = 1
    }
}
