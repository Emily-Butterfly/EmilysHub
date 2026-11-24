package de.emilyshub.vault

import de.emilyshub.backup.BackupAttachment
import de.emilyshub.backup.TableRows
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.AttachmentKind
import de.emilyshub.languages.AttachmentRef
import de.emilyshub.languages.LessonDocuments
import de.emilyshub.languages.Photos
import de.emilyshub.languages.PickedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * The Ablage (CONTEXT.md): the encrypted files of the app's data beside its database, in [store], each kept by one of
 * its two owners, the Sessions with their Anhänge (SPEC.md §15) and the Gründe of Mein Warum with their photos (§18);
 * Tracking's photos lie with their island instead (ADR 0004). A file imported ([importPhoto], [keep]) is staged: no
 * owner has it yet, and clearing up spares it. An owner claims and lets go of files only in a [changing], which takes
 * effect once it has gone through, the files taken no longer staged and those released deleted. The backups read the
 * files, lay those of a backup aside, encrypted the same way, and put them in place of these ([steady]). [documents] and
 * the store are only called on [io].
 */
class Vault(
    private val store: AttachmentStore,
    internal val documents: LessonDocuments,
    private val io: CoroutineContext = Dispatchers.IO,
    private val aside: AttachmentStore = store.besideIt(),
) {
    private val staged = mutableSetOf<String>()

    /** Held while files are claimed, released or cleared up, and while a backup reads or replaces them. */
    private val lock = Mutex()

    /** [bytes] kept encrypted under a new name, which it returns, staged for what is being edited. */
    fun keep(bytes: ByteArray): String = synchronized(staged) { store.write(bytes).also { staged += it } }

    /** [bytes] kept as an Anhang or photo of [kind], named [name], staged. */
    internal fun keep(kind: AttachmentKind, bytes: ByteArray, name: String): AttachmentImport {
        // Staged as it is written, so that clearing up meanwhile spares it.
        val file = keep(bytes)
        val cleaned = InputText.singleLine(name).take(NAME_MAX).ifBlank { kind.stored }
        return AttachmentImport.Added(AttachmentRef(kind, file, bytes.size.toLong(), cleaned))
    }

    /** The photo at [address] for an owner that has [count] photos or Anhänge where it is to go now, of at most [max]. */
    suspend fun importPhoto(address: String, count: Int, max: Int): AttachmentImport = withContext(io + NonCancellable) {
        if (count >= max) return@withContext AttachmentImport.TooMany
        val picked = documents.read(address, PHOTO_MAX_BYTES + 1) ?: return@withContext AttachmentImport.Unreadable
        importPhoto(picked, count, max)
    }

    /**
     * The photo [picked], read already, as the camera app took it, as [importPhoto] has it: shrunk and encoded anew
     * ([Photos]), nothing of its metadata kept.
     */
    suspend fun importPhoto(picked: PickedDocument, count: Int, max: Int): AttachmentImport = withContext(io + NonCancellable) {
        if (count >= max) return@withContext AttachmentImport.TooMany
        if (picked.bytes.size > PHOTO_MAX_BYTES) return@withContext AttachmentImport.TooLarge
        val jpeg = Photos.shrink(picked.bytes) ?: return@withContext AttachmentImport.Unreadable
        keep(AttachmentKind.Photo, jpeg, picked.name.substringBeforeLast('.').ifBlank { picked.name } + ".jpg")
    }

    /** What is kept under [file], decrypted; none if it is gone or damaged. Blocking. */
    fun read(file: String): ByteArray? = store.read(file)

    /** Whether [file] was imported and no owner has it yet. */
    fun isStaged(file: String): Boolean = synchronized(staged) { file in staged }

    /** Lets go of [file] if it was imported and is not saved yet: taken off again in an editor, or the editor left. */
    suspend fun drop(file: String) = withContext(io + NonCancellable) {
        synchronized(staged) {
            if (file in staged) {
                store.delete(file)
                staged -= file
            }
        }
    }

    /** The files the owners have, as [database] holds them. */
    suspend fun owned(database: HubDatabase): Set<String> = database.vault().owned().toSet()

    /**
     * Runs [change] of what an owner has, in which it claims ([VaultChange.take]) and lets go of ([VaultChange.release])
     * files; once it has gone through, those taken are no longer staged and those released are deleted. Should it fail,
     * nothing of that happens. One at a time, and not while a backup reads or replaces the files.
     */
    suspend fun <T> changing(database: HubDatabase, change: suspend VaultChange.() -> T): T = withContext(io + NonCancellable) {
        lock.withLock {
            val changing = VaultChange(database, store)
            val result = changing.change()
            synchronized(staged) { staged -= changing.taken }
            changing.released.forEach(store::delete)
            result
        }
    }

    /**
     * Clears up what a crash or a lost editor left: the owners' files still laid aside by a restore that broke off are
     * put in place; every other file but those staged, and older than [olderThan], is deleted. Those younger may belong
     * to an editor the app is bringing back after Android ended it.
     */
    suspend fun clearUp(database: HubDatabase, olderThan: Duration = Duration.ofDays(1)) = withContext(io + NonCancellable) {
        lock.withLock {
            val owned = owned(database)
            synchronized(staged) {
                owned.filter { !store.has(it) && aside.has(it) }.forEach { store.moveFrom(aside, it) }
                val now = System.currentTimeMillis()
                (store.names() - owned - staged).filter { now - store.modified(it) >= olderThan.toMillis() }.forEach(store::delete)
            }
        }
    }

    // What the backups need of it (SPEC.md §10).

    /** Runs [block] while no file is claimed, released or cleared up, so that the tables and the files agree throughout. */
    suspend fun <T> steady(block: suspend () -> T): T = lock.withLock { block() }

    /** Whether [file] is a name a file of the Ablage can have. */
    fun isName(file: String): Boolean = AttachmentStore.isName(file)

    /** Lets go of all laid aside, to begin anew. */
    fun clearAside() = aside.keepOnly(emptySet())

    /** Lays [bytes] aside as the file [file] of a backup being opened, apart from the app's own; encrypted. */
    fun putAside(file: String, bytes: ByteArray) = aside.write(file, bytes)

    /** Whether all of [files] are laid aside, as a restore needs them. */
    fun hasAside(files: Set<String>): Boolean = files.all(aside::has)

    /**
     * Makes the files those laid aside under [files]: the app's own given up, but for those still being imported, and
     * those laid aside put in their place. Only within [steady].
     */
    fun takeAside(files: Set<String>) {
        check(lock.isLocked) { "Only while steady" }
        synchronized(staged) {
            // Those of the backup in first, then the app's own out: so that the tables never lack a file for long.
            files.forEach { store.moveFrom(aside, it) }
            (store.names() - files - staged).forEach(store::delete)
        }
        clearAside()
    }

    /** The owners' files in the tables of a backup, the Anhänge first, in the order of their rows. */
    fun filesIn(tables: Map<String, TableRows>): List<String> = Owner.entries.flatMap { owner ->
        val table = tables[owner.table] ?: return@flatMap emptyList()
        val index = table.columns.indexOf(owner.column)
        table.rows.mapNotNull { it[index] as String? }
    }

    /**
     * The tables of a backup with only the owners' files that could be read, [kept]: an Anhang that could not is left out
     * with its row; a Grund keeps its text without its photo, and one with a photo alone is left out.
     */
    fun readableOnly(tables: Map<String, TableRows>, kept: Set<String>): Map<String, TableRows> =
        Owner.entries.fold(tables) { all, owner ->
            val table = all[owner.table] ?: return@fold all
            val index = table.columns.indexOf(owner.column)
            val rows = table.rows.mapNotNull { row ->
                when {
                    row[index] == null || row[index] in kept -> row
                    owner.text == null -> null
                    InputText.isVisible(row[table.columns.indexOf(owner.text)] as String? ?: "") ->
                        row.toMutableList().also { it[index] = null }
                    else -> null
                }
            }
            all + (owner.table to TableRows(table.columns, rows))
        }

    /**
     * Whether the owners' files in [database], as a backup put them there, are those it [carried], each Anhang as large
     * as it says, each file once, and no owner with more than it may have.
     */
    fun restoredFits(database: HubDatabase, carried: List<BackupAttachment>): Boolean {
        val rows = database.backup().lessonAttachments()
        val photos = database.backup().reasons().mapNotNull { it.photo }
        val sizes = carried.associate { it.file to it.bytes }
        // Each file once, not a Session's and a Grund's both.
        val files = rows.map { it.file } + photos
        return files.size == carried.size && files.toSet().size == files.size &&
            rows.all { sizes[it.file] == it.bytes } && photos.all { it in sizes } &&
            rows.groupBy { it.lessonId }.values.none { it.size > MAX_PER_SESSION } && photos.size <= MAX_REASON_PHOTOS
    }

    /** The owners of the Ablage's files: the table and column that name them, and the column of a text that may stand alone. */
    private enum class Owner(val table: String, val column: String, val text: String?) {
        Sessions("lesson_attachments", "file", null),
        Reasons("reasons", "photo", "text"),
    }

    companion object {
        /** How many Anhänge a Session may have (SPEC.md §15). */
        const val MAX_PER_SESSION = 10

        /** How many Gründe may have a photo (SPEC.md §18). */
        const val MAX_REASON_PHOTOS = 100

        /** The largest photo read before it is shrunk: 40 MB, more than any phone's camera makes. */
        const val PHOTO_MAX_BYTES = 40 * 1024 * 1024

        /** The name a photo taken with the camera app goes by. */
        const val TAKEN_NAME = "photo.jpg"

        /** The longest name an Anhang keeps, in characters. */
        private const val NAME_MAX = 120
    }
}

/**
 * A change of what an owner of the Ablage has ([Vault.changing]): the files it claims and those it lets go of, which take
 * effect once the change has gone through.
 */
class VaultChange internal constructor(private val database: HubDatabase, private val store: AttachmentStore) {
    internal val taken = mutableSetOf<String>()
    internal val released = mutableSetOf<String>()
    private var owned: Set<String>? = null

    /**
     * Whether [file] lies in the Ablage and no owner has it, as it was when first asked in this change: imported for what
     * is being saved, though perhaps before Android ended the app and brought the editor back.
     */
    suspend fun isFree(file: String): Boolean {
        val all = owned ?: database.vault().owned().toSet().also { owned = it }
        return file !in all && file !in taken && store.has(file)
    }

    /** [file] is the owner's now. */
    fun take(file: String) {
        taken += file
    }

    /** [file] is no one's any more: deleted once the change has gone through. */
    fun release(file: String) {
        released += file
    }
}
