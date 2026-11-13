package de.emilyshub.backup

import de.emilyshub.data.HubDatabase
import de.emilyshub.data.Setting
import de.emilyshub.data.SettingsDao
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.languages.LessonFiles
import de.emilyshub.notes.notesHoldTogether
import de.emilyshub.notes.notesKeepTheRules
import de.emilyshub.security.KeyWrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
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

    /** How many routines, to-dos, wishes, notes, places and tags a backup holds, for the user to see what it would bring back. */
    data class Summary(val routines: Int, val todos: Int, val wishes: Int, val notes: Int, val places: Int, val tags: Int)

    /** When it was made, in the time zone here. */
    val created: ZonedDateTime get() = content.created.atZone(zone)

    val summary: Summary
        get() = Summary(count("routines"), count("todos"), count("wishes"), count("notes"), count("places"), count("tags"))

    /** Whether it holds a Doku, to be restored only with the Doku password that held when it was made. */
    val hasDoku: Boolean get() = content.doku != null

    private fun count(table: String) = content.tables[table]?.rows?.size ?: 0
}

/**
 * The backups of all data (SPEC.md §10). The backup password is set once, and only the key derived from it is kept, in
 * the settings, sealed with the Keystore key [wrappingKey]; backups are made with it, without asking for the password.
 * A backup opens with the password it was made with, on this device or another; one of an older version of the app is
 * brought up to this one by [migration]. It is checked strictly before it can be restored. Restoring puts it in place of
 * all data but the settings of the device, all in one or nothing at all; then [onRestore] runs, for the reminders to
 * follow. [database] and [settings] are only called on [io].
 *
 * The [doku] goes along in its own encryption, taken as its files lie, so that a backup neither unlocks it nor asks
 * for its password, by night too. It comes back only with the Doku password that held when the backup was made, and
 * else, or from a backup without one, stays as it is.
 *
 * The lessons' [attachments] follow the content in the file, each in a record of its own, so that a backup is written
 * and read as a stream (format 2 of [BackupFile]). Opening a backup checks them all and lays them aside, encrypted;
 * restoring puts them in place of the app's own.
 */
class Backups(
    private val database: () -> HubDatabase,
    private val settings: () -> SettingsDao,
    private val wrappingKey: () -> SecretKey,
    private val clock: Clock,
    private val migration: BackupMigration,
    private val doku: DokuVault,
    private val attachments: AttachmentVault,
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

    /**
     * Writes a backup of all data to the stream [output] opens; only once the backup password is set. The stream is
     * opened only once the content is sealed and every attachment found readable, so that nothing is written if that
     * fails; the attachments are then read again one by one as they are written, none taken in or deleted meanwhile.
     */
    suspend fun export(output: () -> OutputStream) = withContext(io + NonCancellable) {
        val key = checkNotNull(storedKey()) { "The backup password is not set" }
        val database = database()
        val now = clock.instant()
        val snapshot = doku.snapshot()
        attachments.steady {
            val read = database.runInTransaction(Callable { DataTables(database.openHelper.writableDatabase).read() })
            // Each read once here for its size, and once more as it is written, so that they need not all fit in memory.
            val carried = attachmentFilesOf(read).mapNotNull { file ->
                attachments.read(file)?.let { BackupAttachment(file, it.size.toLong()) }
            }
            // One that cannot be read is left out with its row, rather than keeping every backup from being made.
            val tables = withAttachmentsOnly(read, carried.map { it.file }.toSet())
            val content = BackupContent(HubDatabase.VERSION, now, tables, snapshot, carried).toJson()
            output().use { out ->
                val writer = BackupFile.writer(out, key, content)
                carried.forEach { writer.attachment(checkNotNull(attachments.read(it.file)) { "An attachment went missing" }) }
                writer.finish()
            }
        }
        settings().put(Setting(LAST_MADE, now.toString()))
        if (firstMadeStored() == null) settings().put(Setting(FIRST_MADE, now.toString()))
    }

    /** When the last backup was made here, by hand or by night. */
    suspend fun lastMade(): Instant? = withContext(io) { settings().get(LAST_MADE)?.let(Instant::parse) }

    /**
     * When the first backup was made on this device, as far as it was noted: from this version on, or, for backups made
     * before it, from the moment [noteFirstMade] was first told after the update.
     */
    suspend fun firstMade(): Instant? = withContext(io) { firstMadeStored() }

    /** Takes [at] as the moment of the first backup, if there are backups made before the first was noted. */
    suspend fun noteFirstMade(at: Instant) = withContext(io) {
        if (firstMadeStored() == null && settings().get(LAST_MADE) != null) settings().put(Setting(FIRST_MADE, at.toString()))
    }

    private suspend fun firstMadeStored(): Instant? = settings().get(FIRST_MADE)?.takeIf { it.isNotEmpty() }?.let(Instant::parse)

    /**
     * The backup in the stream [input] opens, opened with its [password] and checked: by putting it in place of all data
     * and reading it back, in a transaction that is then rolled back. Fails with a [BackupException] that says why it
     * cannot be restored.
     */
    suspend fun open(input: () -> InputStream, password: String): Backup = withContext(io) {
        // So that no other backup is laid aside or restored meanwhile.
        attachments.steady { openSteady(input, password) }
    }

    private suspend fun openSteady(input: () -> InputStream, password: String): Backup {
        attachments.clearAside()
        try {
            val (found, key) = input().use { stream ->
                val reader = BackupFile.reader(stream, password)
                val found = BackupContent.fromJson(reader.content())
                // Told as such before anything else of it is read, whatever else may be amiss.
                if (found.schema > HubDatabase.VERSION || (found.doku?.schema ?: 0) > DokuDatabase.VERSION) {
                    throw BackupException(BackupProblem.NewerVersion)
                }
                if (found.attachments.map { it.file }.toSet().size != found.attachments.size) damaged()
                found.attachments.forEach { carried ->
                    val bytes = reader.next() ?: damaged()
                    if (bytes.size.toLong() != carried.bytes || !attachments.isName(carried.file)) damaged()
                    attachments.putAside(carried.file, bytes)
                }
                if (reader.next() != null) damaged()
                found to reader.key
            }
            val content = if (found.schema < HubDatabase.VERSION) migration.upToDate(found) else found
            replaceAllWith(content, keep = false)
            return Backup(content, key, clock.zone)
        } catch (e: Exception) {
            attachments.clearAside()
            throw e
        }
    }

    /**
     * Puts [backup] in place of all data. Its backup password becomes the one here first, unless one is set already: so
     * that a failure leaves the data as they were, as the user is then told.
     *
     * Its Doku, if it has one, takes the place of the one here only with a [dokuPassword], which must be the Doku
     * password that held when it was made, or nothing changes ([BackupProblem.WrongDokuPassword]); without one, the
     * Doku here stays as it is. Should the rest fail, the Doku is put back as it was.
     */
    suspend fun restore(backup: Backup, dokuPassword: String? = null) = withContext(NonCancellable) {
        val dokuToRestore = backup.content.doku?.takeIf { dokuPassword != null }
        if (dokuToRestore != null && !withContext(io) { dokuToRestore.opensWith(dokuPassword.orEmpty()) }) {
            throw BackupException(BackupProblem.WrongDokuPassword)
        }
        withContext(io) { if (settings().get(KEY) == null) store(backup.key) }
        val replace = suspend {
            withContext(io) {
                attachments.steady {
                    val files = backup.content.attachments.map { it.file }.toSet()
                    // Another backup opened since has taken their place: this one is to be opened again.
                    if (!attachments.hasAside(files)) damaged()
                    replaceAllWith(backup.content, keep = true)
                    attachments.takeAside(files)
                }
            }
        }
        // The Doku's state changes where the caller is, as when it is unlocked.
        if (dokuToRestore == null) replace() else doku.replacedBy(dokuToRestore, replace)
        withContext(io) { onRestore() }
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
            database.checkAttachments(content.attachments)
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

    /**
     * Every one of the [tables] read as the app reads it; a value it cannot read makes the backup damaged, as do notes,
     * folders and tabs that do not lie as the app keeps them or break the rules its editors keep.
     */
    private fun HubDatabase.readBack(tables: Set<String>) {
        val dao = backup()
        val reads: Map<String, () -> List<Any>> = mapOf(
            "routines" to dao::routines, "occurrences" to dao::occurrences, "checklist_items" to dao::checklistItems,
            "checklist_ticks" to dao::checklistTicks, "tags" to dao::tags, "routine_tags" to dao::routineTags,
            "places" to dao::places, "place_wifis" to dao::placeWifis, "routine_places" to dao::routinePlaces,
            "todos" to dao::todos, "todo_checklist_items" to dao::todoChecklistItems, "todo_tags" to dao::todoTags,
            "todo_places" to dao::todoPlaces, "wishes" to dao::wishes, "wish_tags" to dao::wishTags,
            "note_tabs" to dao::noteTabs, "notes" to dao::notes, "note_folders" to dao::noteFolders,
            "note_general_tab" to dao::noteGeneralTab, "note_goals" to dao::noteGoals, "note_todos" to dao::noteTodos,
            "note_routines" to dao::noteRoutines, "note_tags" to dao::noteTags,
            "ledger_categories" to dao::ledgerCategories, "bookings" to dao::bookings,
            "recurring_bookings" to dao::recurringBookings, "ledger_closed_months" to dao::closedMonths,
            "ledger_skipped" to dao::skipped, "ledger_closed_budgets" to dao::closedBudgets,
            "ledger_opening_balances" to dao::openingBalances, "ledger_month_budgets" to dao::monthBudgets,
            "ledger_total_budget" to dao::standardTotalBudget, "ledger_month_total_budgets" to dao::monthTotalBudgets,
            "subscriptions" to dao::subscriptions, "languages" to dao::languages, "teachers" to dao::teachers,
            "teacher_languages" to dao::teacherLanguages, "lessons" to dao::lessons, "exams" to dao::exams,
            "topics" to dao::topics, "lesson_topics" to dao::lessonTopics,
            "lesson_attachments" to dao::lessonAttachments, "inbox" to dao::inbox,
        )
        check(reads.keys == tables) { "Each table is to be read back: ${tables - reads.keys}" }
        try {
            reads.values.forEach { it() }
        } catch (e: RuntimeException) {
            // What the type converters throw on what they cannot read, such as an unknown status or a date that is none.
            throw BackupException(BackupProblem.Damaged, e)
        }
        val (folders, notes) = dao.noteFolders() to dao.notes()
        if (!notesHoldTogether(folders, notes) || !notesKeepTheRules(dao.noteTabs(), folders, notes)) {
            throw BackupException(BackupProblem.Damaged)
        }
        // At most one "Sonstiges" of each kind, which the bookings of a category deleted go to; none, and it comes anew.
        if (dao.ledgerCategories().filter { it.isOther }.groupBy { it.kind }.values.any { it.size > 1 }) {
            throw BackupException(BackupProblem.Damaged)
        }
    }

    /**
     * That the lessons' attachments in the tables are those the backup carries, each as large as it says, and no lesson
     * has more than a lesson can.
     */
    private fun HubDatabase.checkAttachments(carried: List<BackupAttachment>) {
        val rows = backup().lessonAttachments()
        val sizes = carried.associate { it.file to it.bytes }
        if (rows.size != carried.size || rows.any { sizes[it.file] != it.bytes }) damaged()
        if (rows.groupBy { it.lessonId }.values.any { it.size > LessonFiles.MAX_ATTACHMENTS }) damaged()
    }

    /** [tables] with only the rows of the lessons' attachments whose files are [kept]. */
    private fun withAttachmentsOnly(tables: Map<String, TableRows>, kept: Set<String>): Map<String, TableRows> {
        val table = tables[ATTACHMENTS_TABLE] ?: return tables
        val column = table.columns.indexOf("file")
        return tables + (ATTACHMENTS_TABLE to TableRows(table.columns, table.rows.filter { it[column] in kept }))
    }

    /** The files of the lessons' attachments in [tables], in the order of their rows. */
    private fun attachmentFilesOf(tables: Map<String, TableRows>): List<String> {
        val table = tables[ATTACHMENTS_TABLE] ?: return emptyList()
        val column = table.columns.indexOf("file")
        return table.rows.map { it[column] as String }
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
        private const val LAST_MADE = "backup-last-made"
        private const val FIRST_MADE = "backup-first-made"
        private const val SEPARATOR = ":"
        private const val ATTACHMENTS_TABLE = "lesson_attachments"

        fun passwordProblem(password: String, repeated: String): PasswordProblem? = when {
            password.length < MIN_PASSWORD_LENGTH -> PasswordProblem.TooShort
            password != repeated -> PasswordProblem.NotRepeated
            else -> null
        }
    }
}
