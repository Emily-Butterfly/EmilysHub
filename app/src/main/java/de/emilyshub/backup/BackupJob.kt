package de.emilyshub.backup

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import de.emilyshub.EmilysHubApplication
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Makes the night's backup, once [BackupReceiver] has handed it over: as a job, Android gives it minutes, where a
 * receiver has seconds, enough for a folder that is slow to write, such as one of a cloud storage. Not exported (SPEC.md
 * §9): only Android binds it, for the job the app scheduled.
 */
class BackupJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as EmilysHubApplication
        app.background.launch {
            try {
                app.nightlyBackups.backUp()
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    /** Not to be tried again: the next night's alarm is set already, and a backup cut off is taken away. */
    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val ID = 28

        /** Starts the job, as soon as Android lets it; one going on is left alone. */
        fun start(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(ID) != null) return
            val job = JobInfo.Builder(ID, ComponentName(context, BackupJob::class.java))
                // A deadline, as a job needs some condition, and no other: it may start at once.
                .setOverrideDeadline(START_WITHIN.toMillis())
                .build()
            scheduler.schedule(job)
        }

        private val START_WITHIN: Duration = Duration.ofMinutes(15)
    }
}
