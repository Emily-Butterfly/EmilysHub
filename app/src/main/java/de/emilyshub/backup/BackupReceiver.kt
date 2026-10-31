package de.emilyshub.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The night's backup is due: handed to [BackupJob]. Not exported, and anything but [ACTION_BACKUP] is ignored (SPEC.md §9). */
class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BACKUP) return
        BackupJob.start(context)
    }

    companion object {
        const val ACTION_BACKUP = "de.emilyshub.action.BACKUP"
    }
}
