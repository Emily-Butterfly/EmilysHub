package de.emilyshub.doku

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.emilyshub.backup.BackupException
import de.emilyshub.backup.BackupFile
import de.emilyshub.backup.BackupKey
import de.emilyshub.backup.BackupProblem
import de.emilyshub.backup.DokuVault
import de.emilyshub.logged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

/** Where the Doku stands (SPEC.md §14). */
sealed interface DokuState {
    /** No Doku password yet: it is set on first opening. */
    data object New : DokuState

    /**
     * Its database closed and its key gone from memory. With the [fingerprint] on, the prompt comes first; once a new
     * fingerprint was set up on the phone, the fingerprint is off and [fingerprintLost] says so, until the password
     * opens the Doku.
     */
    data class Locked(val fingerprint: Boolean, val fingerprintLost: Boolean = false) : DokuState

    /** Unlocked; after the fingerprint was lost, [offerFingerprint] to turn it on again. */
    data class Open(val fingerprint: Boolean, val offerFingerprint: Boolean = false) : DokuState
}

/** How unlocking with the fingerprint ended. */
enum class FingerprintUnlock {
    /** The Doku is open. */
    Opened,

    /** The password is to be asked for: chosen in the prompt, or the fingerprint no longer opens the Doku. */
    Password,

    /** The prompt went away otherwise; the Doku stays locked, to ask again. */
    Dismissed,
}

/**
 * What an editor of the Doku has typed and not yet saved, to be saved before the Doku locks (SPEC.md §14: "Was gerade
 * getippt wird, wird vorher gespeichert"); the editors of patients and appointments (#59, #60) hand theirs over by it.
 */
fun interface DokuSaver {
    /** The write of what is not saved yet, or null when everything is. */
    fun pending(): (suspend (DokuDatabase) -> Unit)?
}

/**
 * The Doku's vault (SPEC.md §14): its own database, whose random 256-bit key is kept only sealed ([DokuKeys]) in
 * [keyFile], once under the Doku password and, if on, once under the [fingerprint]'s key. The app's own database key
 * has no part in it. Unlocked, the database is open and the key in memory; locked, the database is closed and both the
 * key and its raw form are wiped.
 *
 * It locks when the screen showing it says so ([lock]), and after [IDLE_MILLIS] without a touch or a key ([touched]),
 * by [now], a monotonic clock. It lives as long as the process: a new process starts locked.
 *
 * The backups take its [files] along as they lie, encrypted, and put them back ([DokuVault]), without unlocking it.
 */
class Doku internal constructor(
    private val keyFile: DokuKeyFile,
    private val files: DokuFiles,
    private val fingerprint: Fingerprint,
    private val openDatabase: (RawKey) -> DokuDatabase,
    /** Where what is copied from the Doku goes. */
    val clipboard: DokuClipboard,
    private val io: CoroutineContext,
    private val background: CoroutineScope,
    private val iterations: Int,
    private val now: () -> Long,
) : DokuVault, DokuStore {
    constructor(
        keyFile: File,
        databaseFile: File,
        fingerprint: Fingerprint,
        openDatabase: (RawKey) -> DokuDatabase,
        clipboard: DokuClipboard,
        io: CoroutineContext,
        background: CoroutineScope,
        iterations: Int = BackupKey.ITERATIONS,
        now: () -> Long = SystemClock::elapsedRealtime,
    ) : this(DokuKeyFile(keyFile), DokuFiles(keyFile, databaseFile), fingerprint, openDatabase, clipboard, io, background, iterations, now)

    /** Compose state; null until [load] has read the key file. Nothing of the Doku shows unless it is [DokuState.Open]. */
    var state by mutableStateOf<DokuState?>(null)
        private set

    private class Opened(val key: ByteArray, val rawKey: RawKey, val database: DokuDatabase)

    private var opened: Opened? = null
    private var keys: DokuKeys? = null
    private val savers = mutableSetOf<DokuSaver>()
    private var lastTouch = 0L

    /** How often the Doku was told to lock: an unlock still under way when it is told so again does not open it. */
    private var locks = 0

    /**
     * Opening and closing the database, one at a time: an unlock waits for the lock before it to have closed it. Every
     * read and write of the database holds it too ([withDatabase]), so that nothing writes while it is held.
     */
    private val openingAndClosing = Mutex()

    /** The closing of the database the last [lock] started. */
    private var lastClose: Job? = null

    /**
     * What [block] makes of the open database, on [io]; null once the Doku is locked. Locking waits for it to finish
     * before the database is closed, so that nothing reads or writes a closed database.
     */
    override suspend fun <T> withDatabase(block: suspend (DokuDatabase) -> T): T? = openingAndClosing.withLock {
        val database = opened?.database ?: return@withLock null
        withContext(io) { block(database) }
    }

    /** How many changes were made through [changing]: Compose state, read by the screens to read anew. */
    var changes by mutableIntStateOf(0)
        private set

    /** What [block] makes of the open database, as [withDatabase], counted in [changes] if it was open. */
    override suspend fun <T> changing(block: suspend (DokuDatabase) -> T): T? =
        withDatabase(block).also { if (it != null) changes++ }

    /** Whether the fingerprint can be turned on: a strong one is set up on the phone. */
    val canUseFingerprint: Boolean get() = fingerprint.isEnrolled()

    /** Reads the key file, to know whether the Doku has its password yet and whether the fingerprint is on; once. */
    suspend fun load() {
        if (state != null) return
        val read = withContext(io) {
            files.recover()
            keyFile.read()
        }
        keys = read
        if (state == null) state = read?.lockedState() ?: DokuState.New
    }

    /** Sets the Doku password, which must have no [passwordProblem], and opens the Doku with a new, empty database. */
    suspend fun setUp(password: String) {
        require(passwordProblem(password, password) == null) { "The Doku password is too short" }
        check(state == DokuState.New) { "The Doku password is set once" }
        val since = locks
        val key = DokuKeys.newKey()
        keys = withContext(io + NonCancellable) { DokuKeys.sealed(key, password, iterations).also(keyFile::write) }
        open(key, since)
    }

    /** Opens the Doku with its password; false, leaving it locked, if [password] is not it. */
    suspend fun unlockWithPassword(password: String): Boolean {
        if (state !is DokuState.Locked) return state is DokuState.Open
        val since = locks
        val key = withContext(io) { checkNotNull(keys).openWithPassword(password) } ?: return false
        open(key, since)
        return true
    }

    /**
     * Asks for the fingerprint over [activity] and opens the Doku with it. Once a new fingerprint on the phone made its
     * key invalid, the fingerprint is turned off and the password asked for, as when the prompt's "Passwort" is tapped.
     */
    suspend fun unlockWithFingerprint(activity: Activity): FingerprintUnlock {
        if (state !is DokuState.Locked) return if (state is DokuState.Open) FingerprintUnlock.Opened else FingerprintUnlock.Password
        val current = checkNotNull(keys)
        val sealed = current.byFingerprint ?: return FingerprintUnlock.Password
        val cipher = withContext(io) { fingerprint.openingCipher(sealed) }
        if (cipher == null) {
            update {
                fingerprint.deleteKey()
                current.withoutFingerprint(lost = true)
            }
            if (state is DokuState.Locked) state = checkNotNull(keys).lockedState()
            return FingerprintUnlock.Password
        }
        val since = locks
        val result = fingerprint.prompt(activity, cipher, FingerprintPurpose.Unlock)
        if (result !is PromptResult.Recognised) {
            return if (result == PromptResult.Other) FingerprintUnlock.Password else FingerprintUnlock.Dismissed
        }
        val key = try {
            current.openWithFingerprint(result.cipher)
        } catch (_: IllegalStateException) {
            return FingerprintUnlock.Password
        }
        open(key, since)
        return if (state is DokuState.Open) FingerprintUnlock.Opened else FingerprintUnlock.Dismissed
    }

    /**
     * Asks for the fingerprint over [activity] and turns it on with it; only while open, and not once it locked
     * meanwhile. Whether it is on now.
     */
    suspend fun turnOnFingerprint(activity: Activity): Boolean {
        requireOpen()
        // A new Keystore key is made for it, which takes a while.
        val cipher = withContext(io) { fingerprint.sealingCipher() }
        val result = fingerprint.prompt(activity, cipher, FingerprintPurpose.TurnOn)
        val key = opened?.key ?: return false
        if (result !is PromptResult.Recognised) return false
        update { checkNotNull(keys).withFingerprint(key, result.cipher) }
        state = DokuState.Open(fingerprint = true)
        return true
    }

    /** Turns the fingerprint off; only while open. */
    suspend fun turnOffFingerprint() {
        requireOpen()
        update {
            fingerprint.deleteKey()
            checkNotNull(keys).withoutFingerprint()
        }
        state = DokuState.Open(fingerprint = false)
    }

    /** Leaves the fingerprint off, as it is, without offering again to turn it on. */
    suspend fun declineFingerprint() {
        val open = state as? DokuState.Open ?: return
        if (open.fingerprint) return
        update { checkNotNull(keys).withoutFingerprint() }
        state = open.copy(offerFingerprint = false)
    }

    /**
     * Seals the database key anew under [new], which must have no [passwordProblem]; false if [old] is not the Doku
     * password. The key itself, and so the data, stay as they are.
     */
    suspend fun changePassword(old: String, new: String): Boolean {
        require(passwordProblem(new, new) == null) { "The Doku password is too short" }
        requireOpen()
        return withContext(io + NonCancellable) {
            val current = checkNotNull(keys)
            val key = current.openWithPassword(old) ?: return@withContext false
            try {
                keys = current.withPassword(key, new, iterations).also(keyFile::write)
            } finally {
                key.fill(0)
            }
            true
        }
    }

    /** A touch or a key in the Doku: the [IDLE_MILLIS] start anew. */
    fun touched() {
        lastTouch = now()
    }

    /** How long until the Doku has been idle for [IDLE_MILLIS]. */
    fun millisUntilIdle(): Long = (lastTouch + IDLE_MILLIS - now()).coerceAtLeast(0)

    /** Locks the Doku once it has been idle for [IDLE_MILLIS]. */
    fun lockIfIdle() {
        if (opened != null && millisUntilIdle() == 0L) lock()
    }

    /** [saver] is asked for what it has not saved yet each time the Doku locks, until the returned handle is closed. */
    fun addSaver(saver: DokuSaver): AutoCloseable {
        savers += saver
        return AutoCloseable { savers -= saver }
    }

    /**
     * Locks the Doku at once: nothing of it shows from here on, and an unlock still under way does not open it. What
     * the editors have not saved yet is saved, then the database is closed and the key wiped; and the clipboard emptied
     * if it still holds text copied from the Doku.
     */
    fun lock() {
        locks++
        clipboard.clearIfFromDoku()
        val closing = opened ?: return
        val pending = savers.mapNotNull { it.pending() }
        opened = null
        state = checkNotNull(keys).lockedState()
        lastClose = background.launch(io + NonCancellable) {
            openingAndClosing.withLock {
                try {
                    pending.forEach { it(closing.database) }
                } finally {
                    closing.database.close()
                    closing.key.fill(0)
                    closing.rawKey.wipe()
                }
            }
        }
    }

    /**
     * The Doku as its files lie now, for a backup, its key sealed only under the Doku password; null before it has one.
     * Open or locked, it is not unlocked for this, and what is in it holds together, as nothing writes meanwhile. Fails
     * as [BackupProblem.TooLarge] if it is larger than a backup can be.
     */
    override suspend fun snapshot(): DokuSnapshot? = openingAndClosing.withLock {
        withContext(io) {
            val stored = keyFile.read() ?: return@withContext null
            // What is in the write-ahead log into the database first, so that the backup carries no more than it needs.
            opened?.database?.checkpoint()
            files.snapshot(stored.passwordOnly(), BackupFile.MAX_BYTES) ?: throw BackupException(BackupProblem.TooLarge)
        }
    }

    /**
     * Puts [snapshot] in place of the Doku, locked first, then runs [during]; if that fails, the Doku is put back as it
     * was. Then the Doku password of the snapshot holds, and the fingerprint is off, to be turned on once it is open.
     * Called where the Doku's state is, as unlocking is; only the files are read and written on [io].
     */
    override suspend fun <T> replacedBy(snapshot: DokuSnapshot, during: suspend () -> T): T {
        lock()
        lastClose?.join()
        return openingAndClosing.withLock {
            // An unlock begun meanwhile, with the keys before, does not open it.
            locks++
            opened?.let { open ->
                opened = null
                state = checkNotNull(keys).lockedState()
                withContext(io + NonCancellable) { open.database.close() }
                open.key.fill(0)
                open.rawKey.wipe()
            }
            val replacement = withContext(io + NonCancellable) { files.replaceWith(snapshot) }
            val result = try {
                during()
            } catch (e: Throwable) {
                try {
                    withContext(io + NonCancellable) { replacement.undo() }
                } catch (failed: Exception) {
                    e.addSuppressed(failed)
                }
                throw e
            }
            keys = snapshot.keys
            if (state != null) state = snapshot.keys.lockedState()
            // All is restored now: what is left to tidy up must not make it look as though it had failed.
            try {
                withContext(io + NonCancellable) {
                    replacement.done()
                    fingerprint.deleteKey()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tidying up after the Doku was restored failed: ${e.logged}")
            }
            result
        }
    }

    /**
     * Opens the database with [key], unless the Doku was told to lock [since] the unlock began: it left the screen
     * meanwhile, say. After a lost fingerprint, and with one on the phone again, it offers to turn it on again.
     */
    private suspend fun open(key: ByteArray, since: Int) {
        // Opened already, by a second tap say: this key is not needed.
        if (opened != null) return key.fill(0)
        val rawKey = DokuDatabase.rawKey(key)
        val database = openingAndClosing.withLock {
            // Put in place of from a backup meanwhile, say: this key may not fit it.
            if (locks != since) return@withLock null
            withContext(io) {
                openDatabase(rawKey).also {
                    // Opened here, so that a damaged file fails now, not on the first screen.
                    it.openHelper.writableDatabase
                }
            }
        }
        val current = checkNotNull(keys)
        if (database == null || locks != since) {
            database?.let { withContext(io + NonCancellable) { it.close() } }
            key.fill(0)
            rawKey.wipe()
            // Set up meanwhile, the Doku now has its password.
            if (state == DokuState.New) state = current.lockedState()
            return
        }
        opened = Opened(key, rawKey, database)
        lastTouch = now()
        state = DokuState.Open(current.hasFingerprint, offerFingerprint = current.fingerprintLost && fingerprint.isEnrolled())
    }

    /** Writes the keys [change] makes of the current ones. */
    private suspend fun update(change: () -> DokuKeys) {
        keys = withContext(io + NonCancellable) { change().also(keyFile::write) }
    }

    private fun requireOpen(): Opened = checkNotNull(opened) { "The Doku is locked" }

    private fun DokuKeys.lockedState() = DokuState.Locked(fingerprint = hasFingerprint, fingerprintLost = fingerprintLost)

    companion object {
        private const val TAG = "EmilysHub"

        /** The fewest characters a Doku password has. */
        const val MIN_PASSWORD_LENGTH = 8

        /** After this long without a touch or a key the Doku locks. */
        const val IDLE_MILLIS = 5 * 60_000L

        /** What is wrong with a new Doku [password] and its [repeated] typing, if anything. */
        fun passwordProblem(password: String, repeated: String): DokuPasswordProblem? = when {
            password.length < MIN_PASSWORD_LENGTH -> DokuPasswordProblem.TooShort
            password != repeated -> DokuPasswordProblem.NotRepeated
            else -> null
        }
    }
}

/** What keeps a new Doku password from being set: shorter than [Doku.MIN_PASSWORD_LENGTH], or typed twice unlike. */
enum class DokuPasswordProblem { TooShort, NotRepeated }
