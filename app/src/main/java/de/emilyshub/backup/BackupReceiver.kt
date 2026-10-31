package de.emilyshub.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.emilyshub.EmilysHubApplication
import de.emilyshub.reminders.finishInBackground

/** The night's backup is due. Not exported, and anything but [ACTION_BACKUP] is ignored (SPEC.md §9). */
class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BACKUP) return
        val app = context.applicationContext as EmilysHubApplication
        finishInBackground(app) { app.nightlyBackups.backUp() }
    }

    companion object {
        const val ACTION_BACKUP = "de.emilyshub.action.BACKUP"
    }
}
