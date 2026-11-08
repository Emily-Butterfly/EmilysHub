package de.emilyshub.doku

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.emilyshub.backup.BackupKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.Cipher
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

/** What an editor of the Doku has typed and not yet saved, to be saved before the Doku locks; null when nothing is. */
fun interface DokuSaver {
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
 */
class Doku internal constructor(
    private val keyFile: DokuKeyFile,
    private val fingerprint: Fingerprint,
    private val openDatabase: (rawKey: ByteArray) -> DokuDatabase,
    /** Where what is copied from the Doku goes. */
    val clipboard: DokuClipboard,
    private val io: CoroutineContext,
    private val background: CoroutineScope,
    private val iterations: Int,
    private val now: () -> Long,
) {
    constructor(
        keyFile: File,
        fingerprint: Fingerprint,
        openDatabase: (rawKey: ByteArray) -> DokuDatabase,
        clipboard: DokuClipboard,
        io: CoroutineContext,
        background: CoroutineScope,
        iterations: Int = BackupKey.ITERATIONS,
        now: () -> Long = SystemClock::elapsedRealtime,
    ) : this(DokuKeyFile(keyFile), fingerprint, openDatabase, clipboard, io, background, iterations, now)

    /** Compose state; null until [load] has read the key file. Nothing of the Doku shows unless it is [DokuState.Open]. */
    var state by mutableStateOf<DokuState?>(null)
        private set

    private class Opened(val key: ByteArray, val rawKey: ByteArray, val database: DokuDatabase)

    private var opened: Opened? = null
    private var keys: DokuKeys? = null
    private val savers = mutableSetOf<DokuSaver>()
    private var lastTouch = 0L

    /** How often the Doku was told to lock: an unlock still under way when it is told so again does not open it. */
    private var locks = 0

    /** Opening and closing the database, one at a time: an unlock waits for the lock before it to have closed it. */
    private val vault = Mutex()

    /** The open database; only while [state] is [DokuState.Open]. */
    val database: DokuDatabase get() = checkNotNull(opened) { "The Doku is locked" }.database

    suspend fun load() {
        if (state != null) return
        val read = withContext(io) { keyFile.read() }
        keys = read
        state = when {
            opened != null -> state
            read == null -> DokuState.New
            else -> DokuState.Locked(fingerprint = read.hasFingerprint)
        }
    }

    /** Sets the Doku password, which must have no [passwordProblem], and opens the Doku with a new, empty database. */
    suspend fun setUp(password: String) {
        require(passwordProblem(password, password) == null) { "The Doku password is too short" }
        check(state == DokuState.New) { "The Doku password is set once" }
        val since = locks
        val key = DokuKeys.newKey()
        val sealed = withContext(io + NonCancellable) {
            DokuKeys.sealed(key, password, iterations).also(keyFile::write)
        }
        keys = sealed
        open(key, since, fingerprint = false)
    }

    /** Opens the Doku with its password; false, leaving it locked, if [password] is not it. */
    suspend fun unlockWithPassword(password: String): Boolean {
        val locked = state as? DokuState.Locked ?: return state is DokuState.Open
        val since = locks
        val key = withContext(io) { checkNotNull(keys).openWithPassword(password) } ?: return false
        open(key, since, fingerprint = locked.fingerprint, offerFingerprint = locked.fingerprintLost && fingerprint.isEnrolled())
        return true
    }

    /**
     * The cipher for the fingerprint's prompt to set free, to open the Doku with; null once the fingerprint's key is no
     * longer valid, as when a new fingerprint was set up on the phone: then the fingerprint is turned off, and the
     * password asked for.
     */
    suspend fun unlockingCipher(): Cipher? {
        val current = keys ?: return null
        val iv = current.fingerprintIv ?: return null
        withContext(io) { fingerprint.openingCipher(iv) }?.let { return it }
        withContext(io + NonCancellable) {
            fingerprint.deleteKey()
            current.withoutFingerprint().also(keyFile::write)
        }.let { keys = it }
        if (state is DokuState.Locked) state = DokuState.Locked(fingerprint = false, fingerprintLost = true)
        return null
    }

    /**
     * Opens the Doku with [cipher], set free by the fingerprint's prompt from [unlockingCipher]; false, leaving it locked
     * for the password, if what the fingerprint sealed does not open.
     */
    suspend fun unlockWithFingerprint(cipher: Cipher): Boolean {
        if (state !is DokuState.Locked) return state is DokuState.Open
        val since = locks
        val key = try {
            checkNotNull(keys).openWithFingerprint(cipher)
        } catch (_: IllegalStateException) {
            return false
        }
        open(key, since, fingerprint = true)
        return true
    }

    /** The cipher for the fingerprint's prompt to set free, to turn the fingerprint on with; only while open. */
    suspend fun sealingCipher(): Cipher {
        check(opened != null) { "The Doku is locked" }
        // A new Keystore key is made for it, which takes a while.
        return withContext(io) { fingerprint.sealingCipher() }
    }

    /** Turns the fingerprint on with [cipher], set free by its prompt from [sealingCipher]; not once the Doku has locked meanwhile. */
    suspend fun enableFingerprint(cipher: Cipher) {
        val key = opened?.key ?: return
        keys = withContext(io + NonCancellable) { checkNotNull(keys).withFingerprint(key, cipher).also(keyFile::write) }
        state = DokuState.Open(fingerprint = true)
    }

    /** Turns the fingerprint off; only while open. */
    suspend fun disableFingerprint() {
        check(opened != null) { "The Doku is locked" }
        keys = withContext(io + NonCancellable) {
            fingerprint.deleteKey()
            checkNotNull(keys).withoutFingerprint().also(keyFile::write)
        }
        state = DokuState.Open(fingerprint = false)
    }

    /** Leaves the fingerprint off, as it is, without offering again to turn it on. */
    fun declineFingerprint() {
        val open = state as? DokuState.Open ?: return
        state = open.copy(offerFingerprint = false)
    }

    /**
     * Seals the database key anew under [new], which must have no [passwordProblem]; false if [old] is not the Doku
     * password. The key itself, and so the data, stay as they are.
     */
    suspend fun changePassword(old: String, new: String): Boolean {
        require(passwordProblem(new, new) == null) { "The Doku password is too short" }
        check(opened != null) { "The Doku is locked" }
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
     * Locks the Doku at once: nothing of it shows from here on. What the editors have not saved yet is saved, then the
     * database is closed and the key wiped; and the clipboard emptied if it still holds text copied from the Doku.
     */
    fun lock() {
        locks++
        val closing = opened ?: return
        val pending = savers.mapNotNull { it.pending() }
        opened = null
        state = DokuState.Locked(fingerprint = keys?.hasFingerprint == true)
        clipboard.clearIfFromDoku()
        background.launch(io + NonCancellable) {
            vault.withLock {
                try {
                    pending.forEach { it(closing.database) }
                } finally {
                    closing.database.close()
                    closing.key.fill(0)
                    closing.rawKey.fill(0)
                }
            }
        }
    }

    /** Opens the database with [key], unless the Doku was told to lock [since] the unlock began: it went to the background meanwhile, say. */
    private suspend fun open(key: ByteArray, since: Int, fingerprint: Boolean, offerFingerprint: Boolean = false) {
        // Opened already, by a second tap say: this key is not needed.
        if (opened != null) return key.fill(0)
        val rawKey = DokuDatabase.rawKey(key)
        val database = vault.withLock {
            withContext(io) {
                openDatabase(rawKey).also {
                    // Opened here, so that a damaged file fails now, not on the first screen.
                    it.openHelper.writableDatabase
                }
            }
        }
        if (locks != since) {
            withContext(io + NonCancellable) { database.close() }
            key.fill(0)
            rawKey.fill(0)
            // Set up meanwhile, the Doku now has its password.
            if (state == DokuState.New) state = DokuState.Locked(fingerprint = false)
            return
        }
        opened = Opened(key, rawKey, database)
        lastTouch = now()
        state = DokuState.Open(fingerprint, offerFingerprint)
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8

        /** After this long without a touch or a key the Doku locks. */
        const val IDLE_MILLIS = 5 * 60_000L

        fun passwordProblem(password: String, repeated: String): DokuPasswordProblem? = when {
            password.length < MIN_PASSWORD_LENGTH -> DokuPasswordProblem.TooShort
            password != repeated -> DokuPasswordProblem.NotRepeated
            else -> null
        }
    }
}

enum class DokuPasswordProblem { TooShort, NotRepeated }
