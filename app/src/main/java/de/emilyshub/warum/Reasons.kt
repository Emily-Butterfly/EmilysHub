package de.emilyshub.warum

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.Saving
import de.emilyshub.languages.AttachmentImport
import de.emilyshub.languages.LessonFiles
import de.emilyshub.languages.PickedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Mein Warum (SPEC.md §18): the Gründe on the board, in the order the user lays them, a new one on top. Nothing keeps
 * when it was opened or looked at, and the search passes it by (§8). [database] is only called on [io], since the first
 * call opens it. Writes run to the end once started, even if the screen that started them goes away.
 *
 * A reason's photo is imported as a lesson's is, shrunk and encrypted in the store of the [files] (§9, §15), staged until
 * the reason is saved, and so in the backups with them (§10). At most [MAX_PHOTOS] reasons have one. A photo replaced,
 * taken off or deleted with its reason goes once that is saved.
 */
class Reasons(
    private val database: () -> HubDatabase,
    private val files: LessonFiles,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().reasons()

    /** The reasons in the board's order. */
    fun board(): Flow<List<Reason>> = flow { emitAll(dao.observeAll()) }.flowOn(io)

    /** The reason [id] as its editor starts with it; none once it is gone. */
    suspend fun editing(id: Long): ReasonDraft? = withContext(io) { dao.find(id)?.let { ReasonDraft(it.text, it.photo, it.look) } }

    /** Whether the reason [id], or a new one with none, may have a photo: fewer than [MAX_PHOTOS] others have one. */
    suspend fun photoAllowed(id: Long?): Boolean = withContext(io) { dao.photoCount(except = id) < MAX_PHOTOS }

    /**
     * The photo at [address], chosen or taken with the camera app, for the reason [id], or a new one with none; staged
     * until it is saved. [AttachmentImport.TooMany] once [MAX_PHOTOS] others have one.
     */
    suspend fun importPhoto(address: String, id: Long?): AttachmentImport {
        val count = withContext(io) { dao.photoCount(except = id) }
        return files.importPhoto(address, count, max = MAX_PHOTOS)
    }

    /** The photo [taken] with the camera app, read already, for the reason [id], or a new one with none; as [importPhoto]. */
    suspend fun importPhoto(taken: PickedDocument, id: Long?): AttachmentImport {
        val count = withContext(io) { dao.photoCount(except = id) }
        return files.importPhoto(taken, count, max = MAX_PHOTOS)
    }

    /** The photo kept under [file], decrypted, to be shown; none if it is gone or damaged. */
    suspend fun photo(file: String): ByteArray? = withContext(io) { files.attachments.read(file) }

    /** Lets go of the photo [file] if it was imported and is not saved yet: replaced or taken off in the editor. */
    suspend fun drop(file: String) = files.drop(file)

    /** Lets go of the photo [draft] imported, unless the reason as saved ([saved]) has it: the editor left unsaved. */
    suspend fun discard(draft: ReasonDraft, saved: ReasonDraft?) {
        val photo = draft.photo ?: return
        if (photo != saved?.photo) files.drop(photo)
    }

    /**
     * Saves [draft] as a new reason, on top of the board, with [id] none, or in place of the reason [id], where it is;
     * refused, with nothing saved, for its problems, or if that reason is gone.
     */
    suspend fun save(id: Long?, draft: ReasonDraft): Saving<ReasonProblem> {
        val problems = draft.problems
        if (problems.isNotEmpty()) return Saving.Refused(problems)
        val cleaned = draft.cleaned()
        return write { files.lock.withLock { saveLocked(id, cleaned) } }
    }

    private suspend fun saveLocked(id: Long?, draft: ReasonDraft): Saving<ReasonProblem> {
        var gone: String? = null
        val photo = draft.photo
        val saving = database().withTransaction {
            val before = id?.let { dao.find(it) ?: return@withTransaction Saving.Refused(setOf(ReasonProblem.Gone)) }
            if (photo != null && photo != before?.photo) {
                // Imported for it, though perhaps before Android ended the app and brought the editor back.
                if (photo in database().languages().attachmentFiles() || !files.attachments.has(photo)) {
                    return@withTransaction Saving.Refused(setOf(ReasonProblem.PhotoGone))
                }
                if (dao.photoCount(except = id) >= MAX_PHOTOS) return@withTransaction Saving.Refused(setOf(ReasonProblem.TooManyPhotos))
            }
            if (before == null) {
                Saving.Saved(dao.insert(Reason(text = draft.text, position = (dao.firstPosition() ?: 1) - 1, photo = photo).lookingAs(draft.look)))
            } else {
                dao.update(before.copy(text = draft.text, photo = photo).lookingAs(draft.look))
                gone = before.photo?.takeIf { it != photo }
                Saving.Saved(before.id)
            }
        }
        if (saving is Saving.Saved) {
            photo?.let { files.taken(listOf(it)) }
            gone?.let(files.attachments::delete)
        }
        return saving
    }

    /** Deletes the reason [id], if it is still there, and its photo with it. */
    suspend fun delete(id: Long) = write {
        files.lock.withLock {
            val photo = database().withTransaction { dao.find(id)?.also { dao.delete(id) }?.photo }
            photo?.let(files.attachments::delete)
        }
    }

    /** Moves the reason [id] one place towards the top ("Nach vorn"); the first stays. */
    suspend fun forward(id: Long) = moveBy(id, -1)

    /** Moves the reason [id] one place towards the end ("Nach hinten"); the last stays. */
    suspend fun back(id: Long) = moveBy(id, 1)

    /** Moves the reason [id] to [index] on the board, as dragged there; past the end, it goes last. */
    suspend fun move(id: Long, index: Int) = write {
        database().withTransaction {
            val order = dao.all().toMutableList()
            val reason = order.find { it.id == id } ?: return@withTransaction
            order.remove(reason)
            order.add(index.coerceIn(0, order.size), reason)
            order.forEachIndexed { position, it -> if (it.position != position) dao.setPosition(it.id, position) }
        }
    }

    private suspend fun moveBy(id: Long, step: Int) = write {
        database().withTransaction {
            val index = dao.all().indexOfFirst { it.id == id }
            if (index >= 0) move(id, (index + step).coerceAtLeast(0))
        }
    }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    companion object {
        /** How many reasons may have a photo (SPEC.md §18). */
        const val MAX_PHOTOS = 100
    }
}
