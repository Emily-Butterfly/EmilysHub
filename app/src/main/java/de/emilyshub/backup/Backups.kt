package de.emilyshub.backup

import de.emilyshub.data.HubDatabase
import de.emilyshub.data.Setting
import de.emilyshub.data.SettingsDao
import de.emilyshub.security.KeyWrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Base64
import java.util.concurrent.Callable
import javax.crypto.SecretKey
import kotlin.coroutines.CoroutineContext

/** What is wrong with a new backup password, typed twice. */
enum class PasswordProblem { TooShort, NotRepeated }

/** A backup opened with its password and checked against the tables of the app, to be restored once confirmed. */
class Backup internal constructor(internal val content: BackupContent, internal val key: BackupKey, private val zone: ZoneId) {

    /** How many routines, to-dos, places and tags a backup holds, for the user to see what it would bring back. */
    data class Summary(val routines: Int, val todos: Int, val places: Int, val tags: Int)

    /** When it was made, in the time zone here. */
    val created: ZonedDateTime get() = content.created.atZone(zone)

    val summary: Summary get() = Summary(count("routines"), count("todos"), count("places"), count("tags"))

    private fun count(table: String) = content.tables[table]?.rows?.size ?: 0
}

/**
 * The backups of all data (SPEC.md §10). The backup password is set once, and only the key derived from it is kept, in
 * the settings, sealed with the Keystore key [wrappingKey]; backups are made with it, without asking for the password.
 * A backup opens with the password it was made with, on this device or another; one of an older version of the app is
 * brought up to this one by [migration]. It is checked strictly before it can be restored. Restoring puts it in place of
 * all data but the settings of the device, all in one or nothing at all; then [onRestore] runs, for the reminders to
 * follow. [database] and [settings] are only called on [io].
 */
class Backups(
    private val database: () -> HubDatabase,
    private val settings: () -> SettingsDao,
    private val wrappingKey: () -> SecretKey,
    private val clock: Clock,
    private val migration: BackupMigration,
    private val io: CoroutineContext = Dispatchers.IO,
    private val iterations: Int = BackupKey.ITERATIONS,
    private val onRestore: suspend () -> Unit = {},
) {
    suspend fun hasPassword(): Boolean = withContext(io) { settings().get(KEY) != null }

    /** Sets the backup password, which must have no [passwordProblem]: once, it cannot be changed. */
    suspend fun setPassword(password: String) = withContext(io + NonCancellable) {
        require(passwordProblem(password, password) == null) { "The backup password is too short" }
        check(settings().get(KEY) == null) { "The backup password is set once" }
        store(BackupKey.derive(password, iterations = iterations))
    }

    /** Writes a backup of all data to the stream [output] opens; only once the backup password is set. */
    suspend fun export(output: () -> OutputStream) = withContext(io + NonCancellable) {
        val key = checkNotNull(storedKey()) { "The backup password is not set" }
        val database = database()
        val tables = database.runInTransaction(Callable { DataTables(database.openHelper.writableDatabase).read() })
        val file = BackupFile.seal(BackupContent(HubDatabase.VERSION, clock.instant(), tables).toJson(), key)
        output().use { it.write(file) }
    }

    /**
     * The backup in the stream [input] opens, opened with its [password] and checked: by putting it in place of all data
     * and reading it back, in a transaction that is then rolled back. Fails with a [BackupException] that says why it
     * cannot be restored.
     */
    suspend fun open(input: () -> InputStream, password: String): Backup = withContext(io) {
        val decrypted = BackupFile.open(input().use(BackupFile::read), password)
        val found = BackupContent.fromJson(decrypted.plaintext)
        val content = when {
            found.schema > HubDatabase.VERSION -> throw BackupException(BackupProblem.NewerVersion)
            found.schema < HubDatabase.VERSION -> migration.upToDate(found)
            else -> found
        }
        replaceAllWith(content, keep = false)
        Backup(content, decrypted.key, clock.zone)
    }

    /**
     * Puts [backup] in place of all data. Its backup password becomes the one here first, unless one is set already: so
     * that a failure leaves the data as they were, as the user is then told.
     */
    suspend fun restore(backup: Backup) = withContext(io + NonCancellable) {
        if (settings().get(KEY) == null) store(backup.key)
        replaceAllWith(backup.content, keep = true)
        onRestore()
    }

    /** The name a backup made today is offered to be saved under: "EmilysHub-Backup-2026-10-23.emhb". */
    fun fileName(): String = "EmilysHub-Backup-${LocalDate.now(clock)}.emhb"

    /**
     * Puts [content] in place of all data and reads it back as the app would, in one transaction: kept only if [keep],
     * otherwise rolled back, as a check that it would work.
     */
    private fun replaceAllWith(content: BackupContent, keep: Boolean) {
        val database = database()
        val replace = {
            DataTables(database.openHelper.writableDatabase).replaceWith(content.tables)
            database.readBack(content.tables.keys)
        }
        if (keep) {
            database.runInTransaction(replace)
        } else {
            // Never marked successful, so rolled back; and nothing the app shows has changed meanwhile.
            val db = database.openHelper.writableDatabase
            db.beginTransaction()
            try {
                replace()
            } finally {
                db.endTransaction()
            }
        }
    }

    /** Every one of the [tables] read as the app reads it; a value it cannot read makes the backup damaged. */
    private fun HubDatabase.readBack(tables: Set<String>) {
        val dao = backup()
        val reads: Map<String, () -> List<Any>> = mapOf(
            "routines" to dao::routines, "occurrences" to dao::occurrences, "checklist_items" to dao::checklistItems,
            "checklist_ticks" to dao::checklistTicks, "tags" to dao::tags, "routine_tags" to dao::routineTags,
            "places" to dao::places, "place_wifis" to dao::placeWifis, "routine_places" to dao::routinePlaces,
            "todos" to dao::todos, "todo_checklist_items" to dao::todoChecklistItems, "todo_tags" to dao::todoTags,
            "todo_places" to dao::todoPlaces,
        )
        check(reads.keys == tables) { "Each table is to be read back: ${tables - reads.keys}" }
        try {
            reads.values.forEach { it() }
        } catch (e: RuntimeException) {
            // What the type converters throw on what they cannot read, such as an unknown status or a date that is none.
            throw BackupException(BackupProblem.Damaged, e)
        }
    }

    private suspend fun storedKey(): BackupKey? = settings().get(KEY)?.let { stored ->
        val parts = stored.split(SEPARATOR)
        check(parts.size == 3) { "The stored backup key is damaged" }
        val (iterations, salt, wrapped) = parts
        val secret = KeyWrap.unwrap(Base64.getDecoder().decode(wrapped), wrappingKey(), BackupKey.SECRET_BYTES, name = "backup key")
        BackupKey(Base64.getDecoder().decode(salt), iterations.toInt(), secret)
    }

    /** "600000:<salt>:<secret sealed with the Keystore key>", in Base64. */
    private suspend fun store(key: BackupKey) {
        val wrapped = KeyWrap.wrap(key.secret, wrappingKey())
        val base64 = Base64.getEncoder()
        val stored = listOf(key.iterations.toString(), base64.encodeToString(key.salt), base64.encodeToString(wrapped))
        settings().put(Setting(KEY, stored.joinToString(SEPARATOR)))
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 10
        private const val KEY = "backup-key"
        private const val SEPARATOR = ":"

        fun passwordProblem(password: String, repeated: String): PasswordProblem? = when {
            password.length < MIN_PASSWORD_LENGTH -> PasswordProblem.TooShort
            password != repeated -> PasswordProblem.NotRepeated
            else -> null
        }
    }
}
