package de.emilyshub.data

import android.util.Log
import de.emilyshub.logged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.CoroutineContext

/** Why the app's data cannot be opened (#100), in one of three groups the user is told; never more than that. */
enum class Unopenable {
    /** The key file cannot be read or is damaged, or the Keystore key that seals it is lost. */
    KeyBroken,

    /** The database file is damaged, or sealed with another key. */
    FileBroken,

    /** The data come from a newer version of the app. */
    NewerVersion,
}

/** The app's data cannot be opened, for [reason]. */
class DataUnavailable(val reason: Unopenable, cause: Throwable? = null) : IllegalStateException("The data cannot be opened: $reason", cause)

/** Data laid aside (#100): those that could not be opened, kept until deleted for good; [at] when they were laid aside. */
data class SetAside(val id: String, val at: Instant)

/**
 * The app's own data as they lie on the phone (SPEC.md §9, §10, #100): the database, opened on first use with [open],
 * which fails with a [DataUnavailable] saying why; once failed, it stays so until [retry], so that every reader in the
 * background fails at once rather than trying again each time.
 *
 * What makes up the data on disk are the [files]: the database with its companions, its key file, and the lessons'
 * attachments with theirs. They are never deleted by the app on its own: [beginAnew] lays them aside under
 * [setAsideFolder], and opens empty data in their place, which [keep] keeps, or [undo] takes back, putting everything as
 * it was. Laid aside, they stay until deleted for good ([delete]). Should the app end between the two, the next start
 * puts everything as it was, as a mark in the folder laid aside tells. The Doku is not among them, and stays as it is.
 * [onReplaced] runs whenever other data take their place, for what holds on to anything of them, such as a key.
 */
class HubData(
    private val open: () -> HubDatabase,
    private val files: List<File>,
    private val setAsideFolder: File,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
    private val onReplaced: () -> Unit = {},
) {
    private val lock = Any()
    private var opened: HubDatabase? = null
    private var failed: DataUnavailable? = null

    /** Laid aside by [beginAnew] and neither kept nor undone yet. */
    private var pending: File? = null

    /** The database, opened on first use; fails with a [DataUnavailable] while it cannot be. Never on the main thread. */
    val database: HubDatabase
        get() = synchronized(lock) {
            opened ?: failed?.let { throw it } ?: openNow()
        }

    /** Why the data cannot be opened, none once they are. */
    suspend fun check(): Unopenable? = withContext(io) {
        try {
            database
            null
        } catch (e: DataUnavailable) {
            e.reason
        }
    }

    /** Opens them once more, as "Erneut versuchen" asks; why not, none once they are. */
    suspend fun retry(): Unopenable? {
        withContext(io) { synchronized(lock) { close() } }
        return check()
    }

    /**
     * Lays the data aside, as they are, and opens empty data in their place, which, from then on, [database] is; to be
     * kept with [keep] or taken back with [undo].
     */
    suspend fun beginAnew() = withContext(io + NonCancellable) {
        synchronized(lock) {
            check(pending == null) { "Already begun anew" }
            close()
            // A folder of its own, also when laid aside within the same millisecond.
            val folder = generateSequence(clock.millis()) { it + 1 }.map { File(setAsideFolder, it.toString()) }.first { !it.exists() }
            folder.mkdirs()
            File(folder, PENDING).createNewFile()
            pending = folder
            try {
                files.forEachIndexed { index, file -> if (file.exists()) move(file, File(folder, nameOf(index, file))) }
                // All moved: from here on, what lies in the place of one is new.
                File(folder, FRESH).createNewFile()
                onReplaced()
                openNow()
            } catch (e: Exception) {
                // Half moved or not opening: all as it was.
                undoNow()
                throw e
            }
        }
    }

    /** Keeps the data begun anew; those laid aside stay so. */
    suspend fun keep() = withContext(io + NonCancellable) {
        synchronized(lock) {
            val folder = checkNotNull(pending) { "Nothing begun anew" }
            File(folder, PENDING).delete()
            File(folder, FRESH).delete()
            pending = null
        }
    }

    /** Takes back the data begun anew, if any, and puts those laid aside back, as they were. */
    suspend fun undo() = withContext(io + NonCancellable) { synchronized(lock) { undoNow() } }

    /** What was laid aside, the newest first. */
    suspend fun setAside(): List<SetAside> = withContext(io) {
        synchronized(lock) {
            setAsideFolder.listFiles().orEmpty()
                .filter { it.isDirectory && it != pending && !File(it, PENDING).exists() }
                .mapNotNull { folder -> folder.name.toLongOrNull()?.let { SetAside(folder.name, Instant.ofEpochMilli(it)) } }
                .sortedByDescending { it.at }
        }
    }

    /** Deletes [setAside] for good. */
    suspend fun delete(setAside: SetAside) = withContext(io + NonCancellable) {
        synchronized(lock) {
            val folder = File(setAsideFolder, setAside.id)
            require(folder.parentFile == setAsideFolder && setAside.id.all(Char::isDigit)) { "Not laid aside here" }
            if (folder != pending) folder.deleteRecursively()
        }
    }

    private fun openNow(): HubDatabase {
        // What the app ended on before it was kept or undone is put back first.
        if (pending == null) leftPending()?.let { folder -> putBack(folder) }
        return try {
            open().also {
                opened = it
                failed = null
            }
        } catch (e: Exception) {
            val unavailable = e as? DataUnavailable ?: DataUnavailable(Unopenable.FileBroken, e)
            // Without its message, which may quote the data (SPEC.md §9).
            Log.e(TAG, "The data cannot be opened (${unavailable.reason}): ${(unavailable.cause ?: unavailable).logged}")
            failed = unavailable
            throw unavailable
        }
    }

    private fun undoNow() {
        val folder = pending ?: return
        close()
        putBack(folder)
        pending = null
        failed = null
    }

    /**
     * Puts back what lies in [folder], in place of what took its place. Where nothing lies there, as the app may have ended
     * before moving it, only what was made anew in its place goes: one that was never moved stays as it is.
     */
    private fun putBack(folder: File) {
        val fresh = File(folder, FRESH).exists()
        files.forEachIndexed { index, file ->
            val laid = File(folder, nameOf(index, file))
            when {
                laid.exists() -> {
                    file.deleteRecursively()
                    move(laid, file)
                }
                // Made anew in its place; without the mark, it was never moved and stays.
                fresh -> file.deleteRecursively()
            }
        }
        folder.deleteRecursively()
        onReplaced()
    }

    private fun leftPending(): File? = setAsideFolder.listFiles().orEmpty().firstOrNull { File(it, PENDING).exists() }

    private fun close() {
        opened?.close()
        opened = null
        failed = null
    }

    private fun move(from: File, to: File) {
        to.parentFile?.mkdirs()
        check(from.renameTo(to)) { "Could not move ${from.name}" }
    }

    private fun nameOf(index: Int, file: File) = "$index-${file.name}"

    private companion object {
        const val TAG = "EmilysHub"

        /** The mark of data laid aside that were neither kept nor undone. */
        const val PENDING = "pending"

        /** The mark that all was laid aside, and anything in the places of the files was made anew. */
        const val FRESH = "fresh"
    }
}
