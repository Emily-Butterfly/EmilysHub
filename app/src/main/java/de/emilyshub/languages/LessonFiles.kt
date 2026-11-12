package de.emilyshub.languages

import de.emilyshub.data.InputText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.coroutines.CoroutineContext

/** A file as Android's file dialog or the camera gives it: the [name] it goes by and its [bytes]. */
class PickedDocument(val name: String, val bytes: ByteArray)

/** Files as Android's file dialog gives them and takes them, and PDFs as Android reads them. */
interface LessonDocuments {
    /** The file at [address], at most [limit] bytes of it; none if it cannot be read. */
    suspend fun read(address: String, limit: Int): PickedDocument?

    /** Writes [bytes] to the file at [address]; whether it worked. */
    suspend fun write(address: String, bytes: ByteArray): Boolean

    /** How many pages Android finds in [pdf]; none if it does not open as a PDF. */
    fun pdfPages(pdf: ByteArray): Int?
}

/** What importing a text file into a lesson's note came to. */
sealed interface TextImport {
    /** The note as it is with the text added. */
    data class Appended(val note: String) : TextImport

    /** It would bring the note over [LessonDraft.NOTE_MAX] characters. */
    data object TooLong : TextImport

    /** It is no text that reads as UTF-8, or cannot be read. */
    data object NotText : TextImport
}

/** What importing a photo or a PDF came to. */
sealed interface AttachmentImport {
    /** It is kept, encrypted, for the lesson once it is saved. */
    data class Added(val attachment: AttachmentRef) : AttachmentImport

    /** The lesson has [LessonFiles.MAX_ATTACHMENTS] already. */
    data object TooMany : AttachmentImport

    /** A PDF larger than [LessonFiles.PDF_MAX_BYTES]. */
    data object TooLarge : AttachmentImport

    /** It is no picture, or no PDF, Android can read; or it cannot be read at all. */
    data object Unreadable : AttachmentImport
}

/**
 * Importing into a lesson (SPEC.md §15): a text file is added to its note as if typed; a photo, shrunk and encoded anew
 * ([Photos]), or a PDF becomes an attachment, kept encrypted in [store] at once but belonging to no lesson until the
 * lesson is saved. Until then it is staged: [Languages.save] takes it in, [discard] lets it go, and clearing up spares
 * it. Shown and exported decrypted, never as a file in plain text. [documents] is only called on [io].
 */
class LessonFiles(
    private val store: AttachmentStore,
    private val documents: LessonDocuments,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val staged = mutableSetOf<String>()

    /** [note] with the text of the file at [address] added, on a line of its own after a blank one. */
    suspend fun importText(address: String, note: String): TextImport = withContext(io) {
        val picked = documents.read(address, TEXT_MAX_BYTES + 1) ?: return@withContext TextImport.NotText
        if (picked.bytes.size > TEXT_MAX_BYTES) return@withContext TextImport.TooLong
        val text = decode(picked.bytes)?.takeUnless { '\u0000' in it } ?: return@withContext TextImport.NotText
        // Line ends as typed ones are, whatever system wrote the file.
        val added = InputText.multiLine(text.replace("\r\n", "\n").replace('\r', '\n')).trim('\n')
        val joined = InputText.multiLine(
            when {
                added.isEmpty() -> note
                note.isBlank() -> added
                else -> note.trimEnd() + "\n\n" + added
            },
        )
        if (InputText.lengthOf(joined) > LessonDraft.NOTE_MAX) TextImport.TooLong else TextImport.Appended(joined)
    }

    /** The photo at [address] for a lesson that has [count] attachments now. */
    suspend fun importPhoto(address: String, count: Int): AttachmentImport = withContext(io + NonCancellable) {
        if (count >= MAX_ATTACHMENTS) return@withContext AttachmentImport.TooMany
        val picked = documents.read(address, PHOTO_MAX_BYTES + 1) ?: return@withContext AttachmentImport.Unreadable
        if (picked.bytes.size > PHOTO_MAX_BYTES) return@withContext AttachmentImport.TooLarge
        val jpeg = Photos.shrink(picked.bytes) ?: return@withContext AttachmentImport.Unreadable
        keep(AttachmentKind.Photo, jpeg, picked.name.substringBeforeLast('.').ifBlank { picked.name } + ".jpg")
    }

    /** The PDF at [address] for a lesson that has [count] attachments now. */
    suspend fun importPdf(address: String, count: Int): AttachmentImport = withContext(io + NonCancellable) {
        if (count >= MAX_ATTACHMENTS) return@withContext AttachmentImport.TooMany
        val picked = documents.read(address, PDF_MAX_BYTES + 1) ?: return@withContext AttachmentImport.Unreadable
        if (picked.bytes.size > PDF_MAX_BYTES) return@withContext AttachmentImport.TooLarge
        if (!looksLikePdf(picked.bytes) || (documents.pdfPages(picked.bytes) ?: 0) < 1) return@withContext AttachmentImport.Unreadable
        keep(AttachmentKind.Pdf, picked.bytes, picked.name)
    }

    /** What [attachment] holds, decrypted, to be shown; none if it is gone or damaged. */
    suspend fun content(attachment: AttachmentRef): ByteArray? = withContext(io) { store.read(attachment.file) }

    /** Writes [attachment] decrypted to the file at [address], chosen in Android's file dialog; whether it worked. */
    suspend fun export(attachment: AttachmentRef, address: String): Boolean = withContext(io + NonCancellable) {
        val content = store.read(attachment.file) ?: return@withContext false
        documents.write(address, content)
    }

    /** Lets go of what [draft] imported but its lesson as saved ([saved]) does not have: the editor left unsaved. */
    suspend fun discard(draft: LessonDraft, saved: LessonDraft?) = withContext(io + NonCancellable) {
        val kept = saved?.attachments.orEmpty().map { it.file }.toSet()
        draft.attachments.map { it.file }.filter { it !in kept && isStaged(it) }.forEach { file ->
            store.delete(file)
            synchronized(staged) { staged -= file }
        }
    }

    /** Whether [file] was imported and is not saved with a lesson yet. */
    fun isStaged(file: String): Boolean = synchronized(staged) { file in staged }

    /** [files] are a saved lesson's now. */
    internal fun taken(files: Collection<String>) = synchronized(staged) { staged -= files.toSet() }

    /** The store the attachments lie in. */
    internal val attachments: AttachmentStore get() = store

    /** Deletes every file in the store but those of [saved] and those staged: what a crash or a lost editor left. */
    internal fun clearUp(saved: Set<String>) = synchronized(staged) { store.keepOnly(saved + staged) }

    private fun keep(kind: AttachmentKind, bytes: ByteArray, name: String): AttachmentImport {
        // Staged as it is written, so that clearing up meanwhile spares it.
        val file = synchronized(staged) { store.write(bytes).also { staged += it } }
        val cleaned = InputText.singleLine(name).take(NAME_MAX).ifBlank { kind.stored }
        return AttachmentImport.Added(AttachmentRef(kind, file, bytes.size.toLong(), cleaned))
    }

    companion object {
        /** How many attachments a lesson may have (SPEC.md §15). */
        const val MAX_ATTACHMENTS = 10

        /** The largest PDF taken (SPEC.md §15): 10 MB. */
        const val PDF_MAX_BYTES = 10 * 1024 * 1024

        /** The largest photo read before it is shrunk: 40 MB, more than any phone's camera makes. */
        const val PHOTO_MAX_BYTES = 40 * 1024 * 1024

        /** The most a text file may have to fit a note: four bytes for each of its characters at most. */
        const val TEXT_MAX_BYTES = LessonDraft.NOTE_MAX * 4

        /** The longest name an attachment keeps, in characters. */
        private const val NAME_MAX = 120

        /** [bytes] read strictly as UTF-8, a byte order mark ahead dropped; none if they are not. */
        private fun decode(bytes: ByteArray): String? = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
        } catch (e: CharacterCodingException) {
            null
        }

        /** Whether [bytes] start as a PDF does, "%PDF-" within the first kilobyte. */
        private fun looksLikePdf(bytes: ByteArray): Boolean {
            val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
            return "%PDF-" in head
        }
    }
}
