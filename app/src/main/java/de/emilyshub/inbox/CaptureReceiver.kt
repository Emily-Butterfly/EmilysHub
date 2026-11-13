package de.emilyshub.inbox

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.emilyshub.EmilysHubApplication
import de.emilyshub.reminders.finishInBackground

/**
 * The text of the field of "Gedanke festhalten" ([CaptureNotification]), put in the inbox as one typed in the app is,
 * with the same checks; then the notification stands there empty again. Not exported: anything but the action below
 * with the field's text is ignored, and nothing else is taken from an intent (SPEC.md §9).
 */
class CaptureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CAPTURE) return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(CaptureNotification.KEY_TEXT)?.toString() ?: return
        val app = context.applicationContext as EmilysHubApplication
        finishInBackground(app) {
            try {
                app.inbox.capture(text)
            } finally {
                // Shown anew, so that it neither waits on the answer nor shows what was written.
                app.updateCaptureNotification()
            }
        }
    }

    companion object {
        const val ACTION_CAPTURE = "de.emilyshub.action.CAPTURE"
    }
}
