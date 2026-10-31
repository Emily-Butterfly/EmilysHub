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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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

/** The folder the nightly backups go to, by the [name] Android shows for it, if it tells one. */
data class ChosenFolder(val name: String?)

/**
 * How the nightly backups stand: the [folder] chosen, none while they are off; when the last backup was made, by night
 * or by hand; and whether the last nightly one [failed] after that.
 */
data class NightlyState(val folder: ChosenFolder?, val lastMade: ZonedDateTime?, val failed: Boolean)

/**
 * The backups made each night (SPEC.md §10) into a folder chosen once, whose access is kept: at [NIGHT], with the key
 * [backups] keep of the backup password, so without asking for it. Each is saved under the day and time it was made,
 * beside the others, never over one; one broken off while it is written is taken away again. The last [KEPT] backups
 * stay, older ones are deleted, other files in the folder are left alone.
 *
 * The next backup is due the first night after the last one tried. One night missed, as the phone was off, it comes the
 * night after; more than one in a row, it is made up as soon as the app may again. [folderAt] gives the folder of the
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

    /** The night's backup is due: it is made, then the next night's alarm set. */
    suspend fun backUp() = withContext(io + NonCancellable) {
        lock.withLock {
            val address = stored(FOLDER) ?: return@withLock alarm.cancel()
            val now = clock.instant()
            put(LAST_TRIED, now.toString())
            try {
                export(folderAt(address))
                put(FAILED, "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not its message, which may quote the data.
                Log.e(TAG, "The nightly backup failed: ${e.javaClass.name}")
                put(FAILED, now.toString())
            }
            setAlarm()
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
            folder = stored(FOLDER)?.let { ChosenFolder(stored(FOLDER_NAME)) },
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

    /**
     * A backup into [folder] under the day and time now, older ones beyond the last [KEPT] deleted. One broken off while
     * it is written is taken away again, so that it neither passes for a backup nor pushes a good one out.
     */
    private suspend fun export(folder: BackupFolder) {
        val fileName = "$PREFIX${LocalDateTime.now(clock).format(STAMP)}$SUFFIX"
        try {
            backups.export { folder.write(fileName) }
        } catch (e: Exception) {
            try {
                if (fileName in folder.fileNames()) folder.delete(fileName)
            } catch (gone: Exception) {
                e.addSuppressed(gone)
            }
            throw e
        }
        deleteOlder(folder)
    }

    /**
     * The backups beyond the last [KEPT], by the day and time in their names, those saved by hand with the day alone
     * too; one dated later than now, made while the clock was wrong, goes first. The backup made stands if this fails.
     */
    private fun deleteOlder(folder: BackupFolder) {
        try {
            val now = LocalDateTime.now(clock)
            folder.fileNames()
                .mapNotNull { name -> madeAt(name)?.let { name to it } }
                .sortedWith(compareBy<Pair<String, LocalDateTime>> { (_, at) -> !at.isAfter(now) }.thenBy { (_, at) -> at })
                .reversed()
                .drop(KEPT)
                .forEach { (name, _) -> folder.delete(name) }
        } catch (e: Exception) {
            Log.e(TAG, "Older backups could not be deleted: ${e.javaClass.name}")
        }
    }

    /**
     * The alarm for the first night after the last backup tried; if that has passed, the night after, or right away
     * once that has passed too. None while off. A backup tried "later" than now, after the clock was put back, counts as
     * tried now.
     */
    private suspend fun setAlarm() {
        if (stored(FOLDER) == null) return alarm.cancel()
        val now = clock.instant()
        val tried = stored(LAST_TRIED)?.let(Instant::parse)?.takeIf { !it.isAfter(now) } ?: now
        val first = nightAfter(tried)
        val second = nightAfter(first)
        alarm.set(
            when {
                first.isAfter(now) -> first
                second.isAfter(now) -> second
                else -> now
            },
        )
    }

    /** The first [NIGHT] after [instant], in the time zone here. */
    private fun nightAfter(instant: Instant): Instant {
        val local = instant.atZone(clock.zone)
        val that = local.toLocalDate().atTime(NIGHT).atZone(clock.zone)
        return (if (that.isAfter(local)) that else local.toLocalDate().plusDays(1).atTime(NIGHT).atZone(clock.zone)).toInstant()
    }

    private suspend fun stored(name: String): String? = settings().get(name)?.takeIf { it.isNotEmpty() }

    private suspend fun put(name: String, value: String) = settings().put(Setting(name, value))

    companion object {
        val NIGHT: LocalTime = LocalTime.of(3, 0)
        const val KEPT = 7
        const val WARN_AFTER_DAYS = 30L

        private const val PREFIX = "EmilysHub-Backup-"
        private const val SUFFIX = ".emhb"

        /** "2026-10-24-030000": the day and time a nightly backup was made. */
        private val STAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmmss")

        /**
         * The names of backups, nightly or saved by hand ([Backups.fileName]), and only those: no other file is deleted.
         * A day saved by hand counts from its start.
         */
        private val BACKUP_NAME = Regex("""EmilysHub-Backup-(\d{4}-\d{2}-\d{2})(?:-(\d{6}))?\.emhb""")

        private fun madeAt(fileName: String): LocalDateTime? {
            val match = BACKUP_NAME.matchEntire(fileName) ?: return null
            val (day, time) = match.destructured
            return try {
                if (time.isEmpty()) LocalDate.parse(day).atStartOfDay() else LocalDateTime.parse("$day-$time", STAMP)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        private const val FOLDER = "backup-folder"
        private const val FOLDER_NAME = "backup-folder-name"
        private const val LAST_TRIED = "backup-night-tried"
        private const val FAILED = "backup-night-failed"
        private const val WATCHED_SINCE = "backup-watched-since"
        private const val TAG = "EmilysHub"
    }
}
