package de.emilyshub.backup

import android.util.Log
import de.emilyshub.data.Setting
import de.emilyshub.data.SettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/** A folder the user chose in Android's dialog, by the Storage Access Framework, to keep the nightly backups in. */
interface BackupFolder {
    /** As Android shows it, if it tells. */
    val name: String?

    /** Keeps the access to the folder for good, also after a restart. */
    fun keepAccess()

    /** Gives the access back; nothing if it is gone already. */
    fun releaseAccess()

    fun fileNames(): List<String>

    /** The file [fileName] in the folder to write, made anew or overwritten. */
    fun write(fileName: String): OutputStream

    fun delete(fileName: String)
}

/** Why Heute tells of the backups: the last one failed, is over 30 days old, or there has been none for 30 days. */
enum class BackupWarning { Failed, Old, None }

/**
 * How the nightly backups stand: the [folderName] chosen, none while they are off; when the last backup was made, by
 * night or by hand; and whether the last nightly one [failed] after that.
 */
data class NightlyState(val folderName: String?, val lastMade: ZonedDateTime?, val failed: Boolean)

/**
 * The backups made each night (SPEC.md §10) into a folder chosen once, whose access is kept: at [NIGHT], with the key
 * [backups] keep of the backup password, so without asking for it. The last [KEPT] backups stay, older ones are deleted,
 * other files in the folder are left alone. A night missed, while the phone was off, is made up as soon as the app
 * may again: the next backup is due the first night after the last one tried. [folderAt] gives the folder of the
 * address stored, and [alarm] wakes the app for the next backup. [settings] is only called on [io].
 *
 * Heute is told ([warning]) when the last backup, made by night or by hand, is over [WARN_AFTER_DAYS] days old, or
 * failed; and when there has been none at all since the app first looked, as long.
 */
class NightlyBackups(
    private val backups: Backups,
    private val settings: () -> SettingsDao,
    private val clock: Clock,
    private val folderAt: (String) -> BackupFolder,
    private val alarm: NightAlarm,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    // Keeps a night's backup and choosing or giving up the folder from crossing.
    private val lock = Mutex()

    /**
     * Makes a backup in the folder at [address] right away, and if that works, makes it the one for the nightly
     * backups in place of any before, keeping its access: whether it worked, so that the user knows the folder will
     * do. One that does not is not taken, and the one before stays.
     */
    suspend fun choose(address: String): Boolean = withContext(io + NonCancellable) {
        lock.withLock {
            val folder = folderAt(address)
            val before = stored(FOLDER)
            val made = try {
                folder.keepAccess()
                export(folder)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "No backup could be made in the folder chosen: ${e.javaClass.name}")
                false
            }
            if (!made) {
                if (address != before) folder.releaseAccess()
                return@withLock false
            }
            before?.takeIf { it != address }?.let { folderAt(it).releaseAccess() }
            put(FOLDER, address)
            put(FOLDER_NAME, folder.name.orEmpty())
            put(LAST_TRIED, clock.instant().toString())
            put(FAILED, "")
            setAlarm()
            true
        }
    }

    /** No more nightly backups; the folder's access is given back, the backups in it stay. */
    suspend fun stop() = withContext(io + NonCancellable) {
        lock.withLock {
            stored(FOLDER)?.let { folderAt(it).releaseAccess() }
            put(FOLDER, "")
            put(FOLDER_NAME, "")
            // Of a folder no longer used.
            put(FAILED, "")
            alarm.cancel()
        }
    }

    /** The alarm went off: the night's backup, then the next night's alarm. */
    suspend fun backUp() = withContext(io + NonCancellable) {
        lock.withLock {
            val address = stored(FOLDER) ?: return@withLock alarm.cancel()
            backUp(folderAt(address))
        }
    }

    /** Sets the alarm for the next backup anew, as after a restart or a change of clock; and notes the app is in use. */
    suspend fun schedule() = withContext(io) {
        lock.withLock {
            if (stored(WATCHED_SINCE) == null) put(WATCHED_SINCE, clock.instant().toString())
            setAlarm()
        }
    }

    suspend fun state(): NightlyState = withContext(io) {
        val lastMade = backups.lastMade()
        val failed = stored(FAILED)?.let(Instant::parse)
        NightlyState(
            folderName = stored(FOLDER)?.let { stored(FOLDER_NAME).orEmpty() },
            lastMade = lastMade?.atZone(clock.zone),
            // Until a backup is made after it, by night or by hand.
            failed = failed != null && (lastMade == null || !failed.isBefore(lastMade)),
        )
    }

    /** What Heute is to tell of the backups, if anything. */
    suspend fun warning(): BackupWarning? {
        val state = state()
        val since = state.lastMade ?: withContext(io) { stored(WATCHED_SINCE) }?.let { Instant.parse(it).atZone(clock.zone) }
        val outdated = since != null && clock.instant().isAfter(since.plusDays(WARN_AFTER_DAYS).toInstant())
        return when {
            state.failed -> BackupWarning.Failed
            outdated && state.lastMade == null -> BackupWarning.None
            outdated -> BackupWarning.Old
            else -> null
        }
    }

    /** A backup into [folder] now, the older ones deleted, and the alarm for the next; whether it was made. */
    private suspend fun backUp(folder: BackupFolder): Boolean {
        val now = clock.instant()
        put(LAST_TRIED, now.toString())
        val made = try {
            export(folder)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not its message, which may quote the data.
            Log.e(TAG, "The nightly backup failed: ${e.javaClass.name}")
            put(FAILED, now.toString())
            false
        }
        if (made) put(FAILED, "")
        setAlarm()
        return made
    }

    /** A backup into [folder], under the name of the day, older ones beyond the last [KEPT] deleted. */
    private suspend fun export(folder: BackupFolder) {
        backups.export { folder.write(backups.fileName()) }
        deleteOlder(folder)
    }

    /** The backups beyond the last [KEPT], by the date in their names; the backup made stands if this fails. */
    private fun deleteOlder(folder: BackupFolder) {
        try {
            folder.fileNames().filter(BACKUP_NAME::matches).sortedDescending().drop(KEPT).forEach(folder::delete)
        } catch (e: Exception) {
            Log.e(TAG, "Older backups could not be deleted: ${e.javaClass.name}")
        }
    }

    /**
     * The alarm for the first night after the last backup tried, or right away if that has passed; none while off. A
     * backup tried "later" than now, after the clock was put back, counts as tried now.
     */
    private suspend fun setAlarm() {
        if (stored(FOLDER) == null) return alarm.cancel()
        val now = clock.instant()
        val tried = stored(LAST_TRIED)?.let(Instant::parse)?.takeIf { !it.isAfter(now) } ?: now
        val local = tried.atZone(clock.zone)
        val tonight = local.toLocalDate().atTime(NIGHT).atZone(clock.zone)
        val next = if (tonight.isAfter(local)) tonight else local.toLocalDate().plusDays(1).atTime(NIGHT).atZone(clock.zone)
        alarm.set(maxOf(next.toInstant(), now))
    }

    private suspend fun stored(name: String): String? = settings().get(name)?.takeIf { it.isNotEmpty() }

    private suspend fun put(name: String, value: String) = settings().put(Setting(name, value))

    companion object {
        val NIGHT: LocalTime = LocalTime.of(3, 0)
        const val KEPT = 7
        const val WARN_AFTER_DAYS = 30L

        /** The names [Backups.fileName] gives, and only those: no other file is deleted. */
        private val BACKUP_NAME = Regex("""EmilysHub-Backup-\d{4}-\d{2}-\d{2}\.emhb""")
        private const val FOLDER = "backup-folder"
        private const val FOLDER_NAME = "backup-folder-name"
        private const val LAST_TRIED = "backup-night-tried"
        private const val FAILED = "backup-night-failed"
        private const val WATCHED_SINCE = "backups-watched-since"
        private const val TAG = "EmilysHub"
    }
}
